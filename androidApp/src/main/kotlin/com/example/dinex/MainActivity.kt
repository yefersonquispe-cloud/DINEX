package com.example.dinex

import android.os.Bundle
import android.graphics.Color
import android.content.Intent
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        updateVoiceVisibility(true)
    }

    override fun onStop() {
        updateVoiceVisibility(false)
        super.onStop()
    }

    private fun updateVoiceVisibility(visible: Boolean) {
        val prefs = getSharedPreferences(DinexHotwordService.PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(DinexHotwordService.KEY_APP_VISIBLE, visible).apply()
        if (prefs.getBoolean(DinexHotwordService.KEY_ENABLED, false)) {
            runCatching {
                startService(Intent(this, DinexHotwordService::class.java).setAction(
                    if (visible) DinexHotwordService.ACTION_APP_VISIBLE else DinexHotwordService.ACTION_APP_HIDDEN,
                ))
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        configureDinexStorage(this)
        DinexAlerts.sink = AndroidDinexAlerts(this)

        setContent {
            App(onThemeChanged = { dark ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }, onDataChanged = { DinexBalanceWidget.updateAll(this) })
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 7005 is the notification permission request from DeviceAutomation.
        if (requestCode == 7005) DinexNotificationPermission.refresh()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
