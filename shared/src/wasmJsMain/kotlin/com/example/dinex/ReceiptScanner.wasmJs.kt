package com.example.dinex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberReceiptScanner(
    onResult: (ScannedPurchase) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val error = rememberUpdatedState(onError)
    return { error.value("El reconocimiento automático con cámara está disponible en la app Android.") }
}
