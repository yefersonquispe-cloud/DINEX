package com.example.dinex

internal interface DinexStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

internal object PlatformDinexStore : DinexStore {
    override fun get(key: String) = readLocalValue(key)
    override fun put(key: String, value: String?) = writeLocalValue(key, value)
}

internal data class FinancialData(
    val movements: List<Movement> = emptyList(),
    val reminders: List<PaymentReminder> = emptyList(),
    val savings: Double = 0.0,
    val streak: Int = 0,
    val streakDay: Int? = null,
    val goalName: String = "",
    val goalTarget: Double = 0.0,
    val dailyLimit: Float = 45f,
    val onboardingCompleted: Boolean = false,
    val categoryBudgets: Map<String, Double> = emptyMap(),
    val goalArtwork: String = "",
    val goals: List<SavingsGoal> = emptyList(),
    val nextGoalId: Int = 1,
)

internal data class SavingsGoal(val id: Int, val name: String, val target: Double, val saved: Double = 0.0, val artwork: String = "")

/** Legacy accounts retain their original reserved money until their first goal action. */
internal fun FinancialData.savingGoals(): List<SavingsGoal> = goals.ifEmpty {
    if (savings > 0 || goalName.isNotBlank()) listOf(SavingsGoal(1, goalName.ifBlank { "Mi ahorro" }, goalTarget, savings, goalArtwork)) else emptyList()
}

/** The stored streak only lives while the last saving was today or yesterday; after a missed day it is 0. */
internal fun FinancialData.activeStreak(today: Int = currentDayIndex()): Int =
    if (streakDay == today || streakDay == today - 1) streak else 0

internal fun FinancialData.availableBalance(): Double = movements.sumOf { if (it.income) it.amount else -it.amount } - savings

