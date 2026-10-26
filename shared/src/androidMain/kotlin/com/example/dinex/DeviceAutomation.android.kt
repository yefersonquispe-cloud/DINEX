package com.example.dinex

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

@Composable
actual fun rememberDeviceAutomation(): DeviceAutomation {
    val context = androidx.compose.ui.platform.LocalContext.current
    val automation = remember(context) { AndroidDeviceAutomation(context) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(automation, lifecycleOwner) {
        DinexLocationProvider.current = { automation.currentLocation() }
        // Follow the GPS only while Dinex is visible; the permission dialog also
        // pauses and resumes the screen, so a new grant starts it right away.
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> automation.startFollowingLocation()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> automation.stopFollowingLocation()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            automation.stopFollowingLocation()
            DinexLocationProvider.current = null
            automation.stopVoice()
        }
    }
    return automation
}

private class AndroidDeviceAutomation(private val context: Context) : DeviceAutomation {
    private val hotwordPrefs get() = context.getSharedPreferences("dinex_hotword", Context.MODE_PRIVATE)
    private var recognizer: SpeechRecognizer? = null
    override val voiceAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)
    override val notificationReaderAvailable: Boolean
        get() = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    override val locationAvailable: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    override val hotwordEnabled: Boolean get() = hotwordPrefs.getBoolean("enabled", false)
    override val supportsBackgroundVoice = true
    override val overlayAvailable: Boolean get() = Settings.canDrawOverlays(context)
    override val notificationListenerConnected: Boolean
        get() = context.getSharedPreferences("dinex_notification_status", Context.MODE_PRIVATE).getBoolean("connected", false)
    override val systemNotificationsSupported = true
    override val systemNotificationsEnabled: Boolean
        get() = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    override fun requestSystemNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val activity = context.findActivity()
            if (activity != null) {
                val prefs = context.getSharedPreferences("dinex_notifications", Context.MODE_PRIVATE)
                val asked = prefs.getBoolean("asked", false)
                // After a permanent denial Android returns "denied" without a
                // dialog, so the only useful next step is the settings screen.
                if (asked && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) {
                    openSystemNotificationSettings()
                    return
                }
                prefs.edit().putBoolean("asked", true).apply()
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7005)
                return
            }
        }
        openSystemNotificationSettings()
    }

    private fun openSystemNotificationSettings() {
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else null
        runCatching { context.startActivity(notifications ?: appDetails) }
            .recoverCatching { context.startActivity(appDetails) }
    }

    override fun openOverlaySettings() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.recoverCatching { openAppDetailsSettings() }
    }

    override fun reconnectNotifications() {
        if (notificationReaderAvailable) {
            runCatching { NotificationListenerService.requestRebind(
                ComponentName(context.packageName, context.packageName + ".DinexNotificationListenerService"),
            ) }
        }
    }

    override fun acknowledgeNotifications(items: List<CapturedTransaction>) = DinexNotificationStore.acknowledge(context, items)

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
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .recoverCatching { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun openAppDetailsSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
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
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .recoverCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
    }

    override fun setHotwordEnabled(enabled: Boolean): String? {
        if (!enabled) {
            hotwordPrefs.edit().putBoolean("enabled", false).apply()
            context.stopService(Intent().setClassName(context.packageName, "${context.packageName}.DinexHotwordService"))
            return null
        }
        if (!voiceAvailable) return "Este teléfono no tiene un servicio de reconocimiento de voz disponible."
        val activity = context as? Activity
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activity?.let { ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.RECORD_AUDIO), 7003) }
            return "Concede el permiso de micrófono y pulsa Activar otra vez."
        }
        // Android 13+ permite ejecutar el servicio aunque el usuario no acepte
        // notificaciones. Se solicita para que el acceso rápido sea visible,
        // pero nunca debe bloquear la activación del micrófono.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            activity?.let { ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7004) }
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
        val location = bestLocation() ?: return "Ubicación GPS disponible"
        return geocode(location).ifBlank { "Ubicación GPS disponible" }
    }

    override fun currentLocation(): DinexLocation? {
        if (!locationAvailable) return null
        val location = bestLocation() ?: return null
        return DinexLocation(location.latitude, location.longitude, geocode(location))
    }

    @Volatile private var freshLocation: Location? = null
    private var lastRefreshAt = 0L
    private var followingLocation = false
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = keepBest(location)
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    /**
     * While Dinex is on screen, follow the phone's GPS so each expense is tagged
     * with where the person really is, not the last place another app asked for.
     */
    @SuppressLint("MissingPermission")
    fun startFollowingLocation() {
        if (followingLocation || !locationAvailable) return
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        locationProviders(manager).forEach { provider ->
            runCatching { manager.requestLocationUpdates(provider, 10_000L, 5f, locationListener, Looper.getMainLooper()) }
        }
        followingLocation = true
        lastRefreshAt = 0L
        refreshLocation()
    }

    fun stopFollowingLocation() {
        if (!followingLocation) return
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        runCatching { manager.removeUpdates(locationListener) }
        followingLocation = false
    }

    /** Asks Android for one new, high-accuracy fix right now. */
    @SuppressLint("MissingPermission")
    fun refreshLocation() {
        if (!locationAvailable) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRefreshAt < 15_000) return
        lastRefreshAt = now
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        locationProviders(manager).forEach { provider ->
            runCatching {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                        val request = LocationRequest.Builder(0L).setQuality(LocationRequest.QUALITY_HIGH_ACCURACY).build()
                        manager.getCurrentLocation(provider, request, null, ContextCompat.getMainExecutor(context)) { location ->
                            location?.let(::keepBest)
                        }
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                        manager.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(context)) { location ->
                            location?.let(::keepBest)
                        }
                    else -> {
                        @Suppress("DEPRECATION")
                        manager.requestSingleUpdate(provider, locationListener, Looper.getMainLooper())
                    }
                }
            }
        }
    }

    private fun keepBest(location: Location) {
        if (isBetterLocation(location, freshLocation)) freshLocation = location
    }

    /** Prefers a clearly newer fix; among fixes of about the same time, the more accurate one. */
    private fun isBetterLocation(candidate: Location, current: Location?): Boolean {
        if (current == null) return true
        val twoMinutes = 2L * 60 * 1_000_000_000
        val newer = candidate.elapsedRealtimeNanos - current.elapsedRealtimeNanos
        if (newer > twoMinutes) return true
        if (newer < -twoMinutes) return false
        return candidate.accuracy <= current.accuracy || (newer > 0 && candidate.accuracy <= current.accuracy + 50f)
    }

    private fun locationProviders(manager: LocationManager): List<String> {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        val enabled = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        return wanted.filter { it in enabled }
    }

    /** Best recent fix, ignoring anything older than 30 minutes so a stale city is never saved. */
    @SuppressLint("MissingPermission")
    private fun bestLocation(): Location? {
        if (!locationAvailable) return null
        refreshLocation()
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val cached = locationProviders(manager)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        val maxAgeNanos = 30L * 60 * 1_000_000_000
        return (cached + listOfNotNull(freshLocation))
            .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < maxAgeNanos }
            .fold(null as Location?) { best, location -> if (isBetterLocation(location, best)) location else best }
    }

    /** Street and number when Android knows them, e.g. "Av. Ejército 1020, Yanahuara, Arequipa". */
    private fun geocode(location: Location): String = runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context, Locale("es", "PE")).getFromLocation(location.latitude, location.longitude, 1)
            ?.firstOrNull()
            ?.let { address ->
                val street = address.thoroughfare?.let { listOfNotNull(it, address.subThoroughfare).joinToString(" ") }
                listOfNotNull(street, address.subLocality, address.locality)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(", ")
                    .ifBlank { address.getAddressLine(0).orEmpty().ifBlank { address.featureName.orEmpty() } }
            }
            .orEmpty()
    }.getOrDefault("")
}

/** Compose may hand a ContextThemeWrapper instead of the Activity itself. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
