package com.hxgh.webviewbubble

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Reads the page's own icon declarations before falling back to conventional paths.
 * The result is intentionally kept as a regular bitmap: Android launcher aliases can
 * use it without depending on XML-only icon formats.
 */
class IconFetcher(
    private val webViewIcon: () -> Bitmap? = { null },
) {
    data class Result(
        val bitmap: Bitmap,
        val source: String,
    )

    private data class Candidate(
        val url: String,
        val size: Int,
        val priority: Int,
    )

    private data class ManifestIcons(
        val candidates: List<Candidate>,
        val maskable: Boolean,
    )

    suspend fun fetch(pageUrl: String): Result? = withContext(Dispatchers.IO) {
        val page = runCatching { URL(pageUrl) }.getOrNull() ?: return@withContext null
        if (page.protocol != "https" && page.protocol != "http") return@withContext null

        try {
            val pageHtml = getText(pageUrl)
            val manifest = pageHtml
                ?.let { parseManifestUrl(it, page) }
                ?.let { manifestUrl -> getText(manifestUrl)?.let { parseManifest(manifestUrl, it) } }
            val htmlIcons = pageHtml?.let { parseHtmlIcons(it, page) }.orEmpty()

            val allCandidates = (manifest?.candidates.orEmpty() + htmlIcons)
                .distinctBy { it.url }
                .sortedWith(compareByDescending<Candidate> { it.priority }.thenByDescending { it.size })

            for (candidate in allCandidates) {
                val bitmap = download(candidate.url) ?: continue
                normalize(bitmap, manifest?.maskable == true && candidate.priority >= 30)?.let {
                    return@withContext Result(it, candidate.url)
                }
            }

            webViewIcon()?.let { result ->
                normalize(result, false)?.let {
                    return@withContext Result(it, "WebView favicon")
                }
            }

            conventionalIcons(page).firstNotNullOfOrNull { url ->
                download(url)?.let { normalize(it, false) }?.let { Result(it, url) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun parseManifestUrl(html: String, page: URL): String? {
        val linkTags = Regex(
            """<link\b[^>]*>""",
            RegexOption.IGNORE_CASE,
        ).findAll(html)
        for (tag in linkTags) {
            val value = tag.value
            if (!attribute(value, "rel").orEmpty().lowercase(Locale.ROOT).contains("manifest")) continue
            val href = attribute(value, "href") ?: continue
            return resolve(page, href)
        }
        return null
    }

    private fun parseHtmlIcons(html: String, page: URL): List<Candidate> {
        val output = mutableListOf<Candidate>()
        for (tag in Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html)) {
            val value = tag.value
            val rel = attribute(value, "rel").orEmpty().lowercase(Locale.ROOT)
            val href = attribute(value, "href") ?: continue
            val resolved = resolve(page, href) ?: continue
            val sizes = parseSizes(attribute(value, "sizes"))
            when {
                rel.contains("apple-touch-icon") -> output += Candidate(resolved, sizes.maxOrNull() ?: 180, 60)
                rel.contains("icon") -> output += Candidate(resolved, sizes.maxOrNull() ?: 32, 40)
            }
        }
        return output
    }

    private fun parseManifest(manifestUrl: String, text: String): ManifestIcons? {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val base = runCatching { URL(manifestUrl) }.getOrNull() ?: return null
        val rawIcons = json.optJSONArray("icons") ?: return ManifestIcons(emptyList(), false)
        val output = mutableListOf<Candidate>()
        var maskable = false
        for (i in 0 until rawIcons.length()) {
            val item = rawIcons.optJSONObject(i) ?: continue
            val src = item.optString("src", "").trim()
            if (src.isEmpty()) continue
            val resolved = resolve(base, src) ?: continue
            val sizes = parseSizes(item.optString("sizes", ""))
            val purpose = item.optString("purpose", "").lowercase(Locale.ROOT)
            if (purpose.contains("maskable")) maskable = true
            val type = item.optString("type", "").lowercase(Locale.ROOT)
            val priority = when {
                purpose.contains("any") -> 35
                purpose.contains("maskable") -> 30
                type.contains("svg") -> 20
                else -> 45
            }
            output += Candidate(resolved, sizes.maxOrNull() ?: 512, priority)
        }
        return ManifestIcons(output, maskable)
    }

    private fun conventionalIcons(page: URL): List<String> {
        val base = "${page.protocol}://${page.host}${if (page.port >= 0) ":${page.port}" else ""}"
        return listOf(
            "$base/apple-touch-icon.png",
            "$base/apple-touch-icon-precomposed.png",
            "$base/favicon-192x192.png",
            "$base/favicon.ico",
        )
    }

    private fun resolve(base: URL, value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("data:")) return null
        return runCatching { URL(base, trimmed).toString() }.getOrNull()
    }

    private fun attribute(tag: String, name: String): String? {
        val pattern = Regex("""\b${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
        val match = pattern.find(tag) ?: return null
        return match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
    }

    private fun parseSizes(value: String?): List<Int> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(' ').mapNotNull { token ->
            token.trim().lowercase(Locale.ROOT).substringBefore('x').toIntOrNull()
        }
    }

    private fun getText(url: String): String? {
        val bytes = getBytes(url, maxBytes = 2_000_000) ?: return null
        return runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
    }

    private fun getBytes(url: String, maxBytes: Int): ByteArray? {
        val connection = open(url) ?: return null
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { it.readLimited(maxBytes) }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String): Bitmap? {
        val connection = open(url) ?: return null
        return try {
            if (connection.responseCode !in 200..299) return null
            val bytes = connection.inputStream.use { it.readLimited(6_000_000) }
            decodeBitmap(bytes)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection? {
        val parsed = runCatching { URL(url) }.getOrNull() ?: return null
        if (parsed.protocol != "http" && parsed.protocol != "https") return null
        return try {
            (parsed.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "WebViewBubble/1.0 Android")
                setRequestProperty("Accept", "image/*, text/html, application/manifest+json, */*")
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) return output.toByteArray()
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun decodeBitmap(bytes: ByteArray): Bitmap? {
        val text = runCatching { bytes.toString(Charsets.UTF_8).trimStart() }.getOrNull()
        if (text != null && (text.startsWith("data:image/") || text.startsWith("<svg") || text.startsWith("<?xml"))) {
            return decodeImageData(text)
        }
        return bytes.toAndroidBitmap()
    }

    private fun decodeImageData(text: String): Bitmap? {
        if (text.startsWith("data:image/")) {
            val headerEnd = text.indexOf(',')
            if (headerEnd < 0) return null
            val header = text.substring(0, headerEnd)
            if (!header.contains(";base64", ignoreCase = true)) return null
            val bytes = runCatching { Base64.decode(text.substring(headerEnd + 1), Base64.DEFAULT) }.getOrNull()
                ?: return null
            return bytes.toAndroidBitmap()
        }
        return null
    }
}

private fun ByteArray.toAndroidBitmap(): Bitmap? {
    if (isEmpty()) return null
    val options = android.graphics.BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    android.graphics.BitmapFactory.decodeByteArray(this, 0, size, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    val ratio = max(options.outWidth, options.outHeight).toFloat()
    options.inSampleSize = max(1, (ratio / 512f).toInt())
    options.inJustDecodeBounds = false
    return runCatching { android.graphics.BitmapFactory.decodeByteArray(this, 0, size, options) }.getOrNull()
}

internal fun normalize(source: Bitmap, maskable: Boolean): Bitmap? {
    if (source.width < 8 || source.height < 8) return null
    val outputSize = 512
    val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawColor(Color.TRANSPARENT)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    if (maskable && source.width >= source.height * 0.8 && source.height >= source.width * 0.8) {
        val scale = outputSize.toFloat() / min(source.width, source.height)
        val dest = RectF(
            (outputSize - source.width * scale) / 2f,
            (outputSize - source.height * scale) / 2f,
            (outputSize + source.width * scale) / 2f,
            (outputSize + source.height * scale) / 2f,
        )
        canvas.drawBitmap(source, null, dest, paint)
    } else {
        val square = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val squareCanvas = Canvas(square)
        squareCanvas.drawColor(Color.WHITE)
        squareCanvas.drawBitmap(
            source,
            null,
            Rect(0, 0, outputSize, outputSize),
            paint,
        )
        val rounded = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val roundedCanvas = Canvas(rounded)
        roundedCanvas.clipPath(android.graphics.Path().apply {
            addRoundRect(RectF(0f, 0f, outputSize.toFloat(), outputSize.toFloat()), 88f, 88f, android.graphics.Path.Direction.CW)
        })
        roundedCanvas.drawBitmap(square, 0f, 0f, paint)
        canvas.drawBitmap(rounded, 0f, 0f, paint)
        square.recycle()
        rounded.recycle()
        source.recycle()
    }
    return output
}
