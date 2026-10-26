package com.example.dinex

import androidx.lifecycle.ViewModel
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
) {
    val activeAccount get() = accounts.firstOrNull { it.email == email }
    val initialBalanceRequired get() = email != null && !data.onboardingCompleted && data.movements.isEmpty()
}

/** Owns session and financial actions; composables only render state and send events. */
internal class DinexViewModel(private val repository: DinexRepository = DinexRepository()) : ViewModel() {
    init { DinexThemeState.dark = repository.darkMode() }
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
    fun refreshProfile() {
        val email = value.email ?: return
        mutableState.value = value.copy(data = repository.profile(email))
        if (value.data.streakDay == currentDayIndex()) DinexAlerts.cancelSaveReminder()
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
        mutableState.value = DinexUiState(accounts, account.email, reduceMotion = value.reduceMotion, largeText = value.largeText, darkMode = value.darkMode,
            budgetAlerts = value.budgetAlerts, saveReminderEnabled = value.saveReminderEnabled, saveReminderHour = value.saveReminderHour)
    }

    fun authenticate(email: String) {
        if (value.accounts.none { it.email == email }) return
        repository.setSession(email)
        mutableState.value = DinexUiState(value.accounts, email, repository.profile(email), reduceMotion = value.reduceMotion, largeText = value.largeText, darkMode = value.darkMode,
            budgetAlerts = value.budgetAlerts, saveReminderEnabled = value.saveReminderEnabled, saveReminderHour = value.saveReminderHour, profilePhoto = repository.profilePhoto(email))
        if (value.data.streakDay == currentDayIndex()) DinexAlerts.cancelSaveReminder()
    }

    fun logout() {
        repository.setSession(null)
        mutableState.value = DinexUiState(accounts = value.accounts, reduceMotion = value.reduceMotion, largeText = value.largeText, darkMode = value.darkMode,
            budgetAlerts = value.budgetAlerts, saveReminderEnabled = value.saveReminderEnabled, saveReminderHour = value.saveReminderHour)
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
                budgetWarning = "Hoy gastaste ${money(after)}. Superaste tu presupuesto de ${money(limit)} por ${money(after - limit)}.",
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
            mutableState.value = value.copy(budgetWarning = "Los movimientos importados llevan el gasto de hoy a ${money(after)}, por encima de tu límite de ${money(limit)}.")
            if (value.budgetAlerts && before <= limit) DinexAlerts.budgetExceeded(after, limit)
        }
        showMessage("${clean.size} movimientos importados")
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
    fun setCategoryBudget(category: String, amount: Double): Boolean {
        if (categories.none { it.name == category } || !amount.isFinite() || amount < 0) return false
        persist(value.data.copy(categoryBudgets = value.data.categoryBudgets + (category to amount)))
        showMessage("Presupuesto de $category actualizado")
        return true
    }
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
    fun saveGoalArtwork(email: String, purpose: String, artwork: String) {
        if (value.email != email || value.data.goalName != purpose || artwork.length > 8_000_000) return
        val goal = value.data.savingGoals().lastOrNull { it.name == purpose } ?: return
        saveGoalArtwork(email, goal.id, purpose, artwork)
    }
    fun saveGoalArtwork(email: String, goalId: Int, purpose: String, artwork: String) {
        if (value.email != email || artwork.length > 8_000_000) return
        val goals = value.data.savingGoals()
        if (goals.none { it.id == goalId && it.name == purpose }) return
        persist(value.data.copy(goals = goals.map { if (it.id == goalId) it.copy(artwork = artwork) else it },
            goalArtwork = if (value.lastSavedGoalId == goalId) artwork else value.data.goalArtwork))
    }
    fun deleteSavingsGoal(id: Int): Boolean {
        val goals = value.data.savingGoals()
        val goal = goals.firstOrNull { it.id == id } ?: return false
        val remaining = goals.filterNot { it.id == id }
        val last = remaining.lastOrNull()
        persist(value.data.copy(goals = remaining, nextGoalId = maxOf(value.data.nextGoalId, (goals.maxOfOrNull { it.id } ?: 0) + 1), savings = remaining.sumOf { it.saved }, goalName = last?.name.orEmpty(),
            goalTarget = last?.target ?: 0.0, goalArtwork = last?.artwork.orEmpty()))
        mutableState.value = value.copy(lastSavedGoalId = last?.id, goalCelebration = false, celebrationGoal = null,
            message = "Meta eliminada. ${money(goal.saved)} vuelven a tu saldo disponible.")
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
            showMessage("Saldo insuficiente. Tienes ${money(value.data.availableBalance())} disponibles para ahorrar.")
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
            levelCelebration = streakLevel(streak).takeIf { streakLevel(streak).startsAt > streakLevel(old.streak).startsAt },
            lastSavedGoalId = goal.id,
            celebrationGoal = goal,
            goalCelebration = goal.target > 0 && goal.saved >= goal.target && (existing?.saved ?: 0.0) < goal.target,
            message = "Ahorro de ${money(entry.amount)} guardado",
        )
        // The person already saved today: remove a nudge that may still be on screen.
        DinexAlerts.cancelSaveReminder()
        return true
    }
    fun dismissCelebrations() { mutableState.value = value.copy(goalCelebration = false, levelCelebration = null) }
    fun handleVoice(text: String, location: String?) {
        val reminder = parseVoiceReminder(text, nextReminderId())
        if (reminder != null) { saveReminder(reminder); selectPage(2); return }
        val movement = parseVoiceCommand(text, nextMovementId(), location)
        if (movement == null) showMessage("No pude identificar el monto. Prueba: registra 20 soles en comida.")
        else { saveMovement(movement); selectPage(1) }
    }

    private fun persist(data: FinancialData) {
        val email = value.email ?: return
        repository.saveProfile(email, data)
        mutableState.value = value.copy(data = data)
    }
}
