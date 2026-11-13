package com.example.dinex

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// ═══════════════════════════════════════════════════════════════════════════
// FIREBASE
// Cuentas con Firebase Authentication y copia de los datos en Cloud Firestore.
// La clave web de Firebase es pública por diseño: la seguridad real está en
// Authentication (contraseñas cifradas por Google) y en las reglas de Firestore
// (cada persona solo puede leer y escribir su propio documento users/{uid}).
// ═══════════════════════════════════════════════════════════════════════════
internal object FirebaseConfig {
    const val API_KEY = "AIzaSyAtstrFuF9wXTSkf-3JtCG-unHr0QUVwM4"
    const val PROJECT_ID = "dinex-63ed4"
    const val GOOGLE_WEB_CLIENT_ID = "928396674848-lp2kictn2l63pmvqj338cthttbu5p9h1.apps.googleusercontent.com"
}

internal class CloudResponse(val status: Int, val body: String)

/** Petición HTTPS de cada plataforma. Lanza una excepción solo si no hubo conexión. */
internal expect suspend fun cloudRequest(method: String, url: String, body: String?, bearer: String?): CloudResponse

@OptIn(ExperimentalTime::class)
internal fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Error devuelto por Firebase (contraseña incorrecta, correo repetido, etc.). */
internal class CloudAuthException(val code: String) : Exception(code)

internal data class CloudSession(
    val uid: String,
    val email: String,
    val idToken: String,
    val refreshToken: String,
    val expiresAt: Long,
) {
    fun fresh(now: Long = epochMillis()) = idToken.isNotBlank() && now < expiresAt - 60_000
}

internal data class CloudProfile(
    val name: String,
    val email: String,
    val profile: String,
    val updatedAt: Long,
    /** Constancia del consentimiento (Ley N.° 29733): versión aceptada y cuándo. */
    val terms: TermsConsent? = null,
)

internal data class TermsConsent(val version: String, val acceptedAt: Long)

/** Consultas a Dinex IA hechas en un día (día = días desde 1970, según el equipo). */
internal data class AiUsage(val day: Int, val count: Int)

