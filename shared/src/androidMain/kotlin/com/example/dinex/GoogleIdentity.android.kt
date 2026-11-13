package com.example.dinex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

@Composable
internal actual fun rememberGoogleIdentity(): GoogleIdentity {
    val context = LocalContext.current
    return remember(context) {
        object : GoogleIdentity {
            override val available = true

            override suspend fun requestToken(): GoogleIdentityToken? {
                val option = GetGoogleIdOption.Builder()
                    .setServerClientId(FirebaseConfig.GOOGLE_WEB_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val credential = try {
                    CredentialManager.create(context).getCredential(context, request).credential
                } catch (_: GetCredentialCancellationException) {
                    return null
                } catch (_: NoCredentialException) {
                    throw CloudAuthException("GOOGLE_NO_ACCOUNT")
                } catch (_: GetCredentialException) {
                    throw CloudAuthException("GOOGLE_CREDENTIAL_INVALID")
                }
                if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    throw CloudAuthException("GOOGLE_CREDENTIAL_INVALID")
                }
                return GoogleIdentityToken(GoogleIdTokenCredential.createFrom(credential.data).idToken, true)
            }
        }
    }
}
