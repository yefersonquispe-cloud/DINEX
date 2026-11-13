package com.example.dinex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberGoogleIdentity(): GoogleIdentity = remember {
    object : GoogleIdentity {
        override val available = false
        override suspend fun requestToken(): GoogleIdentityToken? = null
    }
}
