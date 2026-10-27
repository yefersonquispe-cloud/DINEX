package com.example.dinex

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal actual suspend fun fetchMarketText(url: String): String = suspendCancellableCoroutine { continuation ->
    val address = NSURL.URLWithString(url) ?: run {
        continuation.resumeWithException(IllegalStateException("URL del mercado inválida"))
        return@suspendCancellableCoroutine
    }
    val request = NSMutableURLRequest.requestWithURL(address).apply {
        setValue("application/json", forHTTPHeaderField = "Accept")
        setTimeoutInterval(15.0)
    }
    val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
        if (!continuation.isActive) return@dataTaskWithRequest
        val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
        val body = data?.let { NSString.create(it, NSUTF8StringEncoding)?.toString() }
        when {
            error != null -> continuation.resumeWithException(IllegalStateException(error.localizedDescription))
            status !in 200..299 || body == null -> continuation.resumeWithException(IllegalStateException("El mercado respondió $status"))
            else -> continuation.resume(body)
        }
    }
    continuation.invokeOnCancellation { task.cancel() }
    task.resume()
}

internal actual fun fullScreenDialogProperties() =
    androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
