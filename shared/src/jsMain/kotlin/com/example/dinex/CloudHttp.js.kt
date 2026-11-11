package com.example.dinex

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@JsName("dinexHttp")
private external object BrowserCloudHttp {
    fun request(method: String, url: String, body: String?, bearer: String?, onDone: (Int, String) -> Unit, onError: (String) -> Unit)
}

internal actual suspend fun cloudRequest(method: String, url: String, body: String?, bearer: String?): CloudResponse =
    suspendCancellableCoroutine { continuation ->
        BrowserCloudHttp.request(
            method, url, body, bearer,
            { status, text -> if (continuation.isActive) continuation.resume(CloudResponse(status, text)) },
            { message -> if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message)) },
        )
    }
