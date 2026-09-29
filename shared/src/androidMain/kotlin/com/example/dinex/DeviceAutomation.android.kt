package com.example.dinex

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import android.provider.Settings.Secure

@Composable
actual fun rememberDeviceAutomation(): DeviceAutomation {
    val context = androidx.compose.ui.platform.LocalContext.current
    val automation = remember(context) { AndroidDeviceAutomation(context) }
    DisposableEffect(automation) { onDispose { automation.stopVoice() } }
    return automation
}

private class AndroidDeviceAutomation(private val context: Context) : DeviceAutomation {
    private val hotwordPrefs get() = context.getSharedPreferences("dinex_hotword", Context.MODE_PRIVATE)
    private var recognizer: SpeechRecognizer? = null
    override val voiceAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)
    override val notificationReaderAvailable: Boolean
        get() = runCatching {
            Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?.split(":")?.any { it.startsWith(context.packageName + "/") } == true
        }.getOrDefault(false)
    override val locationAvailable: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    override val hotwordEnabled: Boolean get() = hotwordPrefs.getBoolean("enabled", false)

    override fun startVoice(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!voiceAvailable) return onError("Este teléfono no tiene reconocimiento de voz disponible.")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            (context as? Activity)?.let { ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.RECORD_AUDIO), 7001) }
            return onError("Concede permiso de micrófono y pulsa de nuevo.")
        }
        stopVoice()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { speech ->
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val value = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (value.isBlank()) onError("No entendí el comando.") else onResult(value)
                }
                override fun onError(error: Int) = onError("No pude escuchar el comando. Inténtalo otra vez.")
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Di: Dinex registra 20 soles en comida")
            })
        }
    }

    override fun stopVoice() { recognizer?.destroy(); recognizer = null }

    override fun openNotificationSettings() {
        val component = ComponentName(context.packageName, "${context.packageName}.DinexNotificationListenerService")
        val detailed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                putExtra("android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME", component.flattenToString())
            }
        } else null
        val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        val intent = detailed?.takeIf { it.resolveActivity(context.packageManager) != null } ?: fallback
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun openAppDetailsSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun openBatterySettings() {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val xiaomiIntent = Intent("miui.intent.action.OP_AUTO_START").setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        )
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        val intent = if ((manufacturer.contains("xiaomi") || manufacturer.contains("redmi")) && xiaomiIntent.resolveActivity(context.packageManager) != null) {
            xiaomiIntent
        } else fallback
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun setHotwordEnabled(enabled: Boolean): String? {
        if (!enabled) {
            hotwordPrefs.edit().putBoolean("enabled", false).apply()
            context.stopService(Intent().setClassName(context.packageName, "${context.packageName}.DinexHotwordService"))
            return null
        }
        if (!voiceAvailable) return "Este teléfono no tiene un servicio de reconocimiento de voz disponible."
        val activity = context as? Activity
        val missing = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isNotEmpty()) {
            activity?.let { ActivityCompat.requestPermissions(it, missing.toTypedArray(), 7003) }
            return "Concede micrófono y notificaciones; después pulsa Activar otra vez."
        }
        return runCatching {
            hotwordPrefs.edit().putBoolean("enabled", true).apply()
            val service = Intent().setClassName(context.packageName, "${context.packageName}.DinexHotwordService")
                .setAction("com.example.dinex.action.START_HOTWORD")
            ContextCompat.startForegroundService(context, service)
            null
        }.getOrElse {
            hotwordPrefs.edit().putBoolean("enabled", false).apply()
            "Android bloqueó el inicio en segundo plano. Abre la batería y permite Inicio automático para Dinex."
        }
    }

    override fun consumePendingVoiceCommand(): String? {
        val value = hotwordPrefs.getString("pending_command", null)?.trim().orEmpty()
        if (value.isNotBlank()) hotwordPrefs.edit().remove("pending_command").apply()
        return value.ifBlank { null }
    }

    override fun requestLocationPermission() {
        (context as? Activity)?.let { ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 7002) }
    }

    override fun drainNotifications(): List<CapturedTransaction> = DinexNotificationStore.drain(context)

    override fun currentLocationLabel(): String? {
        if (!locationAvailable) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { manager.isProviderEnabled(it) } ?: return null
        val location = runCatching { manager.getLastKnownLocation(provider) }.getOrNull() ?: return "Ubicación GPS disponible"
        return runCatching {
            Geocoder(context, Locale("es", "PE")).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()?.let {
                listOfNotNull(it.locality, it.thoroughfare).joinToString(", ").ifBlank { "Ubicación GPS disponible" }
            }
        }.getOrNull() ?: "Ubicación GPS disponible"
    }
}
