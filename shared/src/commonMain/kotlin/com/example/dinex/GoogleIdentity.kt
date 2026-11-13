package com.example.dinex

import androidx.compose.runtime.Composable

internal data class GoogleIdentityToken(val value: String, val isIdToken: Boolean)

internal interface GoogleIdentity {
    val available: Boolean
    suspend fun requestToken(): GoogleIdentityToken?
}

@Composable
internal expect fun rememberGoogleIdentity(): GoogleIdentity
