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

/** View layer: navigation, screens and presentation-only dialog state. */
@Composable
fun App(onThemeChanged: (Boolean) -> Unit = {}, onDataChanged: () -> Unit = {}, webLayout: Boolean = false) {
    val model = viewModel { DinexViewModel(cloud = DinexCloud()) }
    val googleIdentity = rememberGoogleIdentity()
    val state by model.state.collectAsState()
    SideEffect { onThemeChanged(state.darkMode) }
    LaunchedEffect(state.email, state.data, state.darkMode) { onDataChanged() }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * if (state.largeText) 1.2f else 1f),
        LocalDinexReducedMotion provides state.reduceMotion,
        LocalWebLayout provides webLayout,
    ) {
        MaterialTheme(colorScheme = colors, typography = if (webLayout) webTypography() else dinexTypography, shapes = dinexShapes) {
            var launchVisible by rememberSaveable { mutableStateOf(true) }
            if (launchVisible) {
                DinexLaunchScreen(reduceMotion = state.reduceMotion, onReduceMotion = model::setReduceMotion) { launchVisible = false }
            } else if (state.email == null) {
                AuthFlow(
                    accounts = state.accounts,
                    busy = state.authBusy,
                    serverError = state.authError,
                    notice = state.authNotice,
                    onRegister = model::register,
                    onSignIn = model::signIn,
                    onForgotPassword = model::sendPasswordReset,
                    googleAvailable = googleIdentity.available,
                    onGoogleSignIn = { model.signInWithGoogle(googleIdentity) },
                    onClearMessages = model::clearAuthMessages,
                )
            } else {
                key(state.email) { DinexSignedInView(model, state) }
            }
        }
    }
}

