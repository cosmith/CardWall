package fr.cosmith.cardwall

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import fr.cosmith.cardwall.anki.AnkiContract
import fr.cosmith.cardwall.anki.AnkiRepository
import fr.cosmith.cardwall.apps.AppRepository
import fr.cosmith.cardwall.data.CardWallPreferences
import fr.cosmith.cardwall.gate.CardWallAccessibilityService
import fr.cosmith.cardwall.ui.CardWallApp

class MainActivity : ComponentActivity() {
    private val ankiPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissionRefresh?.invoke()
        }

    private var permissionRefresh: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ankiRepository = AnkiRepository(this)
        val appRepository = AppRepository(this)
        val preferences = CardWallPreferences(this)

        setContent {
            CardWallApp(
                preferences = preferences,
                ankiRepository = ankiRepository,
                appRepository = appRepository,
                isAccessibilityEnabled = ::isCardWallAccessibilityEnabled,
                onOpenAccessibilitySettings = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onRequestAnkiPermission = { refresh ->
                    permissionRefresh = refresh
                    ankiPermissionLauncher.launch(AnkiContract.PERMISSION)
                },
            )
        }
    }

    private fun isCardWallAccessibilityEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val expected = ComponentName(this, CardWallAccessibilityService::class.java)
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val service = info.resolveInfo.serviceInfo
                service.packageName == expected.packageName && service.name == expected.className
            }
    }
}