internal class DinexCloud(
    private val apiKey: String = FirebaseConfig.API_KEY,
    private val projectId: String = FirebaseConfig.PROJECT_ID,
    private val http: suspend (String, String, String?, String?) -> CloudResponse = ::cloudRequest,
) {
    private val identity = "https://identitytoolkit.googleapis.com/v1/accounts"

    suspend fun signIn(email: String, password: String): CloudSession = authCall(
        "$identity:signInWithPassword?key=$apiKey",
        jsonObject("email" to email, "password" to password, "returnSecureToken" to true),
        email,
    )

    suspend fun signUp(email: String, password: String): CloudSession = authCall(
        "$identity:signUp?key=$apiKey",
        jsonObject("email" to email, "password" to password, "returnSecureToken" to true),
        email,
    )

    /** Cambia el token de Google por una sesión de Firebase y devuelve también el nombre de la cuenta. */
    suspend fun signInWithGoogle(token: GoogleIdentityToken): Pair<CloudSession, String> {
        val credential = if (token.isIdToken) "id_token" else "access_token"
        val body = jsonObject(
            "postBody" to "$credential=${token.value}&providerId=google.com",
            "requestUri" to "https://$projectId.firebaseapp.com",
            "returnSecureToken" to true,
            "returnIdpCredential" to true,
        )
        val json = parseObject(http("POST", "$identity:signInWithIdp?key=$apiKey", body, null))
        val name = json.text("displayName") ?: json.text("fullName") ?: ""
        return sessionFrom(json, "") to name
    }

    suspend fun refresh(session: CloudSession): CloudSession {
        val response = http("POST", "https://securetoken.googleapis.com/v1/token?key=$apiKey",
            jsonObject("grant_type" to "refresh_token", "refresh_token" to session.refreshToken), null)
        val json = parseObject(response)
        return session.copy(
            uid = json.text("user_id") ?: session.uid,
            idToken = json.text("id_token").orEmpty(),
            refreshToken = json.text("refresh_token") ?: session.refreshToken,
            expiresAt = epochMillis() + (json.text("expires_in")?.toLongOrNull() ?: 3600) * 1000,
        )
    }

    suspend fun sendEmailVerification(session: CloudSession) {
        parseObject(http("POST", "$identity:sendOobCode?key=$apiKey",
            jsonObject("requestType" to "VERIFY_EMAIL", "idToken" to session.idToken), null))
    }

    suspend fun sendPasswordReset(email: String) {
        parseObject(http("POST", "$identity:sendOobCode?key=$apiKey",
            jsonObject("requestType" to "PASSWORD_RESET", "email" to email), null))
    }

    suspend fun download(session: CloudSession): CloudProfile? {
        val response = http("GET", documentUrl(session.uid), null, session.idToken)
        if (response.status == 404) return null
        val fields = parseObject(response).obj("fields") ?: return null
        return CloudProfile(
            name = fields.obj("name")?.text("stringValue").orEmpty(),
            email = fields.obj("email")?.text("stringValue").orEmpty(),
            profile = fields.obj("profile")?.text("stringValue").orEmpty(),
            updatedAt = fields.obj("updatedAt")?.text("integerValue")?.toLongOrNull() ?: 0L,
            terms = fields.obj("termsVersion")?.text("stringValue")?.let { version ->
                TermsConsent(version, fields.obj("termsAcceptedAt")?.text("integerValue")?.toLongOrNull() ?: 0L)
            },
        )
    }

    suspend fun upload(session: CloudSession, profile: CloudProfile) {
        val body = "{\"fields\":{" +
            "\"name\":{\"stringValue\":${jsonString(profile.name)}}," +
            "\"email\":{\"stringValue\":${jsonString(profile.email)}}," +
            "\"profile\":{\"stringValue\":${jsonString(profile.profile)}}," +
            "\"updatedAt\":{\"integerValue\":\"${profile.updatedAt}\"}" +
            (profile.terms?.let { ",\"termsVersion\":{\"stringValue\":${jsonString(it.version)}},\"termsAcceptedAt\":{\"integerValue\":\"${it.acceptedAt}\"}" } ?: "") +
            "}}"
        // Solo se reemplazan estos campos; así no se borra el conteo de Dinex IA guardado aparte.
        val fields = listOf("name", "email", "profile", "updatedAt") + if (profile.terms != null) listOf("termsVersion", "termsAcceptedAt") else emptyList()
        parseObject(http("PATCH", documentUrl(session.uid) + updateMask(fields), body, session.idToken))
    }

    /** Lee solo el conteo de Dinex IA (sin bajar todo el perfil). */
    suspend fun aiUsage(session: CloudSession): AiUsage? {
        val response = http("GET", documentUrl(session.uid) + "?mask.fieldPaths=aiDay&mask.fieldPaths=aiCount", null, session.idToken)
        if (response.status == 404) return null
        val fields = parseObject(response).obj("fields") ?: return null
        val day = fields.obj("aiDay")?.text("integerValue")?.toIntOrNull() ?: return null
        return AiUsage(day, fields.obj("aiCount")?.text("integerValue")?.toIntOrNull()?.coerceAtLeast(0) ?: 0)
    }

    /** Guarda el conteo de Dinex IA sin tocar el resto del documento. */
    suspend fun saveAiUsage(session: CloudSession, usage: AiUsage) {
        val body = "{\"fields\":{\"aiDay\":{\"integerValue\":\"${usage.day}\"},\"aiCount\":{\"integerValue\":\"${usage.count}\"}}}"
        parseObject(http("PATCH", documentUrl(session.uid) + updateMask(listOf("aiDay", "aiCount")), body, session.idToken))
    }

    private fun updateMask(fields: List<String>) = fields.joinToString("&", prefix = "?") { "updateMask.fieldPaths=$it" }

    /** Derecho de cancelación: borra el documento en Firestore y luego la cuenta de Firebase. */
    suspend fun deleteAccount(session: CloudSession) {
        val removed = http("DELETE", documentUrl(session.uid), null, session.idToken)
        if (removed.status != 404) parseObject(removed)
        parseObject(http("POST", "$identity:delete?key=$apiKey", jsonObject("idToken" to session.idToken), null))
    }

    private fun documentUrl(uid: String) =
        "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/users/$uid"

    private suspend fun authCall(url: String, body: String, email: String): CloudSession =
        sessionFrom(parseObject(http("POST", url, body, null)), email)

    private fun sessionFrom(json: Map<String, Any?>, email: String): CloudSession {
        return CloudSession(
            uid = json.text("localId").orEmpty(),
            email = json.text("email") ?: email,
            idToken = json.text("idToken").orEmpty(),
            refreshToken = json.text("refreshToken").orEmpty(),
            expiresAt = epochMillis() + (json.text("expiresIn")?.toLongOrNull() ?: 3600) * 1000,
        ).also { if (it.uid.isBlank() || it.idToken.isBlank()) throw CloudAuthException("INVALID_RESPONSE") }
    }

    private fun parseObject(response: CloudResponse): Map<String, Any?> {
        val json = runCatching { parseJson(response.body) as? Map<String, Any?> }.getOrNull() ?: emptyMap()
        if (response.status !in 200..299) {
            val error = json.obj("error")
            // securetoken devuelve "error" como texto; identitytoolkit y Firestore como objeto.
            val code = error?.text("message") ?: json.text("error") ?: error?.text("status") ?: "HTTP_${response.status}"
            throw CloudAuthException(code.substringBefore(" ").substringBefore(":"))
        }
        return json
    }
}

