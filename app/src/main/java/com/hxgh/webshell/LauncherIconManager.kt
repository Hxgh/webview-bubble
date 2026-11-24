package com.hxgh.webshell

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.Intent
import android.graphics.drawable.Icon
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Saves the current website icon and keeps the shell to one launcher alias. */
class LauncherIconManager(private val activity: Activity) {
    private val packageManager get() = activity.packageManager
    private val iconFile get() = File(activity.filesDir, "launcher/icon.png")
    private val defaultAlias = ComponentName(activity, "${activity.packageName}.LauncherDefault")

    fun saveAndApply(bitmap: Bitmap) {
        val directory = iconFile.parentFile ?: throw IOException("Invalid icon directory")
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create icon directory")
        FileOutputStream(iconFile).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Cannot encode launcher icon"
            }
        }
        bitmap.recycle()
        applyDefaultAlias()
        publishHomeShortcut()
    }

    fun applyExisting() {
        if (iconFile.isFile) applyDefaultAlias()
    }

    private fun applyDefaultAlias() {
        if (!iconFile.isFile) return

        // Keep exactly one launcher entry. The alias is the only component with a
        // LAUNCHER category; this avoids MainActivity and an alias resolving as two
        // desktop items at the same time.
        packageManager.setComponentEnabledSetting(
            defaultAlias,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun publishHomeShortcut() {
        val manager = activity.getSystemService(ShortcutManager::class.java)
        val bitmap = android.graphics.BitmapFactory.decodeFile(iconFile.absolutePath) ?: return
        val shortcut = ShortcutInfo.Builder(activity, "current-site")
            .setShortLabel(activity.getString(R.string.app_name))
            .setLongLabel(activity.getString(R.string.app_name))
            .setIcon(Icon.createWithBitmap(bitmap))
            .setIntent(
                Intent(activity, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER),
            )
            .build()
        runCatching {
            manager.pushDynamicShortcut(shortcut)
        }
        bitmap.recycle()
    }
}
