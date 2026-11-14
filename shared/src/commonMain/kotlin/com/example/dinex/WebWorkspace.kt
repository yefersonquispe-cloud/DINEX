@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.dinex

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dinex.shared.generated.resources.Res
import dinex.shared.generated.resources.dinex_app_icon
import dinex.shared.generated.resources.manrope
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

// ═══════════════════════════════════════════════════════════════════════════
// DINEX WEB
// Diseño propio para el navegador (pantallas de 760 px o más). Usa las mismas
// funciones y datos que la app (DinexViewModel), pero con una distribución de
// escritorio: barra lateral, encabezado y páginas en cuadrícula con ancho máximo.
// En pantallas angostas la web sigue usando el diseño del celular.
// ═══════════════════════════════════════════════════════════════════════════

/** true cuando la app corre en el navegador (lo indica webApp/main.kt). */
internal val LocalWebLayout = staticCompositionLocalOf { false }

/** Ancho mínimo para usar el diseño de escritorio en la web. */
internal val WebDesktopMinWidth = 760.dp

internal enum class WebPage(val title: String, val subtitle: String) {
    SUMMARY("Resumen", "Tu dinero de un vistazo"),
    MOVEMENTS("Movimientos", "Todo lo que entra y sale de tu cuenta"),
    BUDGET("Presupuesto", "Límites diarios, categorías y próximos pagos"),
    GOALS("Metas", "Ahorro con propósito"),
    ANALYSIS("Análisis", "Patrones y tendencias de tus gastos"),
    ASSISTANT("Dinex IA", "Pregunta lo que quieras sobre tus finanzas"),
}

private fun WebPage.icon(): ImageVector = when (this) {
    WebPage.SUMMARY -> DinexNavigationIcons.Home
    WebPage.MOVEMENTS -> DinexNavigationIcons.Movements
    WebPage.BUDGET -> DinexNavigationIcons.Plan
    WebPage.GOALS -> DinexNavigationIcons.Target
    WebPage.ANALYSIS -> DinexNavigationIcons.Analysis
    WebPage.ASSISTANT -> DinexNavigationIcons.Sparkle
}

/** Acciones que abren los mismos diálogos de la app (registrar, ahorrar, importar…). */
internal class WebActions(
    val onExpense: () -> Unit,
    val onIncome: () -> Unit,
    val onScan: () -> Unit,
    val onImport: () -> Unit,
    val onExport: () -> Unit,
    val onDeleteMovement: (Movement) -> Unit,
    val onSave: (goalId: Int?) -> Unit,
    val onOpenGoal: (goalId: Int?) -> Unit,
    val onAddReminder: () -> Unit,
    val onResolveReminder: (PaymentReminder) -> Unit,
    val onProfile: () -> Unit,
    val onMarket: () -> Unit,
    val onTips: () -> Unit,
)

// ---------------------------------------------------------------- Paleta y tipografía

private object W {
    private val dark get() = DinexThemeState.dark
    val canvas get() = if (dark) Color(0xFF0B130F) else Color(0xFFF2F4F0)
    val surface get() = if (dark) Color(0xFF121C17) else Color(0xFFFFFFFF)
    val surfaceAlt get() = if (dark) Color(0xFF17231D) else Color(0xFFF7F8F5)
    val hover get() = if (dark) Color(0xFF1B2A22) else Color(0xFFF1F5F1)
    val line get() = if (dark) Color(0xFF23332A) else Color(0xFFE2E7E1)
    val ink get() = if (dark) Color(0xFFEAF2EC) else Color(0xFF101B15)
    val ink2 get() = if (dark) Color(0xFFA6B8AC) else Color(0xFF526258)
    val ink3 get() = if (dark) Color(0xFF6F8277) else Color(0xFF8A978E)
    val brand get() = if (dark) Color(0xFF5ED49B) else Color(0xFF0D6A43)
    val brandSoft get() = if (dark) Color(0xFF173627) else Color(0xFFE1F0E6)
    val positive get() = if (dark) Color(0xFF5ED49B) else Color(0xFF12804A)
    val negative get() = if (dark) Color(0xFFF08B7B) else Color(0xFFC0392B)
    val warning get() = if (dark) Color(0xFFF2C46B) else Color(0xFFB7791F)
    val lime = Color(0xFFC6F06A)
    val forest = Color(0xFF0E1E16)
    val forest2 = Color(0xFF143826)
    val sidebarInk = Color(0xFFDCE7DF)
    val sidebarMuted = Color(0xFF7D9586)
    val sidebarActive = Color(0xFF1A2E23)
}

private val CardShape = RoundedCornerShape(16.dp)

/** Manrope (Google Fonts, licencia OFL): una sola fuente variable para todos los grosores. */
@Composable
internal fun manropeFamily(): FontFamily {
    val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)
    return FontFamily(weights.map { Font(Res.font.manrope, it, FontStyle.Normal, FontVariation.Settings(FontVariation.weight(it.weight))) })
}

/** Tipografía de Material con Manrope, para que los diálogos de la web usen la misma fuente. */
@Composable
internal fun webTypography(): Typography {
    val family = manropeFamily()
    val t = dinexTypography
    fun TextStyle.m() = copy(fontFamily = family)
    return Typography(
        displayLarge = t.displayLarge.m(), displayMedium = t.displayMedium.m(), displaySmall = t.displaySmall.m(),
        headlineLarge = t.headlineLarge.m(), headlineMedium = t.headlineMedium.m(), headlineSmall = t.headlineSmall.m(),
        titleLarge = t.titleLarge.m(), titleMedium = t.titleMedium.m(), titleSmall = t.titleSmall.m(),
        bodyLarge = t.bodyLarge.m(), bodyMedium = t.bodyMedium.m(), bodySmall = t.bodySmall.m(),
        labelLarge = t.labelLarge.m(), labelMedium = t.labelMedium.m(), labelSmall = t.labelSmall.m(),
    )
}

// ---------------------------------------------------------------- Datos derivados

private fun Movement.epochDay() = epochDaysOf(year, month, day)
private fun Movement.monthKey() = year * 12 + month - 1
private fun monthKeyLabel(key: Int) = "${currentMonthName(key % 12 + 1)} ${key / 12}"
private fun monthKeyShort(key: Int) = "${reportMonthShort(key % 12 + 1).replaceFirstChar { it.uppercase() }} ${(key / 12) % 100}"

private fun daysInMonth(year: Int, month: Int): Int = (28..31).last { validDate(year, month, it) }

private fun categoryColor(name: String, index: Int = 0): Color =
    categories.firstOrNull { it.name.equals(name, true) }?.color
        ?: listOf(Color(0xFF3E8EB8), Color(0xFFC2577F), Color(0xFF4F9D69), Color(0xFF2F5D8A), Color(0xFFB5893B))[index % 5]

private fun weekdayName(epochDay: Int): String =
    listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")[((epochDay % 7) + 7 + 3) % 7]

