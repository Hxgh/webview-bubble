package com.hxgh.webshell

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var root: FrameLayout
    private lateinit var siteContainer: FrameLayout
    private lateinit var siteWebView: WebView
    private var guideHubView: GuideHubView? = null
    private lateinit var progressBar: View
    private lateinit var dockView: FloatingDockView

    private var fileChooser: ValueCallback<Array<Uri>>? = null
    private lateinit var iconManager: LauncherIconManager
    private val iconFetcher = IconFetcher { siteWebView.favicon }

    private var currentTheme = "dark"
    private var currentThemeMode = "system"
    private var currentUrl = ""
    private var currentAutoDockSec = 5
    private var lastBackPressTime = 0L

    private var currentSafeTop = 0
    private var currentSafeBottom = 0
    private var currentSafeLeft = 0
    private var currentSafeRight = 0

    private val documentPicker =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            fileChooser?.onReceiveValue(uris.takeIf { it.isNotEmpty() }?.toTypedArray())
            fileChooser = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        prefs = getSharedPreferences("webview_bubble_prefs", Context.MODE_PRIVATE)
        currentThemeMode = prefs.getString("theme_mode", "system") ?: "system"
        currentAutoDockSec = prefs.getInt("auto_dock_sec", 5)
        val defaultUrl = getString(R.string.default_url)
        currentUrl = prefs.getString("wrapped_url", defaultUrl) ?: defaultUrl

        currentTheme = resolveTheme(currentThemeMode)
        applySystemBars(currentTheme)

        iconManager = LauncherIconManager(this)
        buildViews()
        setupBackHandler()

        if (currentUrl.isNotEmpty()) {
            loadSiteUrl(currentUrl)
        } else {
            showGuideHub()
        }
    }

    private fun resolveTheme(mode: String): String {
        return if (mode == "system") {
            val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (nightMode == Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
        } else {
            if (mode == "light") "light" else "dark"
        }
    }

    private fun applySystemBars(theme: String) {
        val dark = theme == "dark"
        val background = if (dark) Color.rgb(18, 18, 18) else Color.rgb(242, 242, 247)
        window.statusBarColor = background
        window.navigationBarColor = background

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController ?: return
            var appearance = 0
            if (!dark) {
                appearance = appearance or WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                appearance = appearance or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            }
            controller.setSystemBarsAppearance(
                appearance,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            var flags = window.decorView.systemUiVisibility
            @Suppress("DEPRECATION")
            val lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            flags = if (!dark) flags or lightFlags else flags and lightFlags.inv()
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (currentThemeMode == "system") {
            val newTheme = resolveTheme("system")
            if (newTheme != currentTheme) {
                currentTheme = newTheme
                applyThemeEverywhere(currentTheme)
            }
        }
    }

    private fun applyThemeEverywhere(theme: String) {
        currentTheme = theme
        applySystemBars(theme)
        val isDark = theme == "dark"
        val bgColor = if (isDark) Color.rgb(18, 18, 18) else Color.rgb(242, 242, 247)
        root.setBackgroundColor(bgColor)
        siteContainer.setBackgroundColor(bgColor)
        siteWebView.setBackgroundColor(bgColor)
        progressBar.setBackgroundColor(if (isDark) Color.WHITE else Color.BLACK)
        dockView.isDarkTheme = isDark

        if (guideHubView != null && guideHubView?.visibility == View.VISIBLE) {
            showGuideHub()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildViews() {
        val isDark = currentTheme == "dark"
        val bgColor = if (isDark) Color.rgb(18, 18, 18) else Color.rgb(242, 242, 247)

        root = FrameLayout(this).apply {
            setBackgroundColor(bgColor)
        }

        // 图层 1：底层网页容器 (由原生设置 padding 物理锁死安全高度，绝对避让刘海/状态栏/手势条)
        siteContainer = FrameLayout(this).apply {
            setBackgroundColor(bgColor)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 顶层 WebView 直接运行目标站点 (彻底干掉 iframe，根除 X-Frame-Options 限制)
        siteWebView = WebView(this).apply {
            setBackgroundColor(bgColor)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        configureSiteWebView()
        siteContainer.addView(siteWebView)
        root.addView(siteContainer)

        // 图层 2：顶部 2dp 极细单色加载进度条
        progressBar = View(this).apply {
            setBackgroundColor(if (isDark) Color.WHITE else Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(0, (2 * resources.displayMetrics.density).roundToInt()).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            visibility = View.GONE
        }
        root.addView(progressBar)

        // 图层 3：纯 Android 原生悬浮控制器 (贴边小条 / 悬浮球)
        dockView = FloatingDockView(this).apply {
            this.isDarkTheme = isDark
            this.autoDockSeconds = currentAutoDockSec
            this.onOpenPanelListener = {
                showControlPanelDialog()
            }
            this.onPositionChanged = { isDocked, isLeft, yPx ->
                prefs.edit()
                    .putBoolean("dock_is_docked", isDocked)
                    .putBoolean("dock_is_left", isLeft)
                    .putFloat("dock_y", yPx)
                    .apply()
            }
        }
        root.addView(dockView)

        setContentView(root)

        // 监听 Android 原生 WindowInsets，物理锁死安全高度
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            currentSafeTop = maxOf(status.top, cutout.top)
            currentSafeBottom = maxOf(nav.bottom, cutout.bottom, ime.bottom)
            currentSafeLeft = maxOf(status.left, cutout.left)
            currentSafeRight = maxOf(status.right, cutout.right)

            // 物理限制底层网页容器内边距
            siteContainer.setPadding(currentSafeLeft, currentSafeTop, currentSafeRight, currentSafeBottom)

            // 进度条位置更新 (紧随状态栏正下方)
            (progressBar.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.topMargin = currentSafeTop
                lp.leftMargin = currentSafeLeft
                lp.rightMargin = currentSafeRight
                progressBar.layoutParams = lp
            }

            // 安全区数据同步至悬浮控制器
            dockView.safeTopPx = currentSafeTop
            dockView.safeBottomPx = currentSafeBottom
            dockView.safeLeftPx = currentSafeLeft
            dockView.safeRightPx = currentSafeRight

            // 初始吸附位置恢复
            val savedY = prefs.getFloat("dock_y", -1f)
            val savedIsLeft = prefs.getBoolean("dock_is_left", true)
            val initialY = if (savedY >= 0) savedY else maxOf((currentSafeTop + 40).toFloat(), resources.displayMetrics.heightPixels * 0.58f)
            dockView.dockToEdge(initialY, savedIsLeft)

            insets
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureSiteWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(siteWebView, true)
        WebView.setWebContentsDebuggingEnabled(true)

        siteWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowContentAccess = true
            allowFileAccess = true
            setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        siteWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme.orEmpty().lowercase()
                if (scheme != "http" && scheme != "https") {
                    openExternal(request.url)
                    return true
                }
                return false // 顶层主站正常直接加载
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                dockView.setLoading(true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                dockView.setLoading(false)
                if (!url.isNullOrBlank() && url != "about:blank") {
                    currentUrl = url
                    prefs.edit().putString("wrapped_url", url).apply()

                    // 自动抓取并更新桌面图标
                    lifecycleScope.launch {
                        try {
                            val result = iconFetcher.fetch(url)
                            if (result != null) {
                                iconManager.saveAndApply(result.bitmap)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail): Boolean {
                (view?.parent as? ViewGroup)?.removeView(view)
                view?.destroy()
                recreate()
                return true
            }
        }

        siteWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                updateProgress(newProgress)
            }

            // 多窗口与弹窗授权：像正常移动端浏览器一样，在当前主页面自然流转，无需生硬的外部弹窗
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                transport.webView = siteWebView
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window)
                if (siteWebView.canGoBack()) {
                    siteWebView.goBack()
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread { request.grant(request.resources) }
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileChooser?.onReceiveValue(null)
                fileChooser = callback
                val accept = params.acceptTypes.filter { it.isNotBlank() }
                documentPicker.launch(accept.ifEmpty { listOf("*/*") }.toTypedArray())
                return true
            }
        }

        siteWebView.setDownloadListener { url, userAgent, disposition, mime, _ ->
            runCatching {
                val filename = URLUtil.guessFileName(url, disposition, mime)
                getSystemService(DownloadManager::class.java).enqueue(
                    DownloadManager.Request(Uri.parse(url)).apply {
                        setMimeType(mime)
                        addRequestHeader("User-Agent", userAgent)
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    }
                )
                toast("下载已开始")
            }.onFailure { toast("无法开始下载") }
        }
    }

    private fun updateProgress(progress: Int) {
        if (progress < 100) {
            progressBar.visibility = View.VISIBLE
            val screenW = resources.displayMetrics.widthPixels - currentSafeLeft - currentSafeRight
            val targetW = (screenW * (progress / 100f)).roundToInt()
            (progressBar.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.width = targetW
                progressBar.layoutParams = lp
            }
        } else {
            progressBar.animate()
                .alpha(0f)
                .setDuration(220)
                .withEndAction {
                    progressBar.visibility = View.GONE
                    progressBar.alpha = 1f
                    (progressBar.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                        lp.width = 0
                        progressBar.layoutParams = lp
                    }
                }
                .start()
        }
    }

    fun loadSiteUrl(url: String) {
        var target = url.trim()
        if (target.isEmpty() || target == "about:blank") {
            showGuideHub()
            return
        }
        if (!target.startsWith("http://", ignoreCase = true) && !target.startsWith("https://", ignoreCase = true)) {
            target = "https://$target"
        }

        guideHubView?.visibility = View.GONE
        siteWebView.visibility = View.VISIBLE
        currentUrl = target
        prefs.edit().putString("wrapped_url", target).apply()
        siteWebView.loadUrl(target)
    }

    fun showGuideHub() {
        currentUrl = ""
        prefs.edit().remove("wrapped_url").apply()
        siteWebView.stopLoading()
        siteWebView.loadUrl("about:blank")
        siteWebView.visibility = View.GONE

        guideHubView?.let { siteContainer.removeView(it) }
        guideHubView = GuideHubView(this, currentTheme == "dark") { selectedUrl ->
            loadSiteUrl(selectedUrl)
        }
        siteContainer.addView(guideHubView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        guideHubView?.visibility = View.VISIBLE
    }

    private fun showControlPanelDialog() {
        val dialog = ControlPanelDialog(
            this,
            isDark = currentTheme == "dark",
            currentUrl = currentUrl,
            currentAutoDockSec = currentAutoDockSec,
            currentThemeMode = currentThemeMode,
            callbacks = object : ControlPanelDialog.Callbacks {
                override fun onApplyUrl(url: String) {
                    loadSiteUrl(url)
                }

                override fun onAutoDockSecChanged(sec: Int) {
                    currentAutoDockSec = sec
                    prefs.edit().putInt("auto_dock_sec", sec).apply()
                    dockView.autoDockSeconds = sec
                }

                override fun onThemeModeChanged(mode: String) {
                    currentThemeMode = mode
                    prefs.edit().putString("theme_mode", mode).apply()
                    val newTheme = resolveTheme(mode)
                    applyThemeEverywhere(newTheme)
                }
            }
        )
        // 闭环交互：面板关闭时（点✕、点背景或应用网址），悬浮球平滑动画自动收起到侧边小条
        dialog.setOnDismissListener {
            dockView.dockToEdge()
        }
        dialog.show()
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (guideHubView != null && guideHubView?.visibility == View.VISIBLE) {
                    handleDoubleBackExit()
                    return
                }

                if (siteWebView.canGoBack()) {
                    siteWebView.goBack()
                } else {
                    handleDoubleBackExit()
                }
            }
        })
    }

    private fun handleDoubleBackExit() {
        if (System.currentTimeMillis() - lastBackPressTime < 2000) {
            finish()
        } else {
            lastBackPressTime = System.currentTimeMillis()
            toast("再按一次退出应用")
        }
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            toast("未找到支持此链接的应用")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        siteWebView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        siteWebView.onResume()
    }

    override fun onDestroy() {
        siteWebView.destroy()
        super.onDestroy()
    }
}
