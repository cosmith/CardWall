package fr.cosmith.cardwall.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import fr.cosmith.cardwall.anki.AnkiContract
import fr.cosmith.cardwall.model.InstalledApp

class AppRepository(private val context: Context) {
    fun loadLaunchableApps(): List<InstalledApp> {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val homePackages =
            packageManager
                .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }
                .toSet()
        val hiddenPackages = CRITICAL_PACKAGES + homePackages + context.packageName + AnkiContract.PACKAGE_NAME

        return packageManager
            .queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .asSequence()
            .filterNot { it.activityInfo.packageName in hiddenPackages }
            .distinctBy { it.activityInfo.packageName }
            .map { resolveInfo ->
                val drawable = resolveInfo.loadIcon(packageManager)
                InstalledApp(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    icon = drawable.toBitmap(96).asImageBitmap(),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun Drawable.toBitmap(sizePx: Int): Bitmap {
        if (this is BitmapDrawable && bitmap.width == sizePx && bitmap.height == sizePx) return bitmap
        val output = createBitmap(sizePx, sizePx)
        val canvas = Canvas(output)
        setBounds(0, 0, sizePx, sizePx)
        draw(canvas)
        return output
    }

    companion object {
        private val CRITICAL_PACKAGES =
            setOf(
                "android",
                "com.android.settings",
                "com.android.systemui",
                "com.android.permissioncontroller",
                "com.google.android.permissioncontroller",
                "com.android.phone",
                "com.google.android.dialer",
                "com.android.server.telecom",
                "com.google.android.apps.safetyhub",
            )
    }
}