private fun longToday(): String {
    val today = civilDateFromEpochDays()
    return "${weekdayName(currentDayIndex())}, ${today.day} de ${currentMonthName(today.month).lowercase()} de ${today.year}"
}

private fun shortMoney(value: Double): String {
    fun short(v: Double) = fixed(v, 1).removeSuffix(".0")
    return when {
        value >= 1_000_000 -> "S/ ${short(value / 1_000_000)}M"
        value >= 1_000 -> "S/ ${short(value / 1_000)}k"
        else -> "S/ ${fixed(value, 0)}"
    }
}

private fun percentLabel(part: Double, total: Double): String {
    if (total <= 0) return "0%"
    val value = part / total * 100
    return if (value in 0.01..9.99) "${fixed(value, 1)}%" else "${value.toInt()}%"
}

// ---------------------------------------------------------------- Shell

@Composable
internal fun WebWorkspace(
    model: DinexViewModel,
    state: DinexUiState,
    actions: WebActions,
    assistant: @Composable () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(WebPage.SUMMARY) }
    val data = state.data
    val name = state.activeAccount?.name ?: state.email.orEmpty().substringBefore('@')
    BoxWithConstraints(Modifier.fillMaxSize().background(W.canvas)) {
        val compact = maxWidth < 1100.dp
        Row(Modifier.fillMaxSize()) {
            WebSidebar(page, { page = it }, compact, name, state.email.orEmpty(), state.profilePhoto, actions)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                WebTopBar(page, data.activeStreak(), state.darkMode, name, state.profilePhoto,
                    onToggleTheme = { model.setDarkMode(!state.darkMode) }, onProfile = actions.onProfile)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (page) {
                        WebPage.SUMMARY -> SummaryPage(data, actions) { page = it }
                        WebPage.MOVEMENTS -> MovementsWebPage(data.movements, actions)
                        WebPage.BUDGET -> BudgetWebPage(model, data, actions)
                        WebPage.GOALS -> GoalsWebPage(data, actions)
                        WebPage.ANALYSIS -> AnalysisWebPage(data.movements, actions)
                        WebPage.ASSISTANT -> PageFrame(scroll = false) {
                            Box(Modifier.fillMaxSize().clip(CardShape).background(W.surface).border(1.dp, W.line, CardShape)) { assistant() }
                        }
                    }
                }
            }
        }
        state.message?.let { message ->
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(W.forest).padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(message, color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = { model.showMessage(null) }) { Text("Cerrar", color = W.lime, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun WebSidebar(
    page: WebPage,
    onSelect: (WebPage) -> Unit,
    compact: Boolean,
    name: String,
    email: String,
    photo: String,
    actions: WebActions,
) {
    Column(
        Modifier.fillMaxHeight().width(if (compact) 76.dp else 248.dp).background(W.forest)
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = 20.dp),
        horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        // Marca
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = if (compact) 0.dp else 6.dp)) {
            Image(painterResource(Res.drawable.dinex_app_icon), "DINEX", Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
            if (!compact) {
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("DINEX", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                    Text("Finanzas personales", color = W.sidebarMuted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        NewMovementButton(compact, actions)
        Spacer(Modifier.height(26.dp))
        if (!compact) SidebarLabel("PRINCIPAL")
        WebPage.entries.filter { it != WebPage.ASSISTANT }.forEach { item ->
            SidebarItem(item.title, item.icon(), page == item, compact) { onSelect(item) }
        }
        Spacer(Modifier.height(18.dp))
        if (!compact) SidebarLabel("HERRAMIENTAS")
        SidebarItem(WebPage.ASSISTANT.title, WebPage.ASSISTANT.icon(), page == WebPage.ASSISTANT, compact) { onSelect(WebPage.ASSISTANT) }
        SidebarItem("Mercado", DinexNavigationIcons.Market, false, compact, onClick = actions.onMarket)
        SidebarItem("Consejos", DinexNavigationIcons.Tips, false, compact, onClick = actions.onTips)
        Spacer(Modifier.weight(1f))
        // Usuario
        val shape = RoundedCornerShape(12.dp)
        Row(
            Modifier.fillMaxWidth().clip(shape).background(W.sidebarActive).clickable(onClickLabel = "Abrir perfil", onClick = actions.onProfile)
                .pointerHoverIcon(PointerIcon.Hand).padding(if (compact) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start,
        ) {
            ProfilePhoto(name, photo, 34.dp, Modifier.clip(CircleShape))
            if (!compact) {
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(email, color = W.sidebarMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(DinexNavigationIcons.Settings, "Ajustes", tint = W.sidebarMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SidebarLabel(text: String) {
    Text(text, color = W.sidebarMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
}

@Composable
private fun SidebarItem(label: String, icon: ImageVector, active: Boolean, compact: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.padding(vertical = 2.dp)) {
        Row(
            Modifier.then(if (compact) Modifier.size(48.dp) else Modifier.fillMaxWidth().height(42.dp)).clip(shape)
                .background(if (active) W.sidebarActive else if (hovered) W.sidebarActive.copy(alpha = .55f) else Color.Transparent)
                .hoverable(interaction).clickable(onClickLabel = label, onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = if (compact) 0.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start,
        ) {
            Icon(icon, label, tint = if (active) W.lime else W.sidebarInk.copy(alpha = .8f), modifier = Modifier.size(19.dp))
            if (!compact) {
                Spacer(Modifier.width(12.dp))
                Text(label, color = if (active) Color.White else W.sidebarInk.copy(alpha = .85f), fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
        if (active) Box(Modifier.align(Alignment.CenterStart).offset(x = if (compact) (-12).dp else (-16).dp).width(3.dp).height(22.dp)
            .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)).background(W.lime))
    }
}

@Composable
private fun NewMovementButton(compact: Boolean, actions: WebActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.then(if (compact) Modifier.size(48.dp) else Modifier.fillMaxWidth().height(44.dp)).clip(RoundedCornerShape(11.dp))
                .background(W.lime).clickable(onClickLabel = "Nuevo movimiento") { open = true }.pointerHoverIcon(PointerIcon.Hand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(DinexNavigationIcons.Add, "Nuevo movimiento", tint = W.forest, modifier = Modifier.size(18.dp))
            if (!compact) {
                Spacer(Modifier.width(8.dp))
                Text("Nuevo movimiento", color = W.forest, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        DropdownMenu(open, { open = false }, containerColor = W.surface) {
            MenuEntry("Registrar gasto", DinexNavigationIcons.ArrowDown) { open = false; actions.onExpense() }
            MenuEntry("Registrar ingreso", DinexNavigationIcons.ArrowUp) { open = false; actions.onIncome() }
            HorizontalDivider(color = W.line)
            MenuEntry("Escanear comprobante", DinexNavigationIcons.ScanReceipt) { open = false; actions.onScan() }
            MenuEntry("Importar Excel o CSV", DinexNavigationIcons.Swap) { open = false; actions.onImport() }
        }
    }
}

@Composable
private fun MenuEntry(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = W.ink, fontSize = 14.sp) },
        leadingIcon = { Icon(icon, null, tint = W.ink2, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
    )
}

@Composable
private fun WebTopBar(
    page: WebPage,
    streak: Int,
    darkMode: Boolean,
    name: String,
    photo: String,
    onToggleTheme: () -> Unit,
    onProfile: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(76.dp).background(W.surface).drawBehind {
            drawLine(W.line, Offset(0f, size.height - 1), Offset(size.width, size.height - 1), 1.dp.toPx())
        }.padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(page.title, color = W.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
            Text(if (page == WebPage.SUMMARY) "Hola, ${name.substringBefore(' ')} · ${longToday()}" else page.subtitle, color = W.ink2, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val level = streakLevel(streak)
        Row(
            Modifier.clip(CircleShape).border(1.dp, W.line, CircleShape).padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StreakBadge(level, 24.dp, locked = streak == 0)
            Spacer(Modifier.width(8.dp))
            Text("$streak ${if (streak == 1) "día" else "días"} de racha", color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(10.dp))
        IconSquare(if (darkMode) DinexNavigationIcons.Sun else DinexNavigationIcons.Moon, if (darkMode) "Modo claro" else "Modo oscuro", onToggleTheme)
        Spacer(Modifier.width(10.dp))
        ProfilePhoto(name, photo, 38.dp, Modifier.clip(CircleShape).clickable(onClickLabel = "Abrir perfil", onClick = onProfile).pointerHoverIcon(PointerIcon.Hand))
    }
}

// ---------------------------------------------------------------- Piezas reutilizables

/** Contenedor de página: centrado, con ancho máximo para que nada se estire de lado a lado. */
@Composable
private fun PageFrame(scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 1240.dp).fillMaxWidth().fillMaxHeight()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 32.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

@Composable
private fun WCard(modifier: Modifier = Modifier, padding: Dp = 22.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(CardShape).background(W.surface).border(1.dp, W.line, CardShape).padding(padding), content = content)
}

@Composable
private fun CardTitle(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = W.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = W.ink3, fontSize = 12.sp) }
        }
        action?.invoke()
    }
}

@Composable
private fun LinkButton(text: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = W.brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Icon(DinexNavigationIcons.Chevron, null, tint = W.brand, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun WButton(text: String, icon: ImageVector? = null, primary: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.height(38.dp).clip(shape).background(if (primary) W.brand else W.surface)
            .then(if (primary) Modifier else Modifier.border(1.dp, W.line, shape))
            .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = if (primary) Color.White else W.ink2, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = if (primary) Color.White else W.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun IconSquare(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.size(38.dp).clip(shape).border(1.dp, W.line, shape).clickable(onClickLabel = label, onClick = onClick).pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = W.ink2, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.height(38.dp).clip(RoundedCornerShape(10.dp)).background(W.surfaceAlt).border(1.dp, W.line, RoundedCornerShape(10.dp)).padding(3.dp)) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier.fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(if (active) W.surface else Color.Transparent)
                    .then(if (active) Modifier.border(1.dp, W.line, RoundedCornerShape(8.dp)) else Modifier)
                    .clickable { onSelect(index) }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (active) W.ink else W.ink2, fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium) }
        }
    }
}

@Composable
private fun SelectMenu(label: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val current = options.firstOrNull { it.first == value }?.second ?: value
    Box {
        Row(
            Modifier.height(38.dp).clip(shape).background(W.surface).border(1.dp, W.line, shape).clickable { open = true }
                .pointerHoverIcon(PointerIcon.Hand).padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = W.ink3, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text(current, color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
            Spacer(Modifier.width(4.dp))
            Icon(DinexNavigationIcons.Chevron, null, tint = W.ink3, modifier = Modifier.size(14.dp).rotate(90f))
        }
        DropdownMenu(open, { open = false }, containerColor = W.surface, modifier = Modifier.heightIn(max = 360.dp)) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text, color = W.ink, fontSize = 14.sp, fontWeight = if (key == value) FontWeight.SemiBold else FontWeight.Normal) },
                    trailingIcon = if (key == value) ({ Icon(DinexNavigationIcons.Check, null, tint = W.brand, modifier = Modifier.size(16.dp)) }) else null,
                    onClick = { onSelect(key); open = false },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
            }
        }
    }
}

@Composable
private fun ThinBar(progress: Float, color: Color, height: Dp = 6.dp, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(if (LocalDinexReducedMotion.current) 0 else 500), label = "barra")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(W.line.copy(alpha = .7f))) {
        if (progress > 0f) Box(Modifier.fillMaxWidth(max(animated, .015f)).fillMaxHeight().clip(CircleShape).background(color))
    }
}

@Composable
private fun CategoryIcon(category: String, income: Boolean, size: Dp = 36.dp) {
    val tint = if (income) W.positive else categoryColor(category)
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = if (DinexThemeState.dark) .2f else .12f)), contentAlignment = Alignment.Center) {
        Icon(if (income) DinexNavigationIcons.ArrowUp else DinexCategoryIcons.forCategory(category), null, tint = tint, modifier = Modifier.size(size * .5f))
    }
}

@Composable
private fun EmptyNote(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = W.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = W.ink3, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (action != null && onAction != null) { Spacer(Modifier.height(14.dp)); WButton(action, primary = true, onClick = onAction) }
    }
}

/** Dos columnas en pantallas anchas; una sola columna cuando no alcanza el espacio. */
@Composable
private fun Columns(ratio: Float = 1.6f, gap: Dp = 20.dp, left: @Composable ColumnScope.() -> Unit, right: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 860.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(ratio), verticalArrangement = Arrangement.spacedBy(gap), content = left)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap), content = right)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) { left(); right() }
        }
    }
}

