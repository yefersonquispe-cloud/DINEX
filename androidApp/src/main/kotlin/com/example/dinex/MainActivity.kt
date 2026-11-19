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
                val intent = Intent(this, DinexHotwordService::class.java).setAction(
                    if (visible) DinexHotwordService.ACTION_APP_VISIBLE else DinexHotwordService.ACTION_APP_HIDDEN,
                )
                // Con Dinex a la vista se puede volver a encender el servicio si el sistema lo cerró.
                if (visible) androidx.core.content.ContextCompat.startForegroundService(this, intent) else startService(intent)
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
        if (savedInstanceState == null) handleWidgetAction(intent)
        if (savedInstanceState == null) handleDebugVoice(intent)

        setContent {
            App(onThemeChanged = { dark ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }, onDataChanged = { DinexWidgets.updateAll(this) })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetAction(intent)
        handleDebugVoice(intent)
    }

    /**
     * Solo en la versión de desarrollo (nunca en la publicada): permite probar Oye Dinex
     * en el emulador sin micrófono, con adb y el extra "dinex_debug_phrase".
     */
    private fun handleDebugVoice(intent: Intent?) {
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        val phrase = intent?.getStringExtra("dinex_debug_phrase") ?: return
        intent.removeExtra("dinex_debug_phrase")
        // Se (re)inicia Oye Dinex mientras la app está a la vista, como lo haría la persona.
        runCatching { androidx.core.content.ContextCompat.startForegroundService(this, Intent(this, DinexHotwordService::class.java).setAction(DinexHotwordService.ACTION_START)) }
        if (phrase.isBlank()) return
        val app = applicationContext
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        // Primero se pasa la app a segundo plano y luego se "dicta" la frase a la burbuja.
        handler.postDelayed({ moveTaskToBack(true) }, 600)
        handler.postDelayed({
            runCatching { app.startService(Intent(app, DinexHotwordService::class.java).setAction(DinexHotwordService.ACTION_DEBUG_PHRASE).putExtra(DinexHotwordService.EXTRA_PHRASE, phrase)) }
        }, 1_800)
    }

    /** Un botón del widget se atiende una sola vez, aunque la app se reabra desde recientes. */
    private fun handleWidgetAction(intent: Intent?) {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        DinexQuickActions.request(intent.getStringExtra(DinexWidgets.EXTRA_ACTION))
        intent.removeExtra(DinexWidgets.EXTRA_ACTION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 7002 = ubicación; 7005 = notificaciones. Ambos refrescan el estado de permisos de la UI.
        if (requestCode == 7002 || requestCode == 7005) DinexNotificationPermission.refresh()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
