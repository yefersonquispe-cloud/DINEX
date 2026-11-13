@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.example.dinex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@JsFun("(clientId, onToken, onError) => globalThis.dinexGoogle.request(clientId, onToken, onError)")
private external fun browserGoogleRequest(clientId: String, onToken: (String) -> Unit, onError: (String) -> Unit)

@Composable
internal actual fun rememberGoogleIdentity(): GoogleIdentity = remember {
    object : GoogleIdentity {
        override val available = true
        override suspend fun requestToken(): GoogleIdentityToken? = suspendCancellableCoroutine { continuation ->
            browserGoogleRequest(FirebaseConfig.GOOGLE_WEB_CLIENT_ID,
                { token -> if (continuation.isActive) continuation.resume(GoogleIdentityToken(token, false)) },
                { code -> if (continuation.isActive) {
                    if (code == "CANCELLED") continuation.resume(null)
                    else continuation.resumeWithException(CloudAuthException(code))
                } },
            )
        }
    }
}
