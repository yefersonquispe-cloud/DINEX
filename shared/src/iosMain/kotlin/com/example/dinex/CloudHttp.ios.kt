package com.example.dinex

import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(BetaInteropApi::class)
internal actual suspend fun cloudRequest(method: String, url: String, body: String?, bearer: String?): CloudResponse =
    suspendCancellableCoroutine { continuation ->
        val address = NSURL.URLWithString(url)?.takeIf { url.startsWith("https://") } ?: run {
            continuation.resumeWithException(IllegalStateException("URL inválida"))
            return@suspendCancellableCoroutine
        }
        val request = NSMutableURLRequest.requestWithURL(address).apply {
            setHTTPMethod(method)
            setTimeoutInterval(20.0)
            setValue("application/json", forHTTPHeaderField = "Accept")
            if (bearer != null) setValue("Bearer $bearer", forHTTPHeaderField = "Authorization")
            if (body != null) {
                setValue("application/json; charset=utf-8", forHTTPHeaderField = "Content-Type")
                setHTTPBody(NSString.create(string = body).dataUsingEncoding(NSUTF8StringEncoding))
            }
        }
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            if (!continuation.isActive) return@dataTaskWithRequest
            val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
            val text = data?.let { NSString.create(it, NSUTF8StringEncoding)?.toString() }.orEmpty()
            if (error != null) continuation.resumeWithException(IllegalStateException(error.localizedDescription))
            else continuation.resume(CloudResponse(status, text))
        }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }
