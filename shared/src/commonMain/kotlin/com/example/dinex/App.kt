package com.example.dinex

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dinex.shared.generated.resources.Res
import dinex.shared.generated.resources.dinex_card_balance_bg
import dinex.shared.generated.resources.dinex_card_savings_bg
import dinex.shared.generated.resources.dinex_app_icon
import dinex.shared.generated.resources.dinex_mascot_ai
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max

// Dinex palette: deep navy + emerald + electric blue + warm gold accents.
// It keeps text contrast high while making the dashboard feel more alive.
internal val Night = Color(0xFFF4FBFF)
internal val NightSoft = Color(0xFF294458)
internal val Green = Color(0xFF33D6A5)
internal val GreenDark = Color(0xFF8CF2D4)
internal val Mint = Color(0xFF123A43)
internal val Lime = Color(0xFF7CF2C6)
internal val AppBg = Color(0xFF07151F)
internal val Card = Color(0xFF102431)
internal val CardRaised = Color(0xFF183344)
internal val Red = Color(0xFFFF7180)
internal val Orange = Color(0xFFFFC46B)
internal val Indigo = Color(0xFF7866FF)
internal val ElectricBlue = Color(0xFF36BFF2)
internal val Gray = Color(0xFF9CB4C2)
internal val Border = Color(0xFF29485A)

internal data class Movement(
    val id: Int,
    val title: String,
    val category: String,
    val amount: Double,
    val income: Boolean,
    val day: Int,
    val method: String,
    val location: String = "",
)

internal data class PaymentReminder(
    val id: Int,
    val title: String,
    val date: String,
    val amount: Double,
)

internal data class CategoryInfo(val name: String, val code: String, val color: Color, val limit: Double)

internal data class ChatMessage(val text: String, val user: Boolean, val error: Boolean = false)

internal data class SavingEntry(val amount: Double, val purpose: String, val target: Double)

internal data class UserAccount(val name: String, val email: String, val passwordDigest: String)

internal data class StreakLevel(val name: String, val startsAt: Int, val color: Color)

internal val streakLevels = listOf(
    StreakLevel("Cobre", 1, Color(0xFFC97843)),
    StreakLevel("Plata", 8, Color(0xFFB9C5D2)),
    StreakLevel("Oro", 15, Color(0xFFFFC83D)),
    StreakLevel("Platino", 30, Color(0xFF78D9FF)),
    StreakLevel("Diamante", 45, Color(0xFF73B8FF)),
    StreakLevel("Esmeralda", 60, Color(0xFF38D98A)),
    StreakLevel("Rubí", 90, Color(0xFFFF405F)),
    StreakLevel("Amatista", 120, Color(0xFFB75CFF)),
    StreakLevel("Obsidiana", 150, Color(0xFFFF6B36)),
    StreakLevel("Perla", 180, Color(0xFFF2E9E4)),
    StreakLevel("Madera", 210, Color(0xFFC68A52)),
    StreakLevel("Piedra", 240, Color(0xFF949EAA)),
    StreakLevel("Acero", 270, Color(0xFFCBD5E1)),
    StreakLevel("Cristal", 330, Color(0xFF2DE2FF)),
    StreakLevel("Leyenda", 365, Color(0xFFFFD34E)),
)

internal fun streakLevel(days: Int): StreakLevel = streakLevels.lastOrNull { days >= it.startsAt } ?: streakLevels.first()

internal fun nextStreakLevel(days: Int): StreakLevel? = streakLevels.firstOrNull { it.startsAt > days }

internal val categories = listOf(
    CategoryInfo("Comida", "CO", Red, 450.0),
    CategoryInfo("Transporte", "TR", Indigo, 220.0),
    CategoryInfo("Estudios", "ES", Color(0xFF8E65C5), 180.0),
    CategoryInfo("Entretenimiento", "OC", Orange, 160.0),
    CategoryInfo("Otros", "OT", Color(0xFF71827A), 200.0),
)