// ---------------------------------------------------------------- Resumen

@Composable
private fun SummaryPage(data: FinancialData, actions: WebActions, onNavigate: (WebPage) -> Unit) {
    val movements = data.movements
    val today = civilDateFromEpochDays()
    val month = movements.filter { it.isInCurrentMonth() }
    val income = movements.filter { it.income }.sumOf { it.amount }
    val expense = movements.filterNot { it.income }.sumOf { it.amount }
    val available = income - expense - data.savings
    val monthIncome = month.filter { it.income }.sumOf { it.amount }
    val monthExpense = month.filterNot { it.income }.sumOf { it.amount }
    PageFrame {
        Columns(ratio = 1.55f, left = {
            BalanceHero(available, monthIncome, monthExpense, data.savings, month, today, actions)
        }, right = {
            TodayCard(movements.filter { !it.income && it.isToday() }.sumOf { it.amount }, data.dailyLimit.toDouble(), data.activeStreak(),
                data.streakDay == currentDayIndex(), actions) { onNavigate(WebPage.BUDGET) }
        })
        Columns(ratio = 1.55f, left = {
            DailySpendingCard(month, today.year, today.month)
        }, right = {
            CategoryDonutCard(month.filterNot { it.income })
        })
        Columns(ratio = 1.55f, left = {
            RecentMovementsCard(movements.sortedWith(compareByDescending<Movement> { it.epochDay() }.thenByDescending { it.id }).take(7)) { onNavigate(WebPage.MOVEMENTS) }
        }, right = {
            GoalsCompactCard(data.savingGoals(), actions) { onNavigate(WebPage.GOALS) }
            RemindersCard(data.reminders, actions)
        })
    }
}

