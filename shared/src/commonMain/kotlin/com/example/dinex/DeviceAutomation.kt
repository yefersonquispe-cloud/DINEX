package com.example.dinex

import androidx.compose.runtime.Composable

data class CapturedTransaction(
    val source: String,
    val title: String,
    val amount: Double,
    val income: Boolean = false,
    val raw: String = "",
)

interface DeviceAutomation {
    val voiceAvailable: Boolean
    val notificationReaderAvailable: Boolean
    val locationAvailable: Boolean
    val hotwordEnabled: Boolean get() = false

    fun startVoice(onResult: (String) -> Unit, onError: (String) -> Unit)
    fun stopVoice()
    fun openNotificationSettings()
    fun openAppDetailsSettings() = Unit
    fun openBatterySettings() = Unit
    fun setHotwordEnabled(enabled: Boolean): String? = "Esta función solo está disponible en Android."
    fun consumePendingVoiceCommand(): String? = null
    fun requestLocationPermission()
    fun drainNotifications(): List<CapturedTransaction>
    fun currentLocationLabel(): String?
}

@Composable
expect fun rememberDeviceAutomation(): DeviceAutomation