/** Each profile is one atomic SQLite row, preventing partial saves and account mixing. */
internal class DinexRepository(
    private val store: DinexStore = PlatformDinexStore,
    migrateLegacy: Boolean = true,
) {
    init {
        if (migrateLegacy && store.get("migration_v2") == null) {
            val accounts = decodeAccounts(loadSavedAccounts())
            saveAccounts(accounts)
            val email = loadSavedEmail()
            store.put("session", email.orEmpty())
            if (email != null && store.get(profileKey(email)) == null) {
                saveProfile(email, FinancialData(
                    movements = decodeMovements(loadSavedMovements()),
                    reminders = decodeReminders(loadSavedReminders()),
                    savings = loadSavedSavings() ?: 0.0,
                    streak = loadSavedStreak() ?: 0,
                    streakDay = loadSavedStreakDay(),
                    goalName = loadSavedGoalName().orEmpty(),
                    goalTarget = loadSavedGoalTarget() ?: 0.0,
                    onboardingCompleted = loadSavedOnboardingCompleted(),
                ))
            }
            store.put("migration_v2", "complete")
        }
    }

    fun accounts(): List<UserAccount> = decodeAccounts(store.get("accounts_v2"))
    fun saveAccounts(accounts: List<UserAccount>) = store.put("accounts_v2", encodeAccounts(accounts))
    fun session(): String? = store.get("session")?.takeIf { it.isNotBlank() }
    fun setSession(email: String?) = store.put("session", email.orEmpty())
    fun profile(email: String): FinancialData = unpackProfile(store.get(profileKey(email)))
    fun saveProfile(email: String, data: FinancialData, updatedAt: Long = epochMillis()) {
        store.put(profileKey(email), packProfile(data))
        store.put("profile_at:" + email.trim().lowercase(), updatedAt.toString())
    }
    /** Momento del último cambio local; 0 si el perfil nunca se guardó en este equipo. */
    fun profileUpdatedAt(email: String): Long = store.get("profile_at:" + email.trim().lowercase())?.toLongOrNull()
        ?: if (store.get(profileKey(email)) != null) 1L else 0L
    fun termsConsent(email: String): TermsConsent? = store.get("terms:" + email.trim().lowercase())
        ?.split('|')?.takeIf { it.size == 2 }?.let { TermsConsent(it[0], it[1].toLongOrNull() ?: 0L) }
    fun setTermsConsent(email: String, consent: TermsConsent?) =
        store.put("terms:" + email.trim().lowercase(), consent?.let { "${it.version}|${it.acceptedAt}" })
    /** Consultas a Dinex IA que esta cuenta hizo hoy. Al cambiar el día vuelve a 0. */
    fun aiQueriesToday(email: String, today: Int = currentDayIndex()): Int =
        store.get("ai_usage:" + email.trim().lowercase())?.split('|')
            ?.takeIf { it.size == 2 && it[0].toIntOrNull() == today }?.get(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    fun recordAiQuery(email: String, today: Int = currentDayIndex()) =
        store.put("ai_usage:" + email.trim().lowercase(), "$today|${aiQueriesToday(email, today) + 1}")
    /** Borra todo lo que este equipo guarda de una cuenta. */
    fun forgetAccount(email: String) {
        val key = email.trim().lowercase()
        listOf(profileKey(email), "profile_at:$key", "avatar:$key", "terms:$key", "ai_usage:$key").forEach { store.put(it, null) }
        saveAccounts(accounts().filterNot { it.email == key })
    }
    /** Sesión de Firebase (uid, correo y token de renovación). Nunca se guarda la contraseña. */
    fun cloudSession(): CloudSession? {
        val fields = store.get("cloud_session")?.split('|')?.takeIf { it.size == 3 && it.none(String::isBlank) } ?: return null
        return CloudSession(uid = fields[0], email = fields[1], idToken = "", refreshToken = fields[2], expiresAt = 0)
    }
    fun setCloudSession(session: CloudSession?) =
        store.put("cloud_session", session?.let { "${it.uid}|${it.email}|${it.refreshToken}" })
    fun reduceMotion(): Boolean = store.get("reduce_motion") == "true"
    fun setReduceMotion(value: Boolean) = store.put("reduce_motion", value.toString())
    fun largeText(): Boolean = store.get("large_text") == "true"
    fun setLargeText(value: Boolean) = store.put("large_text", value.toString())
    fun budgetAlertsEnabled(): Boolean = store.get("alert_budget") != "false"
    fun setBudgetAlertsEnabled(value: Boolean) = store.put("alert_budget", value.toString())
    fun saveReminderEnabled(): Boolean = store.get("save_reminder") != "false"
    fun setSaveReminderEnabled(value: Boolean) = store.put("save_reminder", value.toString())
    fun saveReminderHour(): Int = store.get("save_reminder_hour")?.toIntOrNull()?.takeIf { it in 0..23 } ?: 20
    fun setSaveReminderHour(hour: Int) = store.put("save_reminder_hour", hour.coerceIn(0, 23).toString())
    fun darkMode(): Boolean = store.get("theme_dark") == "true"
    fun setDarkMode(value: Boolean) = store.put("theme_dark", value.toString())
    fun profilePhoto(email: String): String = store.get("avatar:${email.trim().lowercase()}").orEmpty()
    fun setProfilePhoto(email: String, encoded: String?) = store.put("avatar:${email.trim().lowercase()}", encoded)

    private fun profileKey(email: String) = "profile:" + email.trim().lowercase()
}

private fun packFields(fields: List<String>): String = fields.joinToString("") { "${it.length}:$it" }

private fun unpackFields(raw: String?): List<String>? {
    if (raw == null) return null
    val result = mutableListOf<String>()
    var cursor = 0
    while (cursor < raw.length) {
        val end = raw.indexOf(':', cursor)
        if (end < 0) return null
        val length = raw.substring(cursor, end).toIntOrNull() ?: return null
        if (length < 0 || length > raw.length - end - 1) return null
        cursor = end + 1
        result.add(raw.substring(cursor, cursor + length))
        cursor += length
    }
    return result
}

internal fun packProfile(data: FinancialData): String = packFields(listOf(
    "4", encodeMovements(data.movements), encodeReminders(data.reminders),
    data.savings.toString(), data.streak.toString(), data.streakDay?.toString().orEmpty(),
    data.goalName, data.goalTarget.toString(), data.dailyLimit.toString(),
    data.onboardingCompleted.toString(),
    packFields(data.categoryBudgets.entries.flatMap { listOf(it.key, it.value.toString()) }), data.goalArtwork,
    packFields(data.goals.map { packFields(listOf(it.id.toString(), it.name, it.target.toString(), it.saved.toString(), it.artwork)) }),
    data.nextGoalId.toString(),
))

internal fun unpackProfile(raw: String?): FinancialData = unpackProfileOrNull(raw) ?: FinancialData()

/** null cuando el texto no es un perfil válido (por ejemplo, datos dañados en la nube). */
internal fun unpackProfileOrNull(raw: String?): FinancialData? {
    val fields = unpackFields(raw) ?: return null
    if (!(fields.size == 10 && fields[0] == "2") && !(fields.size == 12 && fields[0] == "3") && !(fields.size == 14 && fields[0] == "4")) return null
    return FinancialData(
        movements = decodeMovements(fields[1]),
        reminders = decodeReminders(fields[2]),
        savings = fields[3].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: 0.0,
        streak = fields[4].toIntOrNull()?.coerceAtLeast(0) ?: 0,
        streakDay = fields[5].toIntOrNull(),
        goalName = fields[6],
        goalTarget = fields[7].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: 0.0,
        dailyLimit = fields[8].toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(20f, 120f) ?: 45f,
        onboardingCompleted = fields[9] == "true",
        categoryBudgets = unpackFields(fields.getOrNull(10)).orEmpty().chunked(2).mapNotNull { pair ->
            pair.getOrNull(1)?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.let { pair[0] to it }
        }.toMap(),
        goalArtwork = fields.getOrNull(11).orEmpty(),
        goals = unpackFields(fields.getOrNull(12)).orEmpty().mapNotNull { rawGoal ->
            val goal = unpackFields(rawGoal) ?: return@mapNotNull null
            if (goal.size != 5) return@mapNotNull null
            val id = goal[0].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val target = goal[2].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return@mapNotNull null
            val saved = goal[3].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return@mapNotNull null
            SavingsGoal(id, goal[1], target, saved, goal[4])
        }.distinctBy { it.id },
        nextGoalId = fields.getOrNull(13)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
    )
}

/** Copia para la nube: sin ilustraciones de metas (pesan mucho y Firestore admite hasta 1 MB por documento). */
internal fun cloudProfileText(data: FinancialData): String =
    packProfile(data.copy(goalArtwork = "", goals = data.goals.map { it.copy(artwork = "") }))

/** Al bajar datos de la nube, conserva las ilustraciones que ya estaban en este equipo. */
internal fun mergeCloudProfile(cloud: FinancialData, local: FinancialData): FinancialData {
    val art = local.goals.associate { it.id to it.artwork }
    val goals = cloud.goals.map { goal -> if (goal.artwork.isBlank()) goal.copy(artwork = art[goal.id].orEmpty()) else goal }
    val lastArtwork = goals.lastOrNull { it.name == cloud.goalName }?.artwork ?: local.goalArtwork.takeIf { local.goalName == cloud.goalName }.orEmpty()
    return cloud.copy(goals = goals, goalArtwork = cloud.goalArtwork.ifBlank { lastArtwork })
}