/** Mensajes simples en español para los errores de Firebase. */
internal fun cloudErrorMessage(code: String): String = when (code) {
    "INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND", "INVALID_EMAIL" -> "Correo o contraseña incorrectos."
    "EMAIL_EXISTS" -> "Ya existe una cuenta con este correo. Inicia sesión."
    "USER_DISABLED" -> "Esta cuenta fue desactivada."
    "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Demasiados intentos. Espera unos minutos y vuelve a intentarlo."
    "WEAK_PASSWORD", "PASSWORD_DOES_NOT_MEET_REQUIREMENTS" -> "La contraseña no es segura. Revisa los requisitos."
    "OPERATION_NOT_ALLOWED", "CONFIGURATION_NOT_FOUND" -> "El inicio de sesión no está disponible por ahora."
    "GOOGLE_NO_ACCOUNT" -> "No hay una cuenta de Google en este teléfono. Agrégala en Ajustes e inténtalo de nuevo."
    "GOOGLE_NOT_READY" -> "Google todavía está cargando. Espera unos segundos e inténtalo de nuevo."
    "GOOGLE_POPUP_ERROR" -> "No se pudo abrir la ventana de Google. Permite ventanas emergentes e inténtalo otra vez."
    "GOOGLE_CREDENTIAL_INVALID", "INVALID_IDP_RESPONSE", "INVALID_ID_TOKEN" -> "No pudimos verificar tu cuenta de Google. Inténtalo otra vez."
    "FEDERATED_USER_ID_ALREADY_LINKED", "NEED_CONFIRMATION" -> "Este correo ya tiene una cuenta. Inicia sesión con el método que usaste antes."
    else -> "No pudimos conectar con tu cuenta. Inténtalo otra vez."
}

/** Errores que significan que la sesión guardada ya no sirve y hay que volver a entrar. */
internal fun sessionRevoked(code: String) = code in setOf(
    "TOKEN_EXPIRED", "USER_DISABLED", "USER_NOT_FOUND", "INVALID_REFRESH_TOKEN", "INVALID_ID_TOKEN", "PROJECT_NUMBER_MISMATCH",
)

// ── JSON (el lector está en MarketData.kt) ───────────────────────────────────
@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.obj(key: String) = this[key] as? Map<String, Any?>
private fun Map<String, Any?>.text(key: String) = when (val value = this[key]) {
    is String -> value
    is Number, is Boolean -> value.toString()
    else -> null
}

internal fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when {
            char == '"' -> append("\\\"")
            char == '\\' -> append("\\\\")
            char == '\n' -> append("\\n")
            char == '\r' -> append("\\r")
            char == '\t' -> append("\\t")
            char < ' ' -> append("\\u").append(char.code.toString(16).padStart(4, '0'))
            else -> append(char)
        }
    }
    append('"')
}

private fun jsonObject(vararg pairs: Pair<String, Any>): String = pairs.joinToString(",", "{", "}") { (key, value) ->
    jsonString(key) + ":" + if (value is String) jsonString(value) else value.toString()
}
