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
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

@Composable
internal actual fun rememberGoogleIdentity(): GoogleIdentity {
    val context = LocalContext.current
    return remember(context) {
        object : GoogleIdentity {
            override val available = true

            override suspend fun requestToken(): GoogleIdentityToken? {
                // Botón "Continuar con Google": siempre muestra el selector de cuentas del teléfono.
                val option = GetSignInWithGoogleOption.Builder(FirebaseConfig.GOOGLE_WEB_CLIENT_ID).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val credential = try {
                    CredentialManager.create(context).getCredential(context, request).credential
                } catch (error: GetCredentialCancellationException) {
                    // Si la persona cierra el selector no se muestra nada. Pero cuando la huella de la app
                    // (SHA-1 de la firma de Google Play) no está registrada, Google también responde
                    // "cancelado" con el código 16 o "reauth": antes la pantalla se quedaba sin hacer nada.
                    val detail = error.message.orEmpty().lowercase()
                    if ("[16]" in detail || "reauth" in detail || "developer" in detail) throw CloudAuthException("GOOGLE_NOT_CONFIGURED")
                    return null
                } catch (_: NoCredentialException) {
                    // Con el selector de Google esto casi nunca es falta de cuentas: suele ser que esta
                    // versión de la app (paquete y huella SHA-1) no está registrada en Google Cloud.
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