internal fun seedMovements() = listOf(
    Movement(id = 1, title = "Propina mensual", category = "Ingreso", amount = 850.0, income = true, day = 1, method = "Yape"),
    Movement(2, "Trabajo freelance", "Ingreso", 420.0, true, 4, "Transferencia"),
    Movement(3, "Almuerzo en Tecsup", "Comida", 14.5, false, 12, "Yape"),
    Movement(4, "Pasajes de la semana", "Transporte", 36.0, false, 11, "Efectivo"),
    Movement(5, "Materiales del curso", "Estudios", 58.9, false, 10, "Tarjeta"),
    Movement(6, "Salida con amigos", "Entretenimiento", 72.0, false, 8, "Yape"),
    Movement(7, "Desayuno", "Comida", 9.5, false, 7, "Efectivo"),
    Movement(8, "Videojuego en oferta", "Entretenimiento", 29.9, false, 5, "Tarjeta"),
    Movement(9, "Venta de audífonos", "Ingreso", 110.0, true, 3, "Plin"),
)

internal fun seedReminders() = listOf(
    PaymentReminder(1, "Internet móvil", "HOY", 39.9),
    PaymentReminder(2, "Spotify", "18 SEP", 20.9),
    PaymentReminder(3, "Pensión", "25 SEP", 420.0),
)

internal val colors = darkColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = Night,
    secondary = Indigo,
    background = AppBg,
    surface = Card,
    onSurface = Night,
    surfaceVariant = CardRaised,
    onSurfaceVariant = Gray,
    outline = Border,
    error = Red,
)

