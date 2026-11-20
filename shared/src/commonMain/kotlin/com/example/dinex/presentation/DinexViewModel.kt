package com.example.dinex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class DinexUiState(
    val accounts: List<UserAccount> = emptyList(),
    val email: String? = null,
    val data: FinancialData = FinancialData(),
    val page: Int = 0,
    val message: String? = null,
    val goalCelebration: Boolean = false,
    val levelCelebration: StreakLevel? = null,
    val reduceMotion: Boolean = false,
    val largeText: Boolean = false,
    val darkMode: Boolean = false,
    val budgetAlerts: Boolean = true,
    val saveReminderEnabled: Boolean = true,
    val saveReminderHour: Int = 20,
    val profilePhoto: String = "",
    val budgetWarning: String? = null,
    val pendingExpense: Movement? = null,
    val pendingReminderId: Int? = null,
    val lastSavedGoalId: Int? = null,
    val celebrationGoal: SavingsGoal? = null,
    val authBusy: Boolean = false,
    val authError: String? = null,
    val authNotice: String? = null,
    /** Cuenta nueva creada con Google: falta aceptar los Términos antes de entrar. */
    val googleTermsRequired: Boolean = false,
    /** Movimiento dictado que espera confirmación (monto alto, sin moneda, etc.). */
    val voiceConfirmation: VoiceDraft? = null,
    /** Pregunta dictada que Oye Dinex no resolvió solo: se envía a Dinex IA. */
    val pendingAiQuestion: String? = null,
) {
    val activeAccount get() = accounts.firstOrNull { it.email == email }
    val initialBalanceRequired get() = email != null && !data.onboardingCompleted && data.movements.isEmpty()
    val displayName get() = activeAccount?.name ?: email.orEmpty().substringBefore('@')
    val goals get() = data.savingGoals()
    val streak get() = data.activeStreak()
    val savedToday get() = data.streakDay == currentDayIndex()
    val availableBalance get() = data.availableBalance()
    val monthMovements get() = data.movements.filter { it.isInCurrentMonth() }
}

/** Límite de preguntas a Dinex IA por usuario y por día. */
internal const val DAILY_AI_QUERIES = 3

/** Largo máximo del nombre que aceptan las reglas de Firestore. */
private const val MAX_CLOUD_NAME = 80