@Composable
internal fun DinexSignedInView(model: DinexViewModel, state: DinexUiState) {
    val data = state.data
    val movements = data.movements
    val reminders = data.reminders
    val dailyLimit = data.dailyLimit
    val streak = data.activeStreak()
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
    var marketOpen by remember { mutableStateOf(false) }
    var tipsOpen by remember { mutableStateOf(false) }
    var welcomeGuideOpen by remember { mutableStateOf(false) }
    var legalTab by remember { mutableStateOf<Int?>(null) }
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
    val chatMessages = remember {
        mutableStateListOf(ChatMessage(
            "Hola, soy Dinex IA. Te ayudo a entender tus gastos y planear tus ahorros.",
            user = false,
        ))
    }
    val name = state.activeAccount?.name ?: state.email.orEmpty().substringBefore('@')

    BoxWithConstraints(Modifier.fillMaxSize()) {
      // En la web, con pantalla ancha, se usa el diseño de escritorio (WebWorkspace.kt).
      if (LocalWebLayout.current && maxWidth >= WebDesktopMinWidth) {
        WebWorkspace(
            model = model,
            state = state,
            actions = WebActions(
                onExpense = { manualIncome = false },
                onIncome = { manualIncome = true },
                onScan = { scanner() },
                onImport = { importDialog = true },
                onExport = { exportDialog = true },
                onDeleteMovement = { movementToDelete = it },
                onSave = { goalId -> savingGoalId = goalId; savingDialog = true },
                onOpenGoal = { goalId -> selectedGoalId = goalId; goalDetails = true },
                onAddReminder = { reminderDialog = true },
                onResolveReminder = { reminderToResolve = it },
                onProfile = { profileOpen = true },
                onMarket = { marketOpen = true },
                onTips = { tipsOpen = true },
            ),
            assistant = {
                WebAssistantPage(assistant, data, chatMessages,
                    queriesLeft = model::aiQueriesLeft, syncQueriesLeft = model::syncedAiQueriesLeft, onQueryUsed = model::recordAiQuery)
            },
        )
      } else {
        DinexAppShell(
            page = state.page,
            onSelectPage = model::selectPage,
            onAdd = { quickAdd = true },
            onProfile = { profileOpen = true },
            onAssistant = { assistantOpen = true },
            onWalletClick = { walletBubbleOpen = true },
            onMarket = { marketOpen = true },
            onTips = { tipsOpen = true },
            darkMode = state.darkMode,
            onToggleTheme = { model.setDarkMode(!state.darkMode) },
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
                            onScan = { scanner() }, onVoice = { automationDialog = true },
                            onGoals = { savingGoalId = null; savingDialog = true },
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
    }

    if (quickAdd) QuickAddDialog(
        onDismiss = { quickAdd = false },
        onExpense = { quickAdd = false; manualIncome = false },
        onIncome = { quickAdd = false; manualIncome = true },
    )
    if (automationDialog) AutomationDialog(
        automation, onDismiss = { automationDialog = false },
        onVoice = { model.handleVoice(it, automation.currentLocationLabel()); automationDialog = false },
        onImport = { model.importNotifications(it, automation.currentLocationLabel()); automation.acknowledgeNotifications(it); automationDialog = false },
    )
    manualIncome?.let { income ->
        MovementEntryDialog(
            isIncome = income,
            nextId = model.nextMovementId(),
            onDismiss = { manualIncome = null },
            onSave = { model.saveMovement(it); manualIncome = null },
            onScan = { manualIncome = null; scanner() }
        )
    }
    scanResult?.let { result ->
        ScanReviewDialog(result, movementId = null, nextId = model.nextMovementId(),
            onDismiss = { scanResult = null; model.showMessage("Foto descartada") },
            onSave = { model.saveMovement(it); scanResult = null })
    }
    if (savingDialog) SavingDialog(available = data.availableBalance(), goals = data.savingGoals(), initialGoalId = savingGoalId,
        onDismiss = { savingDialog = false }, onSave = { entry -> if (model.saveSaving(entry)) {
            savingDialog = false; goalDetails = true
            selectedGoalId = model.state.value.lastSavedGoalId
        } })
    if (goalDetails) {
        val goal = data.savingGoals().firstOrNull { it.id == selectedGoalId }
        if (goal == null) SavingsGoalsDialog(data.savingGoals(), onOpen = { selectedGoalId = it },
            onNew = { goalDetails = false; savingGoalId = null; savingDialog = true }, onDismiss = { goalDetails = false })
        else key(goal.id) { SavingsGoalDialog(goal, onDismiss = { selectedGoalId = null },
            onAdd = { goalDetails = false; savingGoalId = goal.id; savingDialog = true },
            onDelete = { goalToDelete = goal }) }
    }
    goalToDelete?.let { goal -> AlertDialog(onDismissRequest = { goalToDelete = null }, title = { Text("¿Eliminar ${goal.name}?") },
        text = { Text("Se eliminará esta meta. ${money(goal.saved)} volverán al saldo disponible. Las demás metas y tu historial no cambiarán.") },
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
    if (welcomeGuideOpen) WelcomeGuideDialog(
        name = state.activeAccount?.name.orEmpty(),
        onDismiss = { welcomeGuideOpen = false },
        onAction = { action ->
            welcomeGuideOpen = false
            when (action) {
                GuideAction.Automation -> automationDialog = true
                GuideAction.Market -> marketOpen = true
                GuideAction.Tips -> tipsOpen = true
                GuideAction.Assistant -> assistantOpen = true
            }
        },
    )
    legalTab?.let { LegalDialog(it) { legalTab = null } }
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
                    Box(Modifier.weight(1f)) { AssistantPage(assistant, movements, dailyLimit, streak, savings, chatMessages, data.categoryBudgets,
                        queriesLeft = model::aiQueriesLeft, syncQueriesLeft = model::syncedAiQueriesLeft, onQueryUsed = model::recordAiQuery) }
                }
            }
        }
    }
    if (marketOpen) Dialog(
        onDismissRequest = { marketOpen = false },
        properties = fullScreenDialogProperties(),
    ) {
        MarketScreen(onClose = { marketOpen = false })
    }
    if (tipsOpen) Dialog(
        onDismissRequest = { tipsOpen = false },
        properties = fullScreenDialogProperties(),
    ) {
        TipsScreen(data, onClose = { tipsOpen = false }, onAction = { action ->
            tipsOpen = false
            when (action) {
                TipAction.AddMovement -> quickAdd = true
                TipAction.Save -> { savingGoalId = null; savingDialog = true }
                TipAction.Plan -> model.selectPage(2)
                TipAction.Analysis -> model.selectPage(3)
                TipAction.Assistant -> assistantOpen = true
            }
        })
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
        onOpenGuide = { profileOpen = false; welcomeGuideOpen = true },
        onOpenLegal = { legalTab = it },
        onDeleteAccount = { done -> model.deleteAccount { error -> if (error == null) automation.setHotwordEnabled(false); done(error) } },
    )
    premiumLoading?.let { PremiumLoadingDialog(it) }
}
