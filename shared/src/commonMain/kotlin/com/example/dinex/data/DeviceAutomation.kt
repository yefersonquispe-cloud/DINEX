package com.example.dinex

import androidx.compose.runtime.Composable

data class CapturedTransaction(
    val source: String,
    val title: String,
    val amount: Double,
    val income: Boolean = false,
    val raw: String = "",
    val captureId: String = "",
    val capturedDate: DinexDate? = null,
)

interface DeviceAutomation {
    val voiceAvailable: Boolean
    val notificationReaderAvailable: Boolean
    val locationAvailable: Boolean
    val hotwordEnabled: Boolean get() = false
    val supportsBackgroundVoice: Boolean get() = false
    val overlayAvailable: Boolean get() = false
    /** Permiso de micrófono concedido. */
    val microphoneAvailable: Boolean get() = false
    /** Xiaomi, Redmi o POCO (MIUI/HyperOS): piden permisos extra para la burbuja y el segundo plano. */
    val xiaomiDevice: Boolean get() = false
    /** Android 13+ con la app instalada fuera de Google Play: pide "Permitir ajustes restringidos". */
    val needsRestrictedSettings: Boolean get() = false
    val notificationListenerConnected: Boolean get() = false
    val systemNotificationsSupported: Boolean get() = false
    val systemNotificationsEnabled: Boolean get() = false

    fun startVoice(onResult: (String) -> Unit, onError: (String) -> Unit)
    /** Asks Android 13+ for POST_NOTIFICATIONS, or opens notification settings. */
    fun requestSystemNotificationPermission() = openAppDetailsSettings()
    fun stopVoice()
    fun openNotificationSettings()
    fun openAppDetailsSettings() = Unit
    fun openBatterySettings() = Unit
    fun openOverlaySettings() = Unit
    /** Abre "Otros permisos" de Xiaomi (ventanas en segundo plano, inicio automático). */
    fun openXiaomiPermissions() = openAppDetailsSettings()
    /** Pide el permiso de micrófono. */
    fun requestMicrophonePermission() = Unit
    fun reconnectNotifications() = Unit
    fun acknowledgeNotifications(items: List<CapturedTransaction>) = Unit
    /**
     * Al eliminar la cuenta: borra lo que el equipo guarda fuera del perfil (avisos de pago
     * detectados, la frase pendiente de "Oye Dinex" y las fotos temporales de comprobantes).
     */
    fun clearAccountData() = Unit
    fun setHotwordEnabled(enabled: Boolean): String? = "Esta función solo está disponible en Android."
    fun consumePendingVoiceCommand(): String? = null
    fun requestLocationPermission()
    fun drainNotifications(): List<CapturedTransaction>
    fun currentLocationLabel(): String?
    fun currentLocation(): DinexLocation? = null
}

@Composable
expect fun rememberDeviceAutomation(): DeviceAutomation