/** Owns session and financial actions; composables only render state and send events. */
internal class DinexViewModel(
    private val repository: DinexRepository = DinexRepository(),
    /** null en pruebas: la app funciona solo con los datos del equipo. */
    private val cloud: DinexCloud? = null,
) : ViewModel(), AiQuota {
    init {
        DinexThemeState.dark = repository.darkMode()
        DinexI18n.language = DinexI18n.fromCode(repository.language())
    }
    private val initialEmail = repository.session()
    private val mutableState = MutableStateFlow(DinexUiState(
        accounts = repository.accounts(),
        email = initialEmail,
        data = initialEmail?.let(repository::profile) ?: FinancialData(),
        reduceMotion = repository.reduceMotion(),
        largeText = repository.largeText(),
        darkMode = repository.darkMode(),
        budgetAlerts = repository.budgetAlertsEnabled(),
        saveReminderEnabled = repository.saveReminderEnabled(),
        saveReminderHour = repository.saveReminderHour(),
        profilePhoto = initialEmail?.let(repository::profilePhoto).orEmpty(),
    ))
    init {
        // Keep the daily Android reminder aligned with the saved preference even
        // when the app was updated, reinstalled or reopened after a reboot.
        DinexAlerts.scheduleSaveReminder(mutableState.value.saveReminderEnabled, mutableState.value.saveReminderHour)
        if (mutableState.value.data.streakDay == currentDayIndex()) DinexAlerts.cancelSaveReminder()
    }
    val state = mutableState.asStateFlow()
    private val value get() = mutableState.value
    private var cloudSession: CloudSession? = repository.cloudSession()
    private var pendingGoogle: GoogleSignIn? = null
    private var syncJob: Job? = null
    private var resumeJob: Job? = null

    init {
        // El servidor seguro de Dinex IA necesita el token de la sesión activa (nunca la contraseña).
        if (cloud != null) DinexAiAuth.idToken = { runCatching { validSession()?.idToken }.getOrNull() }
        val email = initialEmail
        if (cloud != null && email != null) {
            if (cloudSession?.email != email) {
                // Sesiones antiguas solo locales: se pide entrar otra vez para pasar la cuenta a Firebase.
                logout()
                mutableState.value = value.copy(authNotice = "Por seguridad, vuelve a iniciar sesión para proteger tu cuenta en la nube.")
            } else {
                resumeJob = viewModelScope.launch { resumeCloud(email) }
            }
        }
    }
    fun refreshProfile() {
        val email = value.email ?: return
        mutableState.value = value.copy(data = repository.profile(email))
        if (value.data.streakDay == currentDayIndex()) DinexAlerts.cancelSaveReminder()
        // Al volver a la app se trae lo hecho en otros equipos y se sube lo registrado por voz.
        if (cloud != null && cloudSession != null && syncJob?.isActive != true && resumeJob?.isActive != true) {
            resumeJob = viewModelScope.launch { resumeCloud(email) }
        }
    }

    /** Consultas a Dinex IA que le quedan hoy a la cuenta activa (máximo [DAILY_AI_QUERIES]). */
    override fun aiQueriesLeft(): Int = value.email?.let { (DAILY_AI_QUERIES - repository.aiQueriesToday(it)).coerceAtLeast(0) } ?: 0
    override fun recordAiQuery() {
        val email = value.email ?: return
        repository.recordAiQuery(email)
        viewModelScope.launch { pushAiUsage(email) }
    }

    /**
     * Antes de cada pregunta se consulta el conteo en la nube, para que el límite valga para la
     * cuenta en todos sus equipos. Sin internet se usa el conteo guardado en este equipo.
     */
    override suspend fun syncedAiQueriesLeft(): Int {
        val email = value.email ?: return 0
        val cloud = cloud
        if (cloud != null && cloudSession != null) {
            try {
                withTimeoutOrNull(5_000) {
                    val session = validSession() ?: return@withTimeoutOrNull
                    val remote = cloud.aiUsage(session)
                    remote?.let { repository.mergeAiUsage(email, it) }
                    // Si este equipo tiene más consultas registradas (por ejemplo, se usó sin internet), las sube.
                    val local = repository.aiUsage(email)
                    if (local != null && (remote == null || local.day > remote.day || (local.day == remote.day && local.count > remote.count))) {
                        cloud.saveAiUsage(session, local)
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                // Sin conexión: vale el conteo del equipo.
            }
        }
        return aiQueriesLeft()
    }

    override fun acceptServerAiQuota(remaining: Int) {
        val email = value.email ?: return
        repository.mergeAiUsage(email, AiUsage(currentDayIndex(), (DAILY_AI_QUERIES - remaining).coerceIn(0, DAILY_AI_QUERIES)))
    }

    override suspend fun submitAiReport(report: AiReport): String? {
        val cloud = cloud ?: return "Los reportes necesitan una cuenta en la nube."
        val session = try { validSession() } catch (cancel: CancellationException) { throw cancel } catch (_: Throwable) { null }
            ?: return "Inicia sesión para enviar el reporte."
        return try {
            cloud.reportAiAnswer(session, report)
            null
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            "No pude enviar el reporte. Revisa tu internet e inténtalo otra vez."
        }
    }

    private suspend fun pushAiUsage(email: String) {
        val cloud = cloud ?: return
        if (cloudSession == null) return
        try {
            val session = validSession() ?: return
            repository.aiUsage(email)?.let { cloud.saveAiUsage(session, it) }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            // Se reintenta en la próxima consulta.
        }
    }

    fun selectPage(page: Int) { mutableState.value = value.copy(page = page.coerceIn(0, 3)) }
    fun showMessage(message: String?) { mutableState.value = value.copy(message = message) }
    fun setReduceMotion(enabled: Boolean) {
        repository.setReduceMotion(enabled)
        mutableState.value = value.copy(reduceMotion = enabled)
    }
    fun setLargeText(enabled: Boolean) {
        repository.setLargeText(enabled)
        mutableState.value = value.copy(largeText = enabled)
    }
    /** Cambia el idioma de toda la app y lo guarda en este equipo. */
    fun setLanguage(language: DinexLanguage) {
        repository.setLanguage(language.code)
        DinexI18n.language = language
    }

    fun setDarkMode(enabled: Boolean) {
        repository.setDarkMode(enabled)
        DinexThemeState.dark = enabled
        mutableState.value = value.copy(darkMode = enabled)
    }
    fun setBudgetAlerts(enabled: Boolean) {
        repository.setBudgetAlertsEnabled(enabled)
        mutableState.value = value.copy(budgetAlerts = enabled)
    }
    fun setSaveReminderEnabled(enabled: Boolean) {
        repository.setSaveReminderEnabled(enabled)
        mutableState.value = value.copy(saveReminderEnabled = enabled)
        DinexAlerts.scheduleSaveReminder(enabled, value.saveReminderHour)
        if (!enabled) DinexAlerts.cancelSaveReminder()
    }
    fun setSaveReminderHour(hour: Int) {
        val safe = hour.coerceIn(0, 23)
        repository.setSaveReminderHour(safe)
        mutableState.value = value.copy(saveReminderHour = safe)
        if (value.saveReminderEnabled) DinexAlerts.scheduleSaveReminder(true, safe)
    }
    fun setProfilePhoto(encoded: String?): Boolean {
        val email = value.email ?: return false
        if (runCatching { repository.setProfilePhoto(email, encoded) }.isFailure) return false
        mutableState.value = value.copy(profilePhoto = encoded.orEmpty())
        return true
    }

    fun createAccount(account: UserAccount) {
        if (value.accounts.any { it.email == account.email }) return
        val accounts = value.accounts + account
        repository.saveAccounts(accounts)
        repository.saveProfile(account.email, FinancialData())
        repository.setSession(account.email)
        mutableState.value = sessionState(accounts, account.email)
    }

    fun authenticate(email: String) {
        if (value.accounts.none { it.email == email }) return
        repository.setSession(email)
        mutableState.value = sessionState(value.accounts, email)
        if (value.data.streakDay == currentDayIndex()) DinexAlerts.cancelSaveReminder()
    }

    /** Estado al cambiar de sesión: conserva las preferencias del equipo y carga los datos de la cuenta. */
    private fun sessionState(accounts: List<UserAccount>, email: String?, authBusy: Boolean = false) = DinexUiState(
        accounts = accounts,
        email = email,
        data = email?.let(repository::profile) ?: FinancialData(),
        reduceMotion = value.reduceMotion,
        largeText = value.largeText,
        darkMode = value.darkMode,
        budgetAlerts = value.budgetAlerts,
        saveReminderEnabled = value.saveReminderEnabled,
        saveReminderHour = value.saveReminderHour,
        profilePhoto = email?.let(repository::profilePhoto).orEmpty(),
        authBusy = authBusy,
    )

    // ── Cuentas en Firebase ───────────────────────────────────────────────────
    fun signIn(email: String, password: String) = launchAuth {
        val cloud = cloud ?: return@launchAuth null
        val legacy = value.accounts.firstOrNull { it.email == email && !it.passwordDigest.startsWith("cloud:") }
        val legacyMatches = legacy != null && legacy.passwordDigest == credentialDigest(email, password)
        val session = try {
            cloud.signIn(email, password)
        } catch (error: CloudAuthException) {
            // Cuenta antigua de este equipo: si su clave coincide, se crea en Firebase y se suben sus datos.
            if (!legacyMatches || error.code !in setOf("INVALID_LOGIN_CREDENTIALS", "EMAIL_NOT_FOUND")) throw error
            cloud.signUp(email, password).also { runCatching { cloud.sendEmailVerification(it) } }
        }
        completeCloudLogin(session, legacy?.name.orEmpty(), trustLocal = legacyMatches)
        null
    }

    fun register(name: String, email: String, password: String) = launchAuth {
        val cloud = cloud ?: return@launchAuth null
        val session = cloud.signUp(email, password)
        val verification = runCatching { cloud.sendEmailVerification(session) }.isSuccess
        repository.saveProfile(session.email, FinancialData())
        // Constancia del consentimiento dado en la casilla del registro.
        repository.setTermsConsent(session.email, TermsConsent(DinexLegal.VERSION, epochMillis()))
        completeCloudLogin(session, name, trustLocal = true)
        if (verification) t("Te enviamos un correo a {0} para verificar tu cuenta.", session.email) else null
    }

    fun sendPasswordReset(email: String) = launchAuth {
        try {
            cloud?.sendPasswordReset(email)
        } catch (error: CloudAuthException) {
            if (error.code != "EMAIL_NOT_FOUND") throw error
        }
        // El mismo mensaje exista o no la cuenta, para no revelar qué correos están registrados.
        t("Si {0} tiene una cuenta, te llegará un enlace para crear una nueva contraseña.", email)
    }

    /**
     * Quien ya tiene cuenta entra directo: aceptó los Términos al crearla.
     * Solo una cuenta nueva debe aceptarlos, en el formulario o en el aviso posterior a Google.
     */
    fun signInWithGoogle(identity: GoogleIdentity, acceptedTerms: Boolean = false) = launchAuth {
        val cloud = cloud ?: return@launchAuth null
        val token = identity.requestToken() ?: return@launchAuth null
        val google = cloud.signInWithGoogle(token)
        when {
            cloud.download(google.session) != null -> completeCloudLogin(google.session, google.name, trustLocal = false)
            acceptedTerms -> finishNewGoogleAccount(cloud, google)
            else -> {
                pendingGoogle = google
                mutableState.value = value.copy(googleTermsRequired = true)
            }
        }
        null
    }

    fun acceptGoogleTerms() = launchAuth {
        val cloud = cloud ?: return@launchAuth null
        val google = pendingGoogle ?: return@launchAuth null
        val session = if (google.session.fresh()) google.session else cloud.refresh(google.session)
        finishNewGoogleAccount(cloud, google.copy(session = session))
        // Se conserva hasta terminar: si se corta el internet, la persona puede volver a aceptar.
        pendingGoogle = null
        null
    }

    /** Sin consentimiento no se conserva la cuenta que Google acaba de crear. */
    fun declineGoogleTerms() = launchAuth {
        val google = pendingGoogle
        pendingGoogle = null
        mutableState.value = value.copy(googleTermsRequired = false)
        // Recién creada: aún no tiene datos en Firestore, solo se borra la cuenta.
        if (google?.isNewUser == true) runCatching { cloud?.deleteAuthUser(google.session) }
        "No creamos tu cuenta porque no aceptaste los Términos."
    }

    private suspend fun finishNewGoogleAccount(cloud: DinexCloud, google: GoogleSignIn) {
        repository.setTermsConsent(google.session.email, TermsConsent(DinexLegal.VERSION, epochMillis()))
        completeCloudLogin(google.session, google.name, trustLocal = false)
        runCatching { cloud.upload(google.session, localCloudProfile(google.session.email, value.activeAccount?.name.orEmpty(), epochMillis())) }
    }

    /**
     * Cómo puede la persona confirmar su identidad antes de eliminar la cuenta:
     * "password", "google.com" o ambos. Una cuenta solo de este equipo devuelve un conjunto vacío.
     */
    fun loadDeleteMethods(onDone: (methods: Set<String>?, error: String?) -> Unit) {
        val cloud = cloud
        if (cloud == null || cloudSession == null) { onDone(emptySet(), null); return }
        viewModelScope.launch {
            val result = try {
                val session = validSession() ?: throw CloudAuthException("TOKEN_EXPIRED")
                cloud.signInMethods(session) to null
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: CloudAuthException) {
                if (sessionRevoked(error.code)) null to "Tu sesión expiró. Cierra sesión, vuelve a entrar y repite la eliminación."
                else null to cloudErrorMessage(error.code)
            } catch (_: Throwable) {
                null to "Necesitas internet para eliminar tu cuenta."
            }
            onDone(result.first, result.second)
        }
    }

    /**
     * Derecho de cancelación (ARCO). Pasos:
     * 1. Confirma que es la misma persona: contraseña ([password]) o Google ([google]). Así Firebase
     *    acepta borrar la cuenta (exige un inicio de sesión reciente) antes de tocar ningún dato.
     * 2. Borra los reportes de Dinex IA y el documento users/{uid}.
     * 3. Recién entonces borra la cuenta de Firebase Authentication.
     * 4. Borra todo lo que este equipo guarda de la cuenta.
     * Si algo falla antes del paso 3, la cuenta sigue activa y se repone su copia en la nube.
     */
    fun deleteAccount(password: String?, google: GoogleIdentity?, onDone: (String?) -> Unit) {
        val email = value.email ?: return
        val cloud = cloud
        val current = cloudSession
        viewModelScope.launch {
            if (cloud == null || current == null) {
                // Cuenta antigua guardada solo en este equipo: no hay nada en la nube.
                forgetDeletedAccount(email)
                onDone(null)
                return@launch
            }
            var fresh: CloudSession? = null
            var cloudDataTouched = false
            val error = try {
                fresh = reauthenticate(cloud, current, email, password, google)
                syncJob?.cancel()
                cloudDataTouched = true
                cloud.deleteAiReports(fresh)
                cloud.deleteProfile(fresh)
                cloud.deleteAuthUser(fresh)
                null
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: ReauthException) {
                error.message
            } catch (error: CloudAuthException) {
                when (error.code) {
                    "INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD" -> "La contraseña no es correcta."
                    "CREDENTIAL_TOO_OLD_LOGIN_AGAIN" -> "Firebase pidió confirmar tu identidad otra vez. Vuelve a intentarlo."
                    else -> cloudErrorMessage(error.code)
                } + if (cloudDataTouched) " Tu cuenta no se eliminó." else ""
            } catch (_: Throwable) {
                "No hay conexión a internet. Revisa tu conexión e inténtalo otra vez." + if (cloudDataTouched) " Tu cuenta no se eliminó." else ""
            }
            if (error == null) {
                forgetDeletedAccount(email)
            } else {
                fresh?.let { session ->
                    cloudSession = session
                    repository.setCloudSession(session)
                    // La cuenta sigue activa: se repone su copia en la nube con lo que hay en este equipo.
                    if (cloudDataTouched) runCatching { cloud.upload(session, localCloudProfile(email, value.activeAccount?.name.orEmpty(), epochMillis())) }
                }
            }
            onDone(error)
        }
    }

    private class ReauthException(message: String) : Exception(message)

    /** Vuelve a iniciar sesión con la misma cuenta para obtener un permiso reciente de Firebase. */
    private suspend fun reauthenticate(cloud: DinexCloud, current: CloudSession, email: String, password: String?, google: GoogleIdentity?): CloudSession {
        val fresh = when {
            !password.isNullOrBlank() -> cloud.signIn(email, password)
            google != null -> {
                val token = google.requestToken() ?: throw ReauthException("Confirma con Google para eliminar tu cuenta.")
                val result = cloud.signInWithGoogle(token)
                if (result.isNewUser) {
                    // Eligió otra cuenta de Google y Firebase la acaba de crear: se borra de inmediato.
                    runCatching { cloud.deleteAuthUser(result.session) }
                }
                result.session
            }
            else -> throw ReauthException("Escribe tu contraseña o confirma con Google.")
        }
        if (fresh.uid != current.uid) throw ReauthException("Esa no es la cuenta que quieres eliminar. Usa la misma cuenta con la que entraste.")
        return fresh
    }

    /** Borra de este equipo todo lo de la cuenta eliminada y cierra la sesión. */
    private fun forgetDeletedAccount(email: String) {
        syncJob?.cancel()
        repository.forgetAccount(email)
        logout()
        mutableState.value = value.copy(accounts = repository.accounts(), authNotice = "Tu cuenta y tus datos fueron eliminados.")
    }

    /**
     * El documento de la cuenta no está en la nube. Si además Firebase ya no renueva la sesión,
     * la cuenta se eliminó (desde otro equipo o la web): se informa como USER_NOT_FOUND.
     */
    private suspend fun confirmAccountExists(cloud: DinexCloud, session: CloudSession) {
        try {
            cloud.refresh(session)
        } catch (error: CloudAuthException) {
            throw if (sessionRevoked(error.code)) CloudAuthException("USER_NOT_FOUND") else error
        }
    }

    /**
     * La sesión guardada ya no sirve. Si Firebase dice que la cuenta no existe (se eliminó desde
     * otro equipo o la web), también se borra la copia de sus datos que quedó en este equipo.
     */
    private fun endRevokedSession(email: String, code: String) {
        if (code == "USER_NOT_FOUND") {
            repository.forgetAccount(email)
            logout()
            mutableState.value = value.copy(accounts = repository.accounts(), authNotice = "Esta cuenta fue eliminada. Borramos también sus datos de este equipo.")
        } else {
            logout()
            mutableState.value = value.copy(authNotice = "Tu sesión expiró. Vuelve a iniciar sesión.")
        }
    }

    private fun localCloudProfile(email: String, name: String, updatedAt: Long) =
        CloudProfile(name.take(MAX_CLOUD_NAME), email, cloudProfileText(repository.profile(email)), updatedAt, repository.termsConsent(email))

    fun clearAuthMessages() { mutableState.value = value.copy(authError = null, authNotice = null) }

    private fun launchAuth(block: suspend () -> String?) {
        if (value.authBusy) return
        mutableState.value = value.copy(authBusy = true, authError = null, authNotice = null)
        viewModelScope.launch {
            var notice: String? = null
            val error = try {
                notice = block(); null
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: CloudAuthException) {
                cloudErrorMessage(error.code)
            } catch (_: Throwable) {
                "Sin conexión a internet. Revísala e inténtalo de nuevo."
            }
            mutableState.value = value.copy(authBusy = false, authError = error, authNotice = notice)
        }
    }

    /**
     * Une los datos del equipo con los de la nube. Los datos locales solo se suben si
     * pertenecen de verdad a esta cuenta (misma uid o cuenta antigua con la clave correcta),
     * así nadie puede mezclar datos ajenos en una cuenta creando un perfil local con su correo.
     */
    private suspend fun completeCloudLogin(session: CloudSession, name: String, trustLocal: Boolean) {
        val cloud = cloud ?: return
        val email = session.email.trim().lowercase()
        val account = session.copy(email = email)
        val linked = value.accounts.any { it.email == email && it.passwordDigest == "cloud:${session.uid}" }
        val remote = cloud.download(session)
        val displayName = remote?.name?.takeIf { it.isNotBlank() } ?: name.ifBlank { email.substringBefore('@') }
        if (!linked && !trustLocal && unpackProfileOrNull(remote?.profile) == null) repository.saveProfile(email, FinancialData())
        reconcile(cloud, session, email, displayName, remote, preferRemote = !linked && !trustLocal)
        val accounts = value.accounts.filterNot { it.email == email } + UserAccount(displayName, email, "cloud:${session.uid}")
        repository.saveAccounts(accounts)
        cloudSession = account
        repository.setCloudSession(account)
        repository.setSession(email)
        mutableState.value = sessionState(accounts, email, authBusy = true)
        if (value.data.streakDay == currentDayIndex()) DinexAlerts.cancelSaveReminder()
    }

    private suspend fun reconcile(cloud: DinexCloud, session: CloudSession, email: String, name: String, remote: CloudProfile?, preferRemote: Boolean = false) {
        val localAt = repository.profileUpdatedAt(email)
        val remoteData = unpackProfileOrNull(remote?.profile)
        if (remote != null && remoteData != null && (preferRemote || remote.updatedAt >= localAt)) {
            val merged = mergeCloudProfile(remoteData, repository.profile(email))
            remote.terms?.let { repository.setTermsConsent(email, it) }
            repository.saveProfile(email, merged, remote.updatedAt)
            if (value.email == email) mutableState.value = value.copy(data = merged)
        } else {
            val stamp = if (localAt > 1) localAt else epochMillis()
            // Sin documento en la nube: antes de crearlo se confirma que la cuenta sigue existiendo
            // (si se eliminó desde otro equipo, Firebase responde USER_NOT_FOUND).
            if (remote == null) confirmAccountExists(cloud, session)
            cloud.upload(session, localCloudProfile(email, name, stamp), mustExist = remote != null)
        }
    }

    private suspend fun resumeCloud(email: String) {
        val cloud = cloud ?: return
        try {
            val session = validSession() ?: return
            reconcile(cloud, session, email, value.activeAccount?.name.orEmpty(), cloud.download(session))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: CloudAuthException) {
            if (sessionRevoked(error.code)) endRevokedSession(email, error.code)
        } catch (_: Throwable) {
            // Sin internet: se sigue usando lo guardado en el equipo y se sube en el próximo cambio.
        }
    }

    private suspend fun validSession(): CloudSession? {
        val cloud = cloud ?: return null
        val current = cloudSession ?: return null
        if (current.fresh()) return current
        return cloud.refresh(current).also {
            // Si mientras tanto se cerró sesión o cambió la cuenta, esta renovación ya no aplica.
            if (cloudSession?.uid == current.uid) {
                cloudSession = it
                repository.setCloudSession(it)
            }
        }
    }

    private fun scheduleSync() {
        if (cloud == null || cloudSession == null) return
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(1_500)
            val email = value.email ?: return@launch
            try {
                val session = validSession() ?: return@launch
                val profile = localCloudProfile(email, value.activeAccount?.name.orEmpty(), repository.profileUpdatedAt(email))
                try {
                    cloud.upload(session, profile, mustExist = true)
                } catch (missing: CloudAuthException) {
                    if (missing.code != "NOT_FOUND") throw missing
                    // El documento no existe: solo se vuelve a crear si la cuenta sigue activa.
                    confirmAccountExists(cloud, session)
                    cloud.upload(session, profile)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: CloudAuthException) {
                if (sessionRevoked(error.code)) endRevokedSession(email, error.code)
            } catch (_: Throwable) {
                // Sin internet: el cambio queda guardado en el equipo y se sube después.
            }
        }
    }

    fun logout() {
        val pending = syncJob?.isActive == true
        syncJob?.cancel()
        resumeJob?.cancel()
        pendingGoogle = null
        val session = cloudSession
        val email = value.email
        if (pending && cloud != null && session != null && email != null) {
            // Termina de subir el último cambio antes de cerrar la sesión.
            val profile = localCloudProfile(email, value.activeAccount?.name.orEmpty(), repository.profileUpdatedAt(email))
            viewModelScope.launch { runCatching { cloud.upload(if (session.fresh()) session else cloud.refresh(session), profile, mustExist = true) } }
        }
        cloudSession = null
        repository.setCloudSession(null)
        repository.setSession(null)
        mutableState.value = sessionState(value.accounts, email = null)
    }

    fun nextMovementId() = (value.data.movements.maxOfOrNull { it.id } ?: 0) + 1
    fun nextReminderId() = (value.data.reminders.maxOfOrNull { it.id } ?: 0) + 1
    fun saveMovement(movement: Movement, confirmed: Boolean = false, reminderId: Int? = null) {
        if (!movement.amount.isFinite() || movement.amount <= 0) return
        val movements = value.data.movements
        val exists = movements.any { it.id == movement.id }
        // Each new expense captures the current place so the heatmap fills up
        // automatically. Edits and incomes keep their original location.
        val located = if (!movement.income && !exists && movement.latitude == null) {
            val current = runCatching { DinexLocationProvider.current?.invoke() }.getOrNull()
            if (current != null && current.valid) movement.copy(
                location = movement.location.ifBlank { current.label },
                latitude = current.latitude,
                longitude = current.longitude,
            ) else movement
        } else movement
        val before = movements.filter { !it.income && it.isToday() }.sumOf { it.amount }
        val previous = movements.firstOrNull { it.id == movement.id }?.takeIf { !it.income && it.isToday() }?.amount ?: 0.0
        val after = before - previous + if (!located.income && located.isToday()) located.amount else 0.0
        if (!confirmed && after > before && before >= value.data.dailyLimit && after > value.data.dailyLimit) {
            mutableState.value = value.copy(pendingExpense = located, pendingReminderId = reminderId)
            return
        }
        persist(value.data.copy(
            movements = if (exists) movements.map { if (it.id == located.id) located else it } else listOf(located) + movements,
            reminders = if (reminderId == null) value.data.reminders else value.data.reminders.filterNot { it.id == reminderId },
        ))
        val limit = value.data.dailyLimit.toDouble()
        if (after > before && after > limit) {
            mutableState.value = value.copy(
                budgetWarning = t("Hoy gastaste {0}. Superaste tu presupuesto de {1} por {2}.", money(after), money(limit), money(after - limit)),
            )
            // Only fire the real notification when the limit is crossed, not on
            // every extra expense once the day is already over budget.
            if (value.budgetAlerts && before <= limit) DinexAlerts.budgetExceeded(after, limit)
        }
        showMessage(if (located.income) "Ingreso guardado" else "Gasto guardado")
    }
    fun dismissBudgetWarning() { mutableState.value = value.copy(budgetWarning = null) }
    fun cancelPendingExpense() { mutableState.value = value.copy(pendingExpense = null, pendingReminderId = null) }
    fun confirmPendingExpense() {
        val movement = value.pendingExpense ?: return
        val reminderId = value.pendingReminderId
        cancelPendingExpense()
        saveMovement(movement, confirmed = true, reminderId = reminderId)
    }
    fun deleteMovement(movement: Movement) {
        persist(value.data.copy(movements = value.data.movements.filterNot { it.id == movement.id }))
        showMessage("Movimiento eliminado")
    }
    fun importMovements(items: List<Movement>) {
        val before = value.data.movements.filter { !it.income && it.isToday() }.sumOf { it.amount }
        val firstId = nextMovementId()
        val clean = items.filter { it.amount.isFinite() && it.amount > 0 && validDate(it.year, it.month, it.day) }.mapIndexed { index, item -> item.copy(id = firstId + index) }
        persist(value.data.copy(movements = clean + value.data.movements))
        val limit = value.data.dailyLimit.toDouble()
        val after = value.data.movements.filter { !it.income && it.isToday() }.sumOf { it.amount }
        if (after > before && after > limit) {
            mutableState.value = value.copy(budgetWarning = t("Los movimientos importados llevan el gasto de hoy a {0}, por encima de tu límite de {1}.", money(after), money(limit)))
            if (value.budgetAlerts && before <= limit) DinexAlerts.budgetExceeded(after, limit)
        }
        showMessage(countLabel(clean.size, "movimiento importado", "movimientos importados"))
    }
    fun importNotifications(items: List<CapturedTransaction>, location: String?) {
        importMovements(items.map {
            val date = it.capturedDate ?: civilDateFromEpochDays()
            Movement(0, it.title, if (it.income) "Ingreso" else "Otros", it.amount, it.income, date.day, it.source, location.orEmpty(), date.month, date.year)
        })
    }
    fun saveReminder(reminder: PaymentReminder) {
        persist(value.data.copy(reminders = listOf(reminder) + value.data.reminders))
        showMessage("Recordatorio agregado")
    }
    fun resolveReminder(reminder: PaymentReminder, registerExpense: Boolean) {
        if (value.data.reminders.none { it.id == reminder.id }) return
        if (registerExpense && reminder.amount.isFinite() && reminder.amount > 0) {
            saveMovement(Movement(nextMovementId(), reminder.title, categoryForReminder(reminder.title), reminder.amount, false, currentDayOfMonth(), "Recordatorio pagado"), reminderId = reminder.id)
        } else {
            persist(value.data.copy(reminders = value.data.reminders.filterNot { it.id == reminder.id }))
            showMessage("Recordatorio eliminado")
        }
    }
    fun setDailyLimit(limit: Float) { persist(value.data.copy(dailyLimit = limit.coerceIn(20f, 120f))) }
    fun setCategoryBudgets(budgets: Map<String, Double>): Boolean {
        if (budgets.any { (category, amount) -> categories.none { it.name == category } || !amount.isFinite() || amount < 0 }) return false
        persist(value.data.copy(categoryBudgets = budgets))
        showMessage("Presupuestos actualizados")
        return true
    }
    fun resetCategoryBudgets() {
        persist(value.data.copy(categoryBudgets = emptyMap()))
        showMessage("Volviste a las sugerencias automáticas")
    }
    fun deleteSavingsGoal(id: Int): Boolean {
        val goals = value.data.savingGoals()
        val goal = goals.firstOrNull { it.id == id } ?: return false
        val remaining = goals.filterNot { it.id == id }
        val last = remaining.lastOrNull()
        persist(value.data.copy(goals = remaining, nextGoalId = maxOf(value.data.nextGoalId, (goals.maxOfOrNull { it.id } ?: 0) + 1), savings = remaining.sumOf { it.saved }, goalName = last?.name.orEmpty(),
            goalTarget = last?.target ?: 0.0, goalArtwork = last?.artwork.orEmpty()))
        mutableState.value = value.copy(lastSavedGoalId = last?.id, goalCelebration = false, celebrationGoal = null,
            message = t("Meta eliminada. {0} vuelven a tu saldo disponible.", money(goal.saved)))
        return true
    }
    fun setInitialBalance(amount: Double) {
        if (value.data.onboardingCompleted) return
        val items = if (amount.isFinite() && amount > 0) listOf(Movement(nextMovementId(), "Saldo inicial", "Ingreso", amount, true, currentDayOfMonth(), "Saldo inicial")) else emptyList()
        persist(value.data.copy(movements = items + value.data.movements, onboardingCompleted = true))
        showMessage(if (items.isEmpty()) "Empezamos con saldo cero" else "Saldo inicial guardado")
    }
    fun saveSaving(entry: SavingEntry, today: Int = currentDayIndex()): Boolean {
        if (!entry.amount.isFinite() || entry.amount <= 0) return false
        if (entry.amount > value.data.availableBalance() + 0.000001) {
            showMessage(t("Saldo insuficiente. Tienes {0} disponibles para ahorrar.", money(value.data.availableBalance())))
            return false
        }
        val old = value.data
        val goals = old.savingGoals()
        val existing = if (entry.goalId != null) goals.firstOrNull { it.id == entry.goalId } ?: return false
            else goals.firstOrNull { it.name == entry.purpose.trim().ifBlank { "Mi ahorro" } }
        val goal = (existing ?: SavingsGoal(maxOf(old.nextGoalId, (goals.maxOfOrNull { it.id } ?: 0) + 1), entry.purpose.trim().ifBlank { "Mi ahorro" }, 0.0)).let {
            it.copy(saved = it.saved + entry.amount, target = entry.target.takeIf { target -> target.isFinite() && target > 0 } ?: it.target)
        }
        val updatedGoals = if (existing == null) goals + goal else goals.map { if (it.id == goal.id) goal else it }
        // Consecutive days matter; repeated deposits on one day do not inflate the streak.
        val streak = when (old.streakDay) {
            today -> old.streak
            today - 1 -> old.streak + 1
            else -> 1
        }
        val next = old.copy(
            savings = updatedGoals.sumOf { it.saved },
            goals = updatedGoals,
            nextGoalId = maxOf(old.nextGoalId, goal.id + 1),
            streak = streak,
            streakDay = today,
            goalName = goal.name,
            goalTarget = goal.target,
            goalArtwork = goal.artwork,
        )
        persist(next)
        mutableState.value = value.copy(
            // Celebra cada vez que la racha cruza el inicio de un nivel, también al ganar la primera medalla (Cobre).
            levelCelebration = streakLevel(streak).takeIf { old.streak < it.startsAt && streak >= it.startsAt },
            lastSavedGoalId = goal.id,
            celebrationGoal = goal,
            goalCelebration = goal.target > 0 && goal.saved >= goal.target && (existing?.saved ?: 0.0) < goal.target,
            message = t("Ahorro de {0} guardado", money(entry.amount)),
        )
        // The person already saved today: remove a nudge that may still be on screen.
        DinexAlerts.cancelSaveReminder()
        return true
    }
    fun dismissCelebrations() { mutableState.value = value.copy(goalCelebration = false, levelCelebration = null) }
    private val voice = VoiceConversation()

    /** Comando de voz dentro de la app: registra, responde con datos reales o pide confirmación. */
    fun handleVoice(text: String, location: String?) {
        if (value.email == null) return
        // La burbuja de Oye Dinex envía aquí las preguntas que deben ir a Dinex IA.
        if (text.startsWith(VOICE_AI_PREFIX)) {
            mutableState.value = value.copy(pendingAiQuestion = text.removePrefix(VOICE_AI_PREFIX).trim().take(500).ifBlank { null })
            return
        }
        when (val outcome = voice.interpret(text, value.data)) {
            is VoiceOutcome.SaveReminder -> { saveReminder(outcome.reminder); selectPage(2) }
            is VoiceOutcome.Save -> saveVoiceDraft(outcome.draft, location)
            is VoiceOutcome.Confirm -> mutableState.value = value.copy(voiceConfirmation = outcome.draft)
            is VoiceOutcome.Answer -> showMessage(outcome.text)
            is VoiceOutcome.Cancelled -> showMessage(outcome.text)
            is VoiceOutcome.Help -> showMessage(outcome.text)
            is VoiceOutcome.AskAi -> mutableState.value = value.copy(pendingAiQuestion = outcome.question)
        }
    }

    /** Respuesta del cuadro "¿Guardo…?" de un movimiento dictado. */
    fun confirmVoice(save: Boolean, location: String? = null) {
        val draft = value.voiceConfirmation ?: return
        voice.reset()
        mutableState.value = value.copy(voiceConfirmation = null)
        if (save) saveVoiceDraft(draft, location) else showMessage("Listo, no guardé nada.")
    }

    fun consumePendingAiQuestion(): String? = value.pendingAiQuestion?.also {
        mutableState.value = value.copy(pendingAiQuestion = null)
    }

    private fun saveVoiceDraft(draft: VoiceDraft, location: String?) {
        val movement = draft.movement.copy(id = nextMovementId(), location = location.orEmpty())
        saveMovement(movement)
        selectPage(1)
    }

    private fun persist(data: FinancialData) {
        val email = value.email ?: return
        repository.saveProfile(email, data)
        mutableState.value = value.copy(data = data)
        scheduleSync()
    }
}
