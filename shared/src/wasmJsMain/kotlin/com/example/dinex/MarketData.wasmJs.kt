@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.example.dinex

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@JsFun("(url, onText, onError) => globalThis.dinexHttp.get(url, onText, onError)")
private external fun browserGet(url: String, onText: (String) -> Unit, onError: (String) -> Unit)

internal actual suspend fun fetchMarketText(url: String): String = suspendCancellableCoroutine { continuation ->
    browserGet(
        url,
        { text -> if (continuation.isActive) continuation.resume(text) },
        { message -> if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message)) },
    )
}

internal actual fun fullScreenDialogProperties() =
    androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
