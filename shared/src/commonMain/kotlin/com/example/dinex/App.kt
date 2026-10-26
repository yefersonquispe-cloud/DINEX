package com.example.dinex

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** View layer: navigation, screens and presentation-only dialog state. */
@Composable
fun App(onThemeChanged: (Boolean) -> Unit = {}, onDataChanged: () -> Unit = {}) {
    val model = viewModel { DinexViewModel() }
    val state by model.state.collectAsState()
    SideEffect { onThemeChanged(state.darkMode) }
    LaunchedEffect(state.email, state.data, state.darkMode) { onDataChanged() }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * if (state.largeText) 1.2f else 1f),
        LocalDinexReducedMotion provides state.reduceMotion,
    ) {
        MaterialTheme(colorScheme = colors, typography = dinexTypography, shapes = dinexShapes) {
            var launchVisible by rememberSaveable { mutableStateOf(true) }
            if (launchVisible) {
                DinexLaunchScreen(reduceMotion = state.reduceMotion, onReduceMotion = model::setReduceMotion) { launchVisible = false }
            } else if (state.email == null) {
                AuthFlow(state.accounts, onCreateAccount = model::createAccount, onAuthenticated = model::authenticate)
            } else {
                key(state.email) { DinexSignedInView(model, state) }
            }
        }
    }
}

