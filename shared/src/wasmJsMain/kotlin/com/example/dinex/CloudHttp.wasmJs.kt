@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.example.dinex

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@JsFun("(method, url, body, bearer, onDone, onError) => globalThis.dinexHttp.request(method, url, body, bearer, onDone, onError)")
private external fun browserRequest(method: String, url: String, body: String?, bearer: String?, onDone: (Int, String) -> Unit, onError: (String) -> Unit)

internal actual suspend fun cloudRequest(method: String, url: String, body: String?, bearer: String?): CloudResponse =
    suspendCancellableCoroutine { continuation ->
        browserRequest(
            method, url, body, bearer,
            { status, text -> if (continuation.isActive) continuation.resume(CloudResponse(status, text)) },
            { message -> if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message)) },
        )
    }