// ═══════════════════════════════════════════════════════════════════════════
// APP STATE & NAVIGATION
// Main entry point, session state, pages and global dialogs.
// ═══════════════════════════════════════════════════════════════════════════
@Composable
@Preview
fun App() {
    MaterialTheme(colorScheme = colors) {
        val accounts = remember {
            mutableStateListOf<UserAccount>().apply { addAll(decodeAccounts(loadSavedAccounts())) }
        }
        var authenticatedEmail by remember { mutableStateOf(loadSavedEmail()) }
        LaunchedEffect(accounts.toList()) { saveSavedAccounts(encodeAccounts(accounts)) }
        if (authenticatedEmail == null) {
            AuthFlow(
                accounts = accounts,
                onCreateAccount = { account ->
                    accounts.removeAll { it.email == account.email }
                    accounts.add(account)
                    saveSavedAccounts(encodeAccounts(accounts))
                    saveSavedMovements("")
                    saveSavedReminders("")
                    saveSavedStreak(0)
                    saveSavedSavings(0.0)
                    saveSavedGoalName("")
                    saveSavedGoalTarget(0.0)
                    saveSavedOnboardingCompleted(false)
                    saveSavedEmail(account.email)
                    authenticatedEmail = account.email
                },
                onAuthenticated = { email ->
                    saveSavedEmail(email)
                    authenticatedEmail = email
                },
            )
            return@MaterialTheme
        }
        val activeAccount = accounts.firstOrNull { it.email == authenticatedEmail }
        val movements = remember(authenticatedEmail) {
            mutableStateListOf<Movement>().apply { addAll(decodeMovements(loadSavedMovements())) }
        }
        val reminders = remember(authenticatedEmail) {
            mutableStateListOf<PaymentReminder>().apply { addAll(decodeReminders(loadSavedReminders())) }
        }
        var initialBalanceOpen by remember(authenticatedEmail) { mutableStateOf(!loadSavedOnboardingCompleted() && loadSavedMovements().isNullOrBlank()) }
        var page by remember { mutableIntStateOf(0) }
        var quickAdd by remember { mutableStateOf(false) }
        var manualIncome by remember { mutableStateOf<Boolean?>(null) }
        var scanResult by remember { mutableStateOf<ScannedPurchase?>(null) }
        var scannedMovementId by remember { mutableStateOf<Int?>(null) }
        var reminderDialog by remember { mutableStateOf(false) }
        var movementToDelete by remember { mutableStateOf<Movement?>(null) }
        var reminderToResolve by remember { mutableStateOf<PaymentReminder?>(null) }
        var profileOpen by remember { mutableStateOf(false) }
        var savingDialog by remember { mutableStateOf(false) }
        var savingGoalName by remember(authenticatedEmail) { mutableStateOf("") }
        var savingGoalTarget by remember(authenticatedEmail) { mutableDoubleStateOf(loadSavedGoalTarget() ?: 0.0) }
        LaunchedEffect(authenticatedEmail) { savingGoalName = loadSavedGoalName().orEmpty() }
        var goalCelebration by remember { mutableStateOf(false) }
        var importDialog by remember { mutableStateOf(false) }
        var exportDialog by remember { mutableStateOf(false) }
        var levelCelebration by remember { mutableStateOf<StreakLevel?>(null) }
        var premiumLoading by remember { mutableStateOf<String?>(null) }
        var automationDialog by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }
        var dailyLimit by remember { mutableFloatStateOf(45f) }
        var streak by remember(authenticatedEmail) { mutableIntStateOf(loadSavedStreak() ?: 0) }
        var savings by remember(authenticatedEmail) { mutableDoubleStateOf(loadSavedSavings() ?: 0.0) }
        var savedStreakDay by remember(authenticatedEmail) { mutableStateOf(loadSavedStreakDay()) }
        val todayIndex = currentDayIndex()
        val savedToday = savedStreakDay == todayIndex

        LaunchedEffect(authenticatedEmail, movements.toList()) { saveSavedMovements(encodeMovements(movements)) }
        LaunchedEffect(authenticatedEmail, streak) { saveSavedStreak(streak) }
        LaunchedEffect(authenticatedEmail, savings) { saveSavedSavings(savings) }
        LaunchedEffect(authenticatedEmail, reminders.toList()) { saveSavedReminders(encodeReminders(reminders)) }

        val scanner = rememberReceiptScanner(
            onResult = { result ->
                premiumLoading = null
                scannedMovementId = null
                message = null
                scanResult = result
            },
            onError = { premiumLoading = null; message = it }
        )
        val assistant = rememberGeminiAssistant()
        val automation = rememberDeviceAutomation()
        LaunchedEffect(automation) {
            automation.consumePendingVoiceCommand()?.let { text ->
                val reminder = parseVoiceReminder(text, (reminders.maxOfOrNull { it.id } ?: 0) + 1)
                if (reminder != null) {
                    reminders.add(0, reminder)
                    page = 2
                    message = "Recordatorio agregado por voz: ${reminder.title}"
                } else {
                    val movement = parseVoiceCommand(
                        text,
                        (movements.maxOfOrNull { it.id } ?: 0) + 1,
                        automation.currentLocationLabel(),
                    )
                    if (movement == null) {
                        message = "No pude identificar el monto. Prueba: registra 20 soles en comida."
                    } else {
                        movements.add(0, movement)
                        page = 1
                        message = "Comando externo guardado como ${money(movement.amount)}"
                    }
                }
            }
        }
        val chatMessages = remember(assistant.isConfigured) {
            mutableStateListOf(
                ChatMessage(
                    if (assistant.isConfigured)
                        "¡Hola! Soy Dinex IA. Ya revisé tu resumen financiero. Pregúntame cómo ahorrar, dónde estás gastando más o si una compra encaja en tu presupuesto."
                    else
                        "¡Hola! Soy Dinex IA. El chat ya está listo; solo falta configurar la clave de Gemini en local.properties para responder con tus datos financieros.",
                    user = false,
                )
            )
        }

        Scaffold(
            containerColor = AppBg,
            topBar = {
                DinexTopBar(
                    streak = streak,
                    name = activeAccount?.name ?: authenticatedEmail.orEmpty().substringBefore('@').replaceFirstChar { it.uppercase() },
                    email = authenticatedEmail.orEmpty(),
                    onProfile = { profileOpen = true },
                )
            },
            bottomBar = { BottomNavigation(page, onSelect = { page = it }, onAdd = { quickAdd = true }) },
            floatingActionButton = {
                if (page != 4) DinexAssistantBubble(onClick = { page = 4 })
            },
            floatingActionButtonPosition = FabPosition.End,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                when (page) {
                    0 -> HomePage(
                        movements, dailyLimit, streak, savedToday, savings, savingGoalName, savingGoalTarget,
                        onDailyLimitChange = { dailyLimit = it },
                        onSaveToday = { savingDialog = true },
                        onHistory = { page = 1 },
                    )
                    1 -> HistoryPage(
                        movements,
                        onAdd = { quickAdd = true },
                        onDelete = { movementToDelete = it },
                        onImport = { importDialog = true },
                        onExport = { exportDialog = true },
                    )
                    2 -> PlanPage(
                        movements,
                        dailyLimit,
                        reminders,
                        onDailyLimitChange = { dailyLimit = it },
                        onAddReminder = { reminderDialog = true },
                        onDeleteReminder = { reminderToResolve = it },
                    )
                    3 -> InsightsPage(movements)
                    else -> AssistantPage(assistant, movements, dailyLimit, streak, savings, chatMessages)
                }
                message?.let { InlineMessage(it, onDismiss = { message = null }) }
            }
        }

        if (quickAdd) {
            QuickAddDialog(
                onDismiss = { quickAdd = false },
                onExpense = { quickAdd = false; manualIncome = false },
                onIncome = { quickAdd = false; manualIncome = true },
                onSaving = { quickAdd = false; savingDialog = true },
                onScan = { quickAdd = false; premiumLoading = "Preparando tu análisis inteligente…"; scanner() },
                onAutomation = { quickAdd = false; automationDialog = true },
            )
        }
        if (automationDialog) {
            AutomationDialog(
                automation = automation,
                onDismiss = { automationDialog = false },
                onVoice = { text ->
                    val reminder = parseVoiceReminder(text, (reminders.maxOfOrNull { it.id } ?: 0) + 1)
                    if (reminder != null) {
                        reminders.add(0, reminder)
                        page = 2
                        message = "Recordatorio agregado por voz: ${reminder.title}"
                    } else {
                        val movement = parseVoiceCommand(text, (movements.maxOfOrNull { it.id } ?: 0) + 1, automation.currentLocationLabel())
                        if (movement == null) message = "No pude identificar un monto. Di: Dinex registra 20 soles en comida."
                        else { movements.add(0, movement); page = 1; message = "Comando de voz guardado como ${money(movement.amount)}" }
                    }
                    automationDialog = false
                },
                onImport = { captured ->
                    val location = automation.currentLocationLabel().orEmpty()
                    captured.forEachIndexed { index, item ->
                        movements.add(0, Movement((movements.maxOfOrNull { it.id } ?: 0) + index + 1, item.title, if (item.income) "Ingreso" else "Otros", item.amount, item.income, currentDayOfMonth(), item.source, location))
                    }
                    automationDialog = false
                    message = "${captured.size} notificaciones revisadas y registradas"
                },
            )
        }
        manualIncome?.let { income ->
            MovementDialog(
                income = income,
                nextId = (movements.maxOfOrNull { it.id } ?: 0) + 1,
                onDismiss = { manualIncome = null },
                onSave = { movements.add(0, it); manualIncome = null; message = if (it.income) "Ingreso agregado" else "Gasto agregado" },
            )
        }
        scanResult?.let { result ->
            ScanReviewDialog(
                result = result,
                movementId = scannedMovementId,
                nextId = (movements.maxOfOrNull { it.id } ?: 0) + 1,
                onDismiss = {
                    scanResult = null
                    scannedMovementId = null
                    message = "Foto descartada. No se registró ningún gasto"
                },
                onSave = { updated ->
                    val index = movements.indexOfFirst { it.id == updated.id }
                    if (index >= 0) movements[index] = updated else movements.add(0, updated)
                    scanResult = null
                    scannedMovementId = null
                    message = "Gasto de la foto guardado"
                },
            )
        }
        if (savingDialog) {
            SavingDialog(
                onDismiss = { savingDialog = false },
                onSave = { entry ->
                    val previousLevel = streakLevel(streak)
                    if (entry.purpose.isNotBlank()) savingGoalName = entry.purpose
                    if (entry.purpose.isNotBlank()) saveSavedGoalName(entry.purpose)
                    if (entry.target > 0) { savingGoalTarget = entry.target; saveSavedGoalTarget(entry.target) }
                    savings += entry.amount
                    if (!savedToday) {
                        streak += 1
                        savedStreakDay = todayIndex
                        saveSavedStreakDay(todayIndex)
                        val unlocked = streakLevel(streak)
                        if (unlocked.name != previousLevel.name) levelCelebration = unlocked
                    }
                    savingDialog = false
                    if (savingGoalTarget > 0 && savings >= savingGoalTarget && savings - entry.amount < savingGoalTarget) {
                        goalCelebration = true
                    }
                    message = "Ahorro de ${money(entry.amount)} agregado · Racha de $streak días"
                },
            )
        }
        if (goalCelebration) {
            GoalCelebrationDialog(
                goalName = savingGoalName.ifBlank { "tu meta" },
                amount = savings,
                onDismiss = { goalCelebration = false },
            )
        }
        if (initialBalanceOpen) {
            InitialBalanceDialog(
                onSave = { amount ->
                    if (amount > 0) movements.add(Movement((movements.maxOfOrNull { it.id } ?: 0) + 1, "Saldo inicial", "Ingreso", amount, true, 1, "Saldo inicial"))
                    saveSavedOnboardingCompleted(true)
                    initialBalanceOpen = false
                    message = if (amount > 0) "Saldo inicial guardado" else "Empezamos con saldo cero"
                },
            )
        }
        if (importDialog) {
            ImportMovementsDialog(
                onDismiss = { importDialog = false },
                onImport = { imported -> movements.addAll(0, imported.mapIndexed { index, movement -> movement.copy(id = (movements.maxOfOrNull { it.id } ?: 0) + index + 1) }); importDialog = false; message = "${imported.size} movimientos importados" },
            )
        }
        if (exportDialog) {
            ExportReportDialog(movements, onDismiss = { exportDialog = false })
        }
        if (reminderDialog) {
            ReminderDialog(
                nextId = (reminders.maxOfOrNull { it.id } ?: 0) + 1,
                onDismiss = { reminderDialog = false },
                onSave = { reminders.add(it); reminderDialog = false; message = "Recordatorio agregado" },
            )
        }
        movementToDelete?.let { movement ->
            AlertDialog(
                onDismissRequest = { movementToDelete = null },
                shape = RoundedCornerShape(26.dp),
                icon = {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(Red.copy(alpha = .13f)),
                        contentAlignment = Alignment.Center,
                    ) { Text("×", color = Red, fontSize = 25.sp, fontWeight = FontWeight.Black) }
                },
                title = { Text("¿Eliminar movimiento?", fontWeight = FontWeight.Black) },
                text = {
                    Text(
                        "${movement.title} · ${money(movement.amount)} se quitará del historial y de todos los cálculos.",
                        color = Gray,
                        textAlign = TextAlign.Center,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            movements.removeAll { it.id == movement.id }
                            movementToDelete = null
                            message = if (movement.income) "Ingreso eliminado" else "Gasto eliminado"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Red),
                    ) { Text("Eliminar", color = Color.White, fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { movementToDelete = null }) { Text("Cancelar") } },
            )
        }
        reminderToResolve?.let { reminder ->
            ReminderPaymentDialog(
                reminder = reminder,
                onDismiss = { reminderToResolve = null },
                onRemoveOnly = {
                    reminders.removeAll { it.id == reminder.id }
                    reminderToResolve = null
                    message = "Recordatorio eliminado sin registrar gasto"
                },
                onRegisterExpense = {
                    reminders.removeAll { it.id == reminder.id }
                    movements.add(
                        0,
                        Movement(
                            id = (movements.maxOfOrNull { it.id } ?: 0) + 1,
                            title = reminder.title,
                            category = categoryForReminder(reminder.title),
                            amount = reminder.amount,
                            income = false,
                            day = currentDayOfMonth(),
                            method = "Recordatorio pagado",
                        ),
                    )
                    reminderToResolve = null
                    message = "Pago registrado automáticamente como egreso"
                },
            )
        }
        if (profileOpen) {
            ProfileDialog(
                name = activeAccount?.name ?: authenticatedEmail.orEmpty().substringBefore('@').replaceFirstChar { it.uppercase() },
                email = authenticatedEmail.orEmpty(),
                streak = streak,
                savings = savings,
                movements = movements,
                onDismiss = { profileOpen = false },
                onLogout = {
                    profileOpen = false
                    saveSavedEmail(null)
                    authenticatedEmail = null
                },
            )
        }
        levelCelebration?.let { level ->
            StreakLevelDialog(level = level, streak = streak, onDismiss = { levelCelebration = null })
        }
        premiumLoading?.let { PremiumLoadingDialog(it) }
    }
}