@Composable
private fun DinexSignedInView(model: DinexViewModel, state: DinexUiState) {
    val data = state.data
    val movements = data.movements
    val reminders = data.reminders
    val dailyLimit = data.dailyLimit
    val streak = data.streak
    val savings = data.savings
    val savedToday = data.streakDay == currentDayIndex()
    var quickAdd by remember { mutableStateOf(false) }
    var manualIncome by remember { mutableStateOf<Boolean?>(null) }
    var scanResult by remember { mutableStateOf<ScannedPurchase?>(null) }
    var reminderDialog by remember { mutableStateOf(false) }
    var movementToDelete by remember { mutableStateOf<Movement?>(null) }
    var reminderToResolve by remember { mutableStateOf<PaymentReminder?>(null) }
    var profileOpen by remember { mutableStateOf(false) }
    var assistantOpen by remember { mutableStateOf(false) }
    var welcomeGuideOpen by remember { mutableStateOf(false) }
    var savingDialog by remember { mutableStateOf(false) }
    var importDialog by remember { mutableStateOf(false) }
    var exportDialog by remember { mutableStateOf(false) }
    var premiumLoading by remember { mutableStateOf<String?>(null) }
    var automationDialog by remember { mutableStateOf(false) }
    var goalDetails by remember { mutableStateOf(false) }
    var selectedGoalId by remember { mutableStateOf<Int?>(null) }
    var savingGoalId by remember { mutableStateOf<Int?>(null) }
    var goalToDelete by remember { mutableStateOf<SavingsGoal?>(null) }
    var walletBubbleOpen by remember { mutableStateOf(false) }
    val illustrator = rememberGoalIllustrator()
    val goalScope = rememberCoroutineScope()
    var artworkBusy by remember { mutableStateOf(emptySet<Int>()) }

    val scanner = rememberReceiptScanner(
        onResult = { premiumLoading = null; scanResult = it; model.showMessage(null) },
        onError = { premiumLoading = null; model.showMessage(it) },
        onAnalyzing = { premiumLoading = "Analizando tu foto…" },
        onCancelled = { premiumLoading = null; model.showMessage(null) },
    )
    val assistant = rememberGeminiAssistant()
    val automation = rememberDeviceAutomation()
    val owner = LocalLifecycleOwner.current
    val permissionRevision = DinexNotificationPermission.revision
    val locationAvailable = remember(permissionRevision, automation) { automation.locationAvailable }
    val notificationsSupported = automation.systemNotificationsSupported
    val notificationsEnabled = remember(permissionRevision, automation) { automation.systemNotificationsEnabled }
    // A command can arrive while MainActivity is already alive. Consume on every resume.
    DisposableEffect(owner, automation, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                model.refreshProfile()
                DinexNotificationPermission.refresh()
                automation.consumePendingVoiceCommand()?.let { model.handleVoice(it, automation.currentLocationLabel()) }
            }
        }
        owner.lifecycle.addObserver(observer)
        automation.consumePendingVoiceCommand()?.let { model.handleVoice(it, automation.currentLocationLabel()) }
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val chatMessages = remember(assistant.isConfigured) {
        mutableStateListOf(ChatMessage(
            if (assistant.isConfigured) "Hola, soy Dinex IA. Te ayudo a entender tus gastos y planear tus ahorros."
            else "Hola, soy Dinex IA. Configura Gemini para conversar sobre tus finanzas.",
            user = false,
        ))
    }
    val name = state.activeAccount?.name ?: state.email.orEmpty().substringBefore('@')

    Box(Modifier.fillMaxSize()) {
        DinexAppShell(
            page = state.page,
            onSelectPage = model::selectPage,
            onAdd = { quickAdd = true },
            onProfile = { profileOpen = true },
            onAssistant = { assistantOpen = true },
            onWalletClick = { walletBubbleOpen = true },
            streak = streak,
            name = name,
            email = state.email.orEmpty(),
            profilePhoto = state.profilePhoto,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                AnimatedContent(
                    targetState = state.page,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        (fadeIn(tween(if (state.reduceMotion) 0 else 260)) + slideInVertically(tween(if (state.reduceMotion) 0 else 320)) { if (state.reduceMotion) 0 else it / 35 }) togetherWith
                            (fadeOut(tween(if (state.reduceMotion) 0 else 120)) + slideOutVertically(tween(if (state.reduceMotion) 0 else 180)) { if (state.reduceMotion) 0 else -it / 60 })
                    },
                    label = "page",
                ) { page ->
                    when (page) {
                        0 -> HomePage(movements, dailyLimit, streak, savings, "${data.savingGoals().size} metas", data.savingGoals().sumOf { it.target },
                            onDailyLimitChange = model::setDailyLimit,
                            onHistory = { model.selectPage(1) }, onAdd = { quickAdd = true }, onGoalDetails = { selectedGoalId = null; goalDetails = true },
                            goals = data.savingGoals(), onOpenGoal = { selectedGoalId = it; goalDetails = true },
                            notificationsSupported = notificationsSupported, notificationsEnabled = notificationsEnabled,
                            onEnableNotifications = { automation.requestSystemNotificationPermission() })
                        1 -> HistoryPage(movements, onAdd = { quickAdd = true }, onDelete = { movementToDelete = it },
                            onImport = { importDialog = true }, onExport = { exportDialog = true })
                        2 -> PlanPage(movements.filter { it.isInCurrentMonth() }, dailyLimit, reminders, onDailyLimitChange = model::setDailyLimit,
                            onAddReminder = { reminderDialog = true }, onDeleteReminder = { reminderToResolve = it }, categoryBudgets = data.categoryBudgets,
                            historyMovements = movements,
                            onCategoryBudgetsSave = { model.setCategoryBudgets(it) }, onCategoryBudgetsReset = model::resetCategoryBudgets)
                        3 -> InsightsPage(
                            movements,
                            onExport = { exportDialog = true },
                            locationAvailable = locationAvailable,
                            onRequestLocation = { automation.requestLocationPermission() },
                        )
                        else -> HomePage(movements, dailyLimit, streak, savings, "${data.savingGoals().size} metas", data.savingGoals().sumOf { it.target },
                            onDailyLimitChange = model::setDailyLimit,
                            onHistory = { model.selectPage(1) }, onAdd = { quickAdd = true }, onGoalDetails = { selectedGoalId = null; goalDetails = true },
                            goals = data.savingGoals(), onOpenGoal = { selectedGoalId = it; goalDetails = true },
                            notificationsSupported = notificationsSupported, notificationsEnabled = notificationsEnabled,
                            onEnableNotifications = { automation.requestSystemNotificationPermission() })
                    }
                }
                state.message?.let { InlineMessage(it, onDismiss = { model.showMessage(null) }) }
            }
        }

        if (walletBubbleOpen) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = .32f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { walletBubbleOpen = false },
                )
                Box(
                    Modifier.fillMaxSize().statusBarsPadding().padding(top = 64.dp, start = 14.dp, end = 14.dp),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    SavingsBubble(
                        streak = streak,
                        savedToday = savedToday,
                        onSaveToday = {
                            walletBubbleOpen = false
                            savingGoalId = null
                            savingDialog = true
                        },
                        onClose = { walletBubbleOpen = false },
                    )
                }
            }
        }
    }

    if (quickAdd) QuickAddDialog(
        onDismiss = { quickAdd = false },
        onExpense = { quickAdd = false; manualIncome = false },
        onIncome = { quickAdd = false; manualIncome = true },
        onSaving = { quickAdd = false; savingGoalId = null; savingDialog = true },
        onScan = { quickAdd = false; scanner() },
        onAutomation = { quickAdd = false; automationDialog = true },
    )
    if (automationDialog) AutomationDialog(
        automation, onDismiss = { automationDialog = false },
        onVoice = { model.handleVoice(it, automation.currentLocationLabel()); automationDialog = false },
        onImport = { model.importNotifications(it, automation.currentLocationLabel()); automation.acknowledgeNotifications(it); automationDialog = false },
    )
    manualIncome?.let { income ->
        MovementDialog(income, model.nextMovementId(), onDismiss = { manualIncome = null },
            onSave = { model.saveMovement(it); manualIncome = null },
            onScan = { manualIncome = null; scanner() })
    }
    scanResult?.let { result ->
        ScanReviewDialog(result, movementId = null, nextId = model.nextMovementId(),
            onDismiss = { scanResult = null; model.showMessage("Foto descartada") },
            onSave = { model.saveMovement(it); scanResult = null })
    }
    if (savingDialog) SavingDialog(available = data.availableBalance(), goals = data.savingGoals(), initialGoalId = savingGoalId,
        onDismiss = { savingDialog = false }, onSave = { entry -> if (model.saveSaving(entry)) {
            savingDialog = false; goalDetails = true
            val goal = model.state.value.data.savingGoals().first { it.id == model.state.value.lastSavedGoalId }
            selectedGoalId = goal.id
            val purpose = goal.name
            val email = state.email.orEmpty()
            if (goal.id !in artworkBusy && purpose.isNotBlank() && goal.artwork.isBlank() && illustrator.isConfigured) {
                artworkBusy = artworkBusy + goal.id
                goalScope.launch {
                    try { model.saveGoalArtwork(email, goal.id, purpose, illustrator.generate(purpose)) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Throwable) { model.showMessage("Tu ahorro está guardado. La imagen no se pudo generar; puedes reintentar desde tu meta.") }
                    finally { artworkBusy = artworkBusy - goal.id }
                }
            }
        } })
    if (goalDetails) {
        val goal = data.savingGoals().firstOrNull { it.id == selectedGoalId }
        if (goal == null) SavingsGoalsDialog(data.savingGoals(), onOpen = { selectedGoalId = it },
            onNew = { goalDetails = false; savingGoalId = null; savingDialog = true }, onDismiss = { goalDetails = false })
        else key(goal.id) { SavingsGoalDialog(goal, illustrator, onDismiss = { selectedGoalId = null },
            onAdd = { goalDetails = false; savingGoalId = goal.id; savingDialog = true },
            onArtwork = { model.saveGoalArtwork(state.email.orEmpty(), goal.id, goal.name, it) }, externalBusy = goal.id in artworkBusy,
            onDelete = { goalToDelete = goal }) }
    }
    goalToDelete?.let { goal -> AlertDialog(onDismissRequest = { goalToDelete = null }, title = { Text("¿Eliminar ${goal.name}?") },
        text = { Text("Se eliminarán esta meta y su imagen. ${money(goal.saved)} volverán al saldo disponible. Las demás metas y tu historial no cambiarán.") },
        confirmButton = { TextButton(onClick = { model.deleteSavingsGoal(goal.id); goalToDelete = null; selectedGoalId = null }) { Text("Eliminar meta", color = Red) } },
        dismissButton = { TextButton(onClick = { goalToDelete = null }) { Text("Conservar meta") } }) }
    state.pendingExpense?.let { pending ->
        AlertDialog(onDismissRequest = model::cancelPendingExpense, title = { Text("¿Deseas continuar?") }, text = {
            Text("Ya alcanzaste tu límite diario de ${money(dailyLimit.toDouble())}. Registrar ${pending.title} por ${money(pending.amount)} aumentará tu gasto de hoy. No se guardará hasta que confirmes.")
        }, confirmButton = { Button(onClick = model::confirmPendingExpense) { Text("Sí, registrar gasto") } }, dismissButton = { TextButton(onClick = model::cancelPendingExpense) { Text("Cancelar gasto") } })
    }
    state.budgetWarning?.let { warning ->
        AlertDialog(onDismissRequest = model::dismissBudgetWarning, title = { Text("Superaste tu presupuesto diario") }, text = { Text(warning) }, confirmButton = { TextButton(onClick = model::dismissBudgetWarning) { Text("Entendido") } })
    }
    if (state.goalCelebration) GoalCelebrationDialog(state.celebrationGoal?.name ?: "tu meta", state.celebrationGoal?.saved ?: savings, onDismiss = model::dismissCelebrations)
    state.levelCelebration?.let { StreakLevelDialog(it, streak, onDismiss = model::dismissCelebrations) }
    if (state.initialBalanceRequired) InitialBalanceDialog(onSave = { model.setInitialBalance(it); welcomeGuideOpen = true })
    if (welcomeGuideOpen) WelcomeGuideDialog(onDismiss = { welcomeGuideOpen = false }, onTryAssistant = { welcomeGuideOpen = false; assistantOpen = true })
    if (assistantOpen) Dialog(onDismissRequest = { assistantOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.fillMaxWidth().widthIn(max = 700.dp).heightIn(max = maxHeight - 24.dp).height(maxHeight.coerceAtMost(760.dp)),
                shape = RoundedCornerShape(28.dp), color = Card, shadowElevation = 20.dp) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Dinex IA · tu guía de dinero", color = Night, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { assistantOpen = false }) { Text("Cerrar") }
                    }
                    HorizontalDivider(color = Border)
                    Box(Modifier.weight(1f)) { AssistantPage(assistant, movements, dailyLimit, streak, savings, chatMessages, data.categoryBudgets) }
                }
            }
        }
    }
    if (importDialog) ImportMovementsDialog(movements, onDismiss = { importDialog = false }, onImport = { model.importMovements(it); importDialog = false })
    if (exportDialog) ExportReportDialog(movements, name, onDismiss = { exportDialog = false })
    if (reminderDialog) ReminderDialog(model.nextReminderId(), onDismiss = { reminderDialog = false }, onSave = { model.saveReminder(it); reminderDialog = false })
    movementToDelete?.let { movement ->
        AlertDialog(
            onDismissRequest = { movementToDelete = null },
            title = { Text("¿Eliminar movimiento?") },
            text = { Text(movement.title + " · " + money(movement.amount) + " se quitará del historial.") },
            confirmButton = { TextButton(onClick = { model.deleteMovement(movement); movementToDelete = null }) { Text("Eliminar", color = Red) } },
            dismissButton = { TextButton(onClick = { movementToDelete = null }) { Text("Cancelar") } },
        )
    }
    reminderToResolve?.let { reminder ->
        ReminderPaymentDialog(reminder, onDismiss = { reminderToResolve = null },
            onRemoveOnly = { model.resolveReminder(reminder, false); reminderToResolve = null },
            onRegisterExpense = { model.resolveReminder(reminder, true); reminderToResolve = null })
    }
    if (profileOpen) ProfileDialog(
        name, state.email.orEmpty(), streak, savings, movements,
        onDismiss = { profileOpen = false }, onLogout = { profileOpen = false; automation.setHotwordEnabled(false); model.logout() },
        reduceMotion = state.reduceMotion, onReduceMotion = model::setReduceMotion,
        largeText = state.largeText, onLargeText = model::setLargeText,
        darkMode = state.darkMode, onDarkMode = model::setDarkMode,
        budgetAlerts = state.budgetAlerts, onBudgetAlerts = model::setBudgetAlerts,
        saveReminderEnabled = state.saveReminderEnabled, onSaveReminderEnabled = model::setSaveReminderEnabled,
        saveReminderHour = state.saveReminderHour, onSaveReminderHour = model::setSaveReminderHour,
        notificationsSupported = notificationsSupported, notificationsEnabled = notificationsEnabled,
        onRequestNotifications = { automation.requestSystemNotificationPermission() },
        onOpenNotificationSettings = { automation.openAppDetailsSettings() },
        profilePhoto = state.profilePhoto, onProfilePhoto = model::setProfilePhoto,
        onAutomation = { profileOpen = false; automationDialog = true },
    )
    premiumLoading?.let { PremiumLoadingDialog(it) }
}