@Composable
private fun BalanceHero(available: Double, monthIncome: Double, monthExpense: Double, savings: Double, month: List<Movement>, today: DinexDate, actions: WebActions) {
    val shape = CardShape
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(W.forest, W.forest2))).drawBehind {
            // Cuadrícula sutil de fondo.
            val step = 28.dp.toPx()
            var x = step
            while (x < size.width) { drawLine(Color.White.copy(alpha = .035f), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = step
            while (y < size.height) { drawLine(Color.White.copy(alpha = .035f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
        }.padding(26.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SALDO DISPONIBLE", color = W.lime, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, modifier = Modifier.weight(1f))
            Text("${currentMonthName(today.month)} ${today.year}", color = W.sidebarMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(moneyGrouped(available), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
        Spacer(Modifier.height(14.dp))
        // Evolución del saldo en el mes.
        val net = month.sumOf { if (it.income) it.amount else -it.amount }
        val base = available + savings - net
        val points = (1..today.day).map { day -> base + month.filter { it.day <= day }.sumOf { if (it.income) it.amount else -it.amount } }
        Sparkline(points, Modifier.fillMaxWidth().height(56.dp))
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            HeroStat("Ingresos del mes", "+ ${moneyGrouped(monthIncome)}", Color(0xFF8BE3B4), Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = .12f)))
            HeroStat("Gastos del mes", "- ${moneyGrouped(monthExpense)}", Color(0xFFFFA697), Modifier.weight(1f).padding(start = 18.dp))
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = .12f)))
            HeroStat("Ahorrado", moneyGrouped(savings), W.lime, Modifier.weight(1f).padding(start = 18.dp))
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(W.lime).clickable(onClick = actions.onExpense)
                    .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DinexNavigationIcons.ArrowDown, null, tint = W.forest, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Registrar gasto", color = W.forest, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Color.White.copy(alpha = .25f), RoundedCornerShape(10.dp))
                    .clickable(onClick = actions.onIncome).pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DinexNavigationIcons.ArrowUp, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Registrar ingreso", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, tint: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = W.sidebarMuted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = tint, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Sparkline(points: List<Double>, modifier: Modifier) {
    Canvas(modifier) {
        if (points.size < 2) {
            drawLine(W.lime.copy(alpha = .6f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), StrokeCap.Round)
            return@Canvas
        }
        val lo = points.min(); val hi = points.max(); val range = (hi - lo).takeIf { it > 0 } ?: 1.0
        fun at(i: Int) = Offset(size.width * i / (points.size - 1), size.height - ((points[i] - lo) / range * (size.height * .85f)).toFloat() - size.height * .075f)
        val line = Path().apply { moveTo(at(0).x, at(0).y); for (i in 1 until points.size) lineTo(at(i).x, at(i).y) }
        val area = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(area, Brush.verticalGradient(listOf(W.lime.copy(alpha = .28f), Color.Transparent)))
        drawPath(line, W.lime, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(W.lime, 4.dp.toPx(), at(points.lastIndex))
    }
}

@Composable
private fun TodayCard(spent: Double, limit: Double, streak: Int, savedToday: Boolean, actions: WebActions, onBudget: () -> Unit) {
    WCard(Modifier.fillMaxWidth()) {
        CardTitle("Gasto de hoy", "Límite diario ${moneyGrouped(limit)}") { LinkButton("Ajustar", onBudget) }
        Spacer(Modifier.height(16.dp))
        val progress = if (limit > 0) (spent / limit).toFloat() else 0f
        val over = spent > limit
        val ring by animateFloatAsState(progress.coerceIn(0f, 1f), tween(if (LocalDinexReducedMotion.current) 0 else 700), label = "anillo")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                val track = W.line; val color = if (over) W.negative else W.brand
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx(); val inset = stroke / 2
                    drawArc(track, -220f, 260f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
                    if (ring > 0f) drawArc(color, -220f, 260f * ring, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(moneyGrouped(spent), color = W.ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${(progress * 100).toInt()}% del límite", color = W.ink3, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(if (over) "Superaste tu límite" else "Te quedan", color = W.ink3, fontSize = 12.sp)
                Text(moneyGrouped(kotlin.math.abs(limit - spent)), color = if (over) W.negative else W.positive, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (over) "Revisa tus gastos de hoy antes de seguir." else "Puedes gastar esto hoy sin salirte de tu plan.",
                    color = W.ink2, fontSize = 12.sp, lineHeight = 17.sp,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = W.line)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StreakBadge(streakLevel(streak), 34.dp, locked = streak == 0)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Racha de ahorro", color = W.ink3, fontSize = 12.sp)
                Text("$streak ${if (streak == 1) "día" else "días"} · Nivel ${streakLevel(streak).name}", color = W.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            if (savedToday) Text("Hoy ya ahorraste", color = W.positive, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            else WButton("Ahorrar hoy", primary = true) { actions.onSave(null) }
        }
    }
}

@Composable
private fun DailySpendingCard(month: List<Movement>, year: Int, monthNumber: Int) {
    val days = daysInMonth(year, monthNumber)
    val today = civilDateFromEpochDays().day
    val values = (1..days).map { d -> month.filter { !it.income && it.day == d }.sumOf { it.amount } }
    val total = values.sum()
    val average = if (today > 0) values.take(today).sum() / today else 0.0
    var hover by remember { mutableStateOf<Int?>(null) }
    WCard(Modifier.fillMaxWidth()) {
        CardTitle("Gasto diario", "${currentMonthName(monthNumber)} · total ${moneyGrouped(total)} · promedio ${moneyGrouped(average)} por día")
        Spacer(Modifier.height(18.dp))
        val measurer = rememberTextMeasurer()
        val labelStyle = TextStyle(color = W.ink3, fontSize = 10.sp, fontFamily = MaterialTheme.typography.bodySmall.fontFamily)
        val brand = W.brand; val faded = W.line; val ink = W.ink; val lime = W.lime; val ink3 = W.ink3
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(days) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val x = event.changes.first().position.x
                            val left = 44.dp.toPx()
                            hover = if (event.type == PointerEventType.Exit || x < left) null
                            else (((x - left) / ((size.width - left) / days)).toInt() + 1).coerceIn(1, days)
                        }
                    }
                },
            ) {
                val left = 44.dp.toPx(); val bottom = size.height - 22.dp.toPx(); val top = 8.dp.toPx()
                val maxValue = max(values.maxOrNull() ?: 0.0, 1.0)
                val nice = niceTop(maxValue)
                for (i in 0..4) {
                    val y = bottom - (bottom - top) * i / 4
                    drawLine(faded.copy(alpha = if (i == 0) 1f else .6f), Offset(left, y), Offset(size.width, y), 1f)
                    val label = measurer.measure(shortMoney(nice * i / 4), labelStyle)
                    drawText(label, topLeft = Offset(left - label.size.width - 8.dp.toPx(), y - label.size.height / 2))
                }
                val slot = (size.width - left) / days
                val barW = min(slot * .62f, 18.dp.toPx())
                values.forEachIndexed { index, value ->
                    val day = index + 1
                    val x = left + slot * index + (slot - barW) / 2
                    val h = ((bottom - top) * value / nice).toFloat()
                    val color = when {
                        day > today -> faded.copy(alpha = .5f)
                        day == today -> lime
                        hover == day -> ink
                        else -> brand
                    }
                    if (day > today) drawRoundRect(color, Offset(x, bottom - 3.dp.toPx()), Size(barW, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
                    else if (h > 0) drawRoundRect(color, Offset(x, bottom - h), Size(barW, h), CornerRadius(3.dp.toPx()))
                    if (day == 1 || day % 5 == 0 || day == days) {
                        val label = measurer.measure("$day", labelStyle)
                        drawText(label, topLeft = Offset(left + slot * index + slot / 2 - label.size.width / 2, bottom + 6.dp.toPx()))
                    }
                }
                if (average > 0) {
                    val y = bottom - ((bottom - top) * average / nice).toFloat()
                    drawLine(ink3, Offset(left, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                }
            }
            hover?.let { day ->
                Box(Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(8.dp)).background(W.forest).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("$day ${reportMonthShort(monthNumber)} · ${moneyGrouped(values[day - 1])}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(W.brand, "Días anteriores"); Legend(W.lime, "Hoy"); Legend(W.ink3, "Promedio", dashed = true)
        }
    }
}

/** Tope del eje con 4 divisiones de números redondos (0, 20, 40, 60, 80…). */
private fun niceTop(value: Double): Double {
    val raw = value / 4
    var magnitude = 1.0
    while (magnitude * 10 <= raw) magnitude *= 10
    while (magnitude > raw && magnitude > 1.0) magnitude /= 10
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= raw }
    return step * 4
}

@Composable
private fun Legend(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = if (dashed) 14.dp else 10.dp, height = if (dashed) 2.dp else 10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = W.ink3, fontSize = 12.sp)
    }
}

@Composable
private fun CategoryDonutCard(expenses: List<Movement>) {
    val total = expenses.sumOf { it.amount }
    val groups = expenses.groupBy { it.category }.map { (name, list) -> name to list.sumOf { it.amount } }.sortedByDescending { it.second }
    WCard(Modifier.fillMaxWidth()) {
        CardTitle("Gastos por categoría", "Este mes")
        Spacer(Modifier.height(18.dp))
        if (groups.isEmpty()) { EmptyNote("Sin gastos este mes", "Cuando registres un gasto verás aquí en qué se va tu dinero."); return@WCard }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                val colors = groups.mapIndexed { i, g -> categoryColor(g.first, i) }
                val surface = W.surface
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 22.dp.toPx(); var start = -90f
                    groups.forEachIndexed { i, (_, value) ->
                        val sweep = (value / total * 360).toFloat()
                        drawArc(colors[i], start, sweep, false, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                        if (groups.size > 1) drawArc(surface, start + sweep - .8f, 1.6f, false, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke + 2))
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total", color = W.ink3, fontSize = 11.sp)
                    Text(moneyGrouped(total), color = W.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        groups.take(6).forEachIndexed { i, (name, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(categoryColor(name, i)))
                Spacer(Modifier.width(10.dp))
                Text(name, color = W.ink, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(percentLabel(value, total), color = W.ink3, fontSize = 12.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                Text(moneyGrouped(value), color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(96.dp), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun RecentMovementsCard(items: List<Movement>, onAll: () -> Unit) {
    WCard(Modifier.fillMaxWidth(), padding = 0.dp) {
        Box(Modifier.padding(start = 22.dp, end = 14.dp, top = 18.dp, bottom = 8.dp)) {
            CardTitle("Últimos movimientos", "Los más recientes primero") { LinkButton("Ver todos", onAll) }
        }
        if (items.isEmpty()) Box(Modifier.padding(horizontal = 22.dp)) { EmptyNote("Todavía no hay movimientos", "Registra tu primer gasto o ingreso.") }
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = W.line, modifier = Modifier.padding(horizontal = 22.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(item.category, item.income)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, color = W.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${item.category} · ${item.method}", color = W.ink3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(item.reportDate(), color = W.ink3, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
                Text((if (item.income) "+ " else "- ") + moneyGrouped(item.amount), color = if (item.income) W.positive else W.ink,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 100.dp), textAlign = TextAlign.End)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GoalsCompactCard(goals: List<SavingsGoal>, actions: WebActions, onAll: () -> Unit) {
    WCard(Modifier.fillMaxWidth()) {
        CardTitle("Metas de ahorro", "${moneyGrouped(goals.sumOf { it.saved })} ahorrados") { LinkButton("Ver metas", onAll) }
        Spacer(Modifier.height(10.dp))
        if (goals.isEmpty()) { EmptyNote("Aún no tienes metas", "Dale un propósito a tu ahorro.", "Crear meta") { actions.onSave(null) }; return@WCard }
        goals.sortedByDescending { if (it.target > 0) it.saved / it.target else 0.0 }.take(3).forEach { goal ->
            val progress = if (goal.target > 0) (goal.saved / goal.target).toFloat() else 0f
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { actions.onOpenGoal(goal.id) }.pointerHoverIcon(PointerIcon.Hand).padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(goalTheme(goal.name).main), null, Modifier.size(26.dp), contentScale = ContentScale.Fit)
                    Spacer(Modifier.width(10.dp))
                    Text(displayGoalName(goal.name), color = W.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(percentLabel(goal.saved, goal.target), color = W.ink2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                ThinBar(progress, W.brand, 5.dp)
                Spacer(Modifier.height(5.dp))
                Text("${moneyGrouped(goal.saved)} de ${moneyGrouped(goal.target)}", color = W.ink3, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        WButton("Ahorrar", DinexNavigationIcons.Add, primary = true) { actions.onSave(null) }
    }
}

@Composable
private fun RemindersCard(reminders: List<PaymentReminder>, actions: WebActions) {
    WCard(Modifier.fillMaxWidth()) {
        CardTitle("Próximos pagos", if (reminders.isEmpty()) "Sin pagos pendientes" else "${reminders.size} pendientes") {
            IconSquare(DinexNavigationIcons.Add, "Agregar recordatorio", actions.onAddReminder)
        }
        if (reminders.isNotEmpty()) Spacer(Modifier.height(10.dp))
        reminders.sortedBy { it.date }.take(4).forEach { reminder ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(W.surfaceAlt).border(1.dp, W.line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Icon(DinexNavigationIcons.Bell, null, tint = W.warning, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(reminder.title, color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(reminder.date, color = W.ink3, fontSize = 12.sp)
                }
                Text(moneyGrouped(reminder.amount), color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                LinkButton("Pagar") { actions.onResolveReminder(reminder) }
            }
        }
    }
}

// ---------------------------------------------------------------- Movimientos

@Composable
private fun MovementsWebPage(movements: List<Movement>, actions: WebActions) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableIntStateOf(0) }
    var category by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf("") }
    var monthFilter by rememberSaveable { mutableIntStateOf(-1) }
    val months = remember(movements) { movements.map { it.monthKey() }.distinct().sortedDescending() }
    val visible = remember(movements, query, type, category, method, monthFilter) {
        val q = query.trim().lowercase()
        movements.filter { m ->
            (type == 0 || (type == 1 && !m.income) || (type == 2 && m.income)) &&
                (category.isEmpty() || m.category == category) && (method.isEmpty() || m.method == method) &&
                (monthFilter < 0 || m.monthKey() == monthFilter) &&
                (q.isEmpty() || m.title.lowercase().contains(q) || m.category.lowercase().contains(q) || m.method.lowercase().contains(q) || m.location.lowercase().contains(q))
        }.sortedWith(compareByDescending<Movement> { it.epochDay() }.thenByDescending { it.id })
    }
    val income = visible.filter { it.income }.sumOf { it.amount }
    val expense = visible.filterNot { it.income }.sumOf { it.amount }
    PageFrame(scroll = false) {
        // Barra de herramientas: búsqueda y acciones arriba, filtros debajo.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchField(query, { query = it }, Modifier.weight(1f).widthIn(max = 420.dp))
            Spacer(Modifier.weight(.01f))
            WButton("Importar", DinexNavigationIcons.Swap, onClick = actions.onImport)
            WButton("Exportar", DinexNavigationIcons.Send, primary = true, onClick = actions.onExport)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Segmented(listOf("Todos", "Gastos", "Ingresos"), type) { type = it }
            SelectMenu("Categoría", category, listOf("" to "Todas") + movements.map { it.category }.distinct().sorted().map { it to it }) { category = it }
            SelectMenu("Medio", method, listOf("" to "Todos") + movements.map { it.method }.distinct().sorted().map { it to it }) { method = it }
            SelectMenu("Mes", monthFilter.toString(), listOf("-1" to "Todos") + months.map { it.toString() to monthKeyLabel(it) }) { monthFilter = it.toInt() }
            if (query.isNotEmpty() || type != 0 || category.isNotEmpty() || method.isNotEmpty() || monthFilter >= 0) {
                LinkButton("Limpiar filtros") { query = ""; type = 0; category = ""; method = ""; monthFilter = -1 }
            }
        }
        // Resumen de lo filtrado
        Row(
            Modifier.fillMaxWidth().clip(CardShape).background(W.surface).border(1.dp, W.line, CardShape).height(IntrinsicSize.Min),
        ) {
            StripStat("Movimientos", "${visible.size}", W.ink, Modifier.weight(1f))
            StripDivider()
            StripStat("Entradas", "+ ${moneyGrouped(income)}", W.positive, Modifier.weight(1f))
            StripDivider()
            StripStat("Salidas", "- ${moneyGrouped(expense)}", W.negative, Modifier.weight(1f))
            StripDivider()
            StripStat("Neto", moneyGrouped(income - expense), if (income - expense < 0) W.negative else W.ink, Modifier.weight(1f))
        }
        // Tabla
        Column(Modifier.fillMaxWidth().weight(1f).clip(CardShape).background(W.surface).border(1.dp, W.line, CardShape)) {
            Row(Modifier.fillMaxWidth().background(W.surfaceAlt).padding(horizontal = 20.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                TableHead("Descripción", Modifier.weight(2.4f))
                TableHead("Categoría", Modifier.weight(1.3f))
                TableHead("Medio", Modifier.weight(1f))
                TableHead("Fecha", Modifier.weight(1f))
                TableHead("Monto", Modifier.weight(1.1f), TextAlign.End)
                Spacer(Modifier.width(44.dp))
            }
            HorizontalDivider(color = W.line)
            if (visible.isEmpty()) {
                EmptyNote(
                    if (movements.isEmpty()) "Todavía no hay movimientos" else "Sin resultados",
                    if (movements.isEmpty()) "Registra tu primer gasto o importa un archivo de tu banco." else "Ningún movimiento coincide con los filtros.",
                    if (movements.isEmpty()) "Registrar gasto" else null, if (movements.isEmpty()) actions.onExpense else null,
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(visible) { item -> MovementTableRow(item) { actions.onDeleteMovement(item) } }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(modifier.height(38.dp).clip(shape).background(W.surface).border(1.dp, W.line, shape).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(DinexNavigationIcons.Search, null, tint = W.ink3, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Buscar movimiento…", color = W.ink3, fontSize = 13.sp)
            BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = W.ink, fontSize = 13.sp, fontFamily = MaterialTheme.typography.bodyMedium.fontFamily),
                cursorBrush = SolidColor(W.brand), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) Icon(DinexNavigationIcons.Close, "Limpiar búsqueda", tint = W.ink3, modifier = Modifier.size(16.dp).clickable { onChange("") })
    }
}

@Composable
private fun StripStat(label: String, value: String, tint: Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(label, color = W.ink3, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = tint, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StripDivider() { Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 12.dp).background(W.line)) }

@Composable
private fun TableHead(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(text.uppercase(), color = W.ink3, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, modifier = modifier, textAlign = align)
}

@Composable
private fun MovementTableRow(item: Movement, onDelete: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column {
        Row(
            Modifier.fillMaxWidth().background(if (hovered) W.hover else Color.Transparent).hoverable(interaction).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(2.4f), verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(item.category, item.income, 34.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(item.title, color = W.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.location.isNotBlank()) Text(item.location, color = W.ink3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.weight(1.3f)) {
                Row(Modifier.clip(CircleShape).background(W.surfaceAlt).border(1.dp, W.line, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (item.income) W.positive else categoryColor(item.category)))
                    Spacer(Modifier.width(6.dp))
                    Text(item.category, color = W.ink2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(item.method, color = W.ink2, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.day} ${reportMonthShort(item.month)} ${item.year}", color = W.ink2, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text((if (item.income) "+ " else "- ") + moneyGrouped(item.amount), color = if (item.income) W.positive else W.ink, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
            Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterEnd) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Eliminar ${item.title}", onClick = onDelete).pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center,
                ) { Icon(DinexNavigationIcons.Close, "Eliminar", tint = if (hovered) W.negative else W.ink3.copy(alpha = .5f), modifier = Modifier.size(15.dp)) }
            }
        }
        HorizontalDivider(color = W.line.copy(alpha = .6f), modifier = Modifier.padding(horizontal = 20.dp))
    }
}

// ---------------------------------------------------------------- Presupuesto

@Composable
private fun BudgetWebPage(model: DinexViewModel, data: FinancialData, actions: WebActions) {
    val month = data.movements.filter { it.isInCurrentMonth() }
    val suggestions = remember(data.movements) { suggestedCategoryBudgets(month, data.movements) }
    var editor by remember { mutableStateOf(false) }
    val spentToday = data.movements.filter { !it.income && it.isToday() }.sumOf { it.amount }
    PageFrame {
        Columns(ratio = 1f, left = {
            WCard(Modifier.fillMaxWidth()) {
                CardTitle("Límite diario", "Lo máximo que quieres gastar en un día")
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(moneyGrouped(data.dailyLimit.toDouble()), color = W.ink, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                    Spacer(Modifier.width(8.dp))
                    Text("por día", color = W.ink3, fontSize = 13.sp, modifier = Modifier.padding(bottom = 7.dp))
                }
                Slider(
                    value = data.dailyLimit, onValueChange = { model.setDailyLimit((it / 5f).toInt() * 5f) }, valueRange = 20f..120f,
                    colors = SliderDefaults.colors(thumbColor = W.brand, activeTrackColor = W.brand, inactiveTrackColor = W.line),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
                Row { Text("S/ 20", color = W.ink3, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text("S/ 120", color = W.ink3, fontSize = 12.sp) }
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = W.line)
                Spacer(Modifier.height(16.dp))
                Row {
                    Text("Hoy llevas", color = W.ink2, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("${moneyGrouped(spentToday)} de ${moneyGrouped(data.dailyLimit.toDouble())}", color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                ThinBar((spentToday / data.dailyLimit).toFloat(), if (spentToday > data.dailyLimit) W.negative else W.brand, 8.dp)
                Spacer(Modifier.height(10.dp))
                Text("Al mes son ${moneyGrouped(data.dailyLimit * 30.0)} como máximo.", color = W.ink3, fontSize = 12.sp)
            }
            RemindersCard(data.reminders, actions)
        }, right = {
            WCard(Modifier.fillMaxWidth()) {
                CardTitle("Presupuesto por categoría", "${currentMonthName()} · sugerido según tus ingresos e historial") {
                    WButton("Editar límites", DinexNavigationIcons.Settings) { editor = true }
                }
                Spacer(Modifier.height(14.dp))
                categories.forEach { info ->
                    val limit = data.categoryBudgets[info.name] ?: suggestions[info.name] ?: 0.0
                    val spent = month.filter { !it.income && it.category == info.name }.sumOf { it.amount }
                    val over = limit > 0 && spent > limit
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryIcon(info.name, false, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(info.name, color = W.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    when { limit <= 0 -> "Sin límite"; over -> "Te pasaste por ${moneyGrouped(spent - limit)}"; else -> "Quedan ${moneyGrouped(limit - spent)}" },
                                    color = if (over) W.negative else W.ink3, fontSize = 12.sp,
                                )
                            }
                            Text("${moneyGrouped(spent)} / ${moneyGrouped(limit)}", color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(9.dp))
                        ThinBar(if (limit > 0) (spent / limit).toFloat() else 0f, if (over) W.negative else info.color, 6.dp)
                    }
                }
                if (data.categoryBudgets.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    LinkButton("Volver a los límites sugeridos") { model.resetCategoryBudgets() }
                }
            }
        })
    }
    if (editor) CategoryBudgetEditorDialog(suggestions, data.categoryBudgets, onDismiss = { editor = false },
        onSave = { if (model.setCategoryBudgets(it)) editor = false }, onReset = { model.resetCategoryBudgets(); editor = false })
}

// ---------------------------------------------------------------- Metas

@Composable
private fun GoalsWebPage(data: FinancialData, actions: WebActions) {
    val goals = data.savingGoals()
    val saved = goals.sumOf { it.saved }
    val target = goals.sumOf { it.target }
    PageFrame {
        Row(
            Modifier.fillMaxWidth().clip(CardShape).background(W.surface).border(1.dp, W.line, CardShape).height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StripStat("Total ahorrado", moneyGrouped(saved), W.brand, Modifier.weight(1f))
            StripDivider()
            StripStat("Meta total", moneyGrouped(target), W.ink, Modifier.weight(1f))
            StripDivider()
            StripStat("Avance general", percentLabel(saved, target), W.ink, Modifier.weight(1f))
            StripDivider()
            Box(Modifier.weight(1f).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                WButton("Ahorrar en una meta", DinexNavigationIcons.Add, primary = true) { actions.onSave(null) }
            }
        }
        if (goals.isEmpty()) {
            WCard(Modifier.fillMaxWidth()) { EmptyNote("Aún no tienes metas", "Crea una meta para darle un propósito a tu ahorro.", "Crear mi primera meta") { actions.onSave(null) } }
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = when { maxWidth >= 1050.dp -> 3; maxWidth >= 680.dp -> 2; else -> 1 }
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    goals.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            row.forEach { goal -> GoalCard(goal, actions, Modifier.weight(1f)) }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalCard(goal: SavingsGoal, actions: WebActions, modifier: Modifier) {
    val theme = goalTheme(goal.name)
    val progress = if (goal.target > 0) (goal.saved / goal.target).toFloat() else 0f
    WCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(theme.top.copy(alpha = if (DinexThemeState.dark) .22f else .14f)), contentAlignment = Alignment.Center) {
                Image(painterResource(theme.main), null, Modifier.size(38.dp), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(displayGoalName(goal.name), color = W.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (progress >= 1f) "Meta cumplida" else "Faltan ${moneyGrouped((goal.target - goal.saved).coerceAtLeast(0.0))}",
                    color = if (progress >= 1f) W.positive else W.ink3, fontSize = 12.sp)
            }
            Text(percentLabel(goal.saved, goal.target), color = W.brand, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(18.dp))
        ThinBar(progress, W.brand, 8.dp)
        Spacer(Modifier.height(10.dp))
        Row {
            Text(moneyGrouped(goal.saved), color = W.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("de ${moneyGrouped(goal.target)}", color = W.ink3, fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WButton("Ahorrar", DinexNavigationIcons.Add, primary = true) { actions.onSave(goal.id) }
            WButton("Ver detalle") { actions.onOpenGoal(goal.id) }
        }
    }
}

// ---------------------------------------------------------------- Análisis

@Composable
private fun AnalysisWebPage(movements: List<Movement>, actions: WebActions) {
    val months = remember(movements) { (movements.map { it.monthKey() } + civilDateFromEpochDays().let { it.year * 12 + it.month - 1 }).distinct().sortedDescending() }
    var selected by rememberSaveable { mutableIntStateOf(months.first()) }
    val inMonth = movements.filter { it.monthKey() == selected }
    val expenses = inMonth.filterNot { it.income }
    val income = inMonth.filter { it.income }.sumOf { it.amount }
    val expense = expenses.sumOf { it.amount }
    val previous = movements.filter { it.monthKey() == selected - 1 && !it.income }.sumOf { it.amount }
    PageFrame {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectMenu("Mes", selected.toString(), months.map { it.toString() to monthKeyLabel(it) }) { selected = it.toInt() }
            Spacer(Modifier.weight(1f))
            WButton("Exportar reporte", DinexNavigationIcons.Send, primary = true, onClick = actions.onExport)
        }
        Row(Modifier.fillMaxWidth().clip(CardShape).background(W.surface).border(1.dp, W.line, CardShape).height(IntrinsicSize.Min)) {
            StripStat("Ingresos", "+ ${moneyGrouped(income)}", W.positive, Modifier.weight(1f))
            StripDivider()
            StripStat("Gastos", "- ${moneyGrouped(expense)}", W.negative, Modifier.weight(1f))
            StripDivider()
            StripStat("Ahorro del mes", percentLabel((income - expense).coerceAtLeast(0.0), income), W.ink, Modifier.weight(1f))
            StripDivider()
            StripStat("Vs. mes anterior", when {
                previous <= 0 -> "Sin datos"
                expense >= previous -> "+${percentLabel(expense - previous, previous)} en gastos"
                else -> "-${percentLabel(previous - expense, previous)} en gastos"
            }, if (previous > 0 && expense > previous) W.negative else W.ink, Modifier.weight(1f))
        }
        Columns(ratio = 1.4f, left = {
            MonthlyComparisonCard(movements, selected)
        }, right = {
            WeekdayCard(expenses)
        })
        Columns(ratio = 1f, left = {
            CategoryTableCard(expenses)
        }, right = {
            WCard(Modifier.fillMaxWidth()) {
                CardTitle("Dónde gastas", "Gastos registrados con ubicación")
                Spacer(Modifier.height(14.dp))
                val places = remember(movements) { spendingPlaces(movements) }
                if (places.isEmpty()) EmptyNote("Sin ubicaciones", "Registra gastos con ubicación desde el celular para ver tu mapa de calor.")
                else Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(12.dp))) { SpendingHeatmap(places, Modifier.fillMaxSize()) }
            }
        })
    }
}

@Composable
private fun MonthlyComparisonCard(movements: List<Movement>, selected: Int) {
    val keys = (selected - 5..selected).toList()
    val series = keys.map { key ->
        val list = movements.filter { it.monthKey() == key }
        list.filter { it.income }.sumOf { it.amount } to list.filterNot { it.income }.sumOf { it.amount }
    }
    WCard(Modifier.fillMaxWidth()) {
        CardTitle("Ingresos y gastos", "Últimos 6 meses")
        Spacer(Modifier.height(18.dp))
        val measurer = rememberTextMeasurer()
        val labelStyle = TextStyle(color = W.ink3, fontSize = 10.sp, fontFamily = MaterialTheme.typography.bodySmall.fontFamily)
        val positive = W.brand; val negative = W.negative; val line = W.line
        Canvas(Modifier.fillMaxWidth().height(220.dp)) {
            val left = 48.dp.toPx(); val bottom = size.height - 22.dp.toPx(); val top = 6.dp.toPx()
            val top0 = niceTop(max(series.maxOf { max(it.first, it.second) }, 1.0))
            for (i in 0..4) {
                val y = bottom - (bottom - top) * i / 4
                drawLine(line, Offset(left, y), Offset(size.width, y), 1f)
                val label = measurer.measure(shortMoney(top0 * i / 4), labelStyle)
                drawText(label, topLeft = Offset(left - label.size.width - 8.dp.toPx(), y - label.size.height / 2))
            }
            val slot = (size.width - left) / keys.size
            val barW = min(slot * .26f, 22.dp.toPx())
            series.forEachIndexed { i, (inc, exp) ->
                val cx = left + slot * i + slot / 2
                listOf(inc to positive, exp to negative).forEachIndexed { j, (value, color) ->
                    val h = ((bottom - top) * value / top0).toFloat()
                    if (h > 0) drawRoundRect(color.copy(alpha = if (keys[i] == selected) 1f else .55f), Offset(if (j == 0) cx - barW - 2 else cx + 2, bottom - h), Size(barW, h), CornerRadius(3.dp.toPx()))
                }
                val label = measurer.measure(monthKeyShort(keys[i]), labelStyle)
                drawText(label, topLeft = Offset(cx - label.size.width / 2, bottom + 6.dp.toPx()))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { Legend(W.brand, "Ingresos"); Legend(W.negative, "Gastos") }
    }
}

@Composable
private fun WeekdayCard(expenses: List<Movement>) {
    val names = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    val totals = DoubleArray(7)
    expenses.forEach { totals[((it.epochDay() % 7) + 7 + 3) % 7] += it.amount }
    val top = totals.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val busiest = totals.indices.maxByOrNull { totals[it] } ?: 0
    WCard(Modifier.fillMaxWidth()) {
        CardTitle("Por día de la semana", if (expenses.isEmpty()) "Sin gastos en el mes" else "Gastas más los ${weekdayName(busiest + 4).lowercase()}")
        Spacer(Modifier.height(16.dp))
        names.forEachIndexed { i, name ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = W.ink2, fontSize = 12.sp, modifier = Modifier.width(36.dp))
                ThinBar((totals[i] / top).toFloat(), if (i == busiest && totals[i] > 0) W.brand else W.brand.copy(alpha = .45f), 10.dp, Modifier.weight(1f))
                Text(moneyGrouped(totals[i]), color = W.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(92.dp), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun CategoryTableCard(expenses: List<Movement>) {
    val total = expenses.sumOf { it.amount }
    val groups = expenses.groupBy { it.category }.map { (name, list) -> Triple(name, list.sumOf { it.amount }, list.size) }.sortedByDescending { it.second }
    WCard(Modifier.fillMaxWidth(), padding = 0.dp) {
        Box(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 12.dp)) { CardTitle("Detalle por categoría", "${groups.size} categorías con gastos") }
        Row(Modifier.fillMaxWidth().background(W.surfaceAlt).padding(horizontal = 22.dp, vertical = 9.dp)) {
            TableHead("Categoría", Modifier.weight(1.6f)); TableHead("Movs.", Modifier.weight(.6f), TextAlign.End)
            TableHead("Participación", Modifier.weight(1.6f).padding(start = 18.dp)); TableHead("Total", Modifier.weight(1f), TextAlign.End)
        }
        if (groups.isEmpty()) Box(Modifier.padding(horizontal = 22.dp)) { EmptyNote("Sin gastos", "Elige otro mes o registra un gasto.") }
        groups.forEachIndexed { i, (name, value, count) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1.6f), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(categoryColor(name, i)))
                    Spacer(Modifier.width(10.dp))
                    Text(name, color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("$count", color = W.ink2, fontSize = 13.sp, modifier = Modifier.weight(.6f), textAlign = TextAlign.End)
                Row(Modifier.weight(1.6f).padding(start = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    ThinBar((value / total).toFloat(), categoryColor(name, i), 6.dp, Modifier.weight(1f))
                    Text(percentLabel(value, total), color = W.ink3, fontSize = 12.sp, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
                }
                Text(moneyGrouped(value), color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
            HorizontalDivider(color = W.line.copy(alpha = .6f), modifier = Modifier.padding(horizontal = 22.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}
