package com.example.dinex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberDeviceAutomation(): DeviceAutomation = remember {
    object : DeviceAutomation {
        override val voiceAvailable: Boolean = false
        override val notificationReaderAvailable: Boolean = false
        override val locationAvailable: Boolean = false
        override fun startVoice(onResult: (String) -> Unit, onError: (String) -> Unit) = onError("La voz web depende de la compatibilidad del navegador.")
        override fun stopVoice() = Unit
        override fun openNotificationSettings() = Unit
        override fun requestLocationPermission() = Unit
        override fun drainNotifications(): List<CapturedTransaction> = emptyList()
        override fun currentLocationLabel(): String? = null
    }
}
