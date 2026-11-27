@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.dinex

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.max

// ═══════════════════════════════════════════════════════════════════════════
// DINEX WEB
// Diseño propio para el navegador (pantallas de 760 px o más). Usa las mismas
// funciones y datos que la app (DinexViewModel), con una distribución de
// escritorio: barra lateral flotante, encabezado, buscador de comandos (Ctrl K)
// y páginas con gráficos interactivos. En pantallas angostas se usa el diseño
// del celular.
//   WebWorkspace.kt  → marco, menú, buscador de comandos y piezas comunes
//   WebCharts.kt     → gráficos
//   WebSummary.kt    → página Resumen
//   WebPages.kt      → Movimientos, Presupuesto, Metas y Análisis
//   WebAssistant.kt  → Dinex IA y panel de la pantalla de acceso
// ═══════════════════════════════════════════════════════════════════════════

/** true cuando la app corre en el navegador (lo indica webApp/main.kt). */
internal val LocalWebLayout = staticCompositionLocalOf { false }

/** Ancho mínimo para usar el diseño de escritorio en la web. */
internal val WebDesktopMinWidth = 760.dp

internal enum class WebPage(val title: String, val subtitle: String, val shortcut: String) {
    SUMMARY("Resumen", "Tu dinero de un vistazo", "1"),
    MOVEMENTS("Movimientos", "Todo lo que entra y sale de tu cuenta", "2"),
    BUDGET("Presupuesto", "Límites, simulador de ahorro y próximos pagos", "3"),
    GOALS("Metas", "Ahorro con propósito y planificador", "4"),
    ANALYSIS("Análisis", "Patrones y tendencias de tus gastos", "5"),
    ASSISTANT("Dinex IA", "Pregunta lo que quieras sobre tus finanzas", "6"),
    MARKET("Mercado", "Tipos de cambio, metales y criptomonedas", "7"),
    TIPS("Consejos", "Recomendaciones según tus números", "8"),
}

internal fun WebPage.icon(): ImageVector = when (this) {
    WebPage.SUMMARY -> DinexNavigationIcons.Home
    WebPage.MOVEMENTS -> DinexNavigationIcons.Movements
    WebPage.BUDGET -> DinexNavigationIcons.Plan
    WebPage.GOALS -> DinexNavigationIcons.Target
    WebPage.ANALYSIS -> DinexNavigationIcons.Analysis
    WebPage.ASSISTANT -> DinexNavigationIcons.Sparkle
    WebPage.MARKET -> DinexNavigationIcons.Market
    WebPage.TIPS -> DinexNavigationIcons.Tips
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
    val onYupay: () -> Unit,
)

// ---------------------------------------------------------------- Paleta y tipografía

/** Colores de la web: los verdes y lima de DINEX; en modo oscuro, fondos gris carbón neutros. */
internal object W {
    private val dark get() = DinexThemeState.dark
    val canvas get() = if (dark) Color(0xFF1A1D21) else Color(0xFFF2F4F0)
    val surface get() = if (dark) Color(0xFF23272B) else Color(0xFFFFFFFF)
    val surfaceAlt get() = if (dark) Color(0xFF292D32) else Color(0xFFF7F8F5)
    val hover get() = if (dark) Color(0xFF31363B) else Color(0xFFF1F5F1)
    val line get() = if (dark) Color(0xFF393F45) else Color(0xFFE2E7E1)
    val ink get() = if (dark) Color(0xFFEEF1F4) else Color(0xFF101B15)
    val ink2 get() = if (dark) Color(0xFFAAB3BB) else Color(0xFF526258)
    val ink3 get() = if (dark) Color(0xFF747E87) else Color(0xFF8A978E)
    val brand get() = if (dark) Color(0xFF5ED49B) else Color(0xFF0D6A43)
    val brandSoft get() = if (dark) Color(0xFF283630) else Color(0xFFE1F0E6)
    val positive get() = if (dark) Color(0xFF5ED49B) else Color(0xFF12804A)
    val negative get() = if (dark) Color(0xFFF08B7B) else Color(0xFFC0392B)
    val warning get() = if (dark) Color(0xFFF2C46B) else Color(0xFFB7791F)
    val lime = Color(0xFFC6F06A)
    val forest = Color(0xFF0E1E16)
    val forest2 = Color(0xFF143826)
    val forestDeep = Color(0xFF0A1610)
    val mint = Color(0xFF8BE3B4)
    val coral = Color(0xFFFFA697)
    val sidebarInk = Color(0xFFDCE7DF)
    val sidebarMuted = Color(0xFF7D9586)
    val sidebarActive = Color(0xFF1A2E23)
    /** Tarjetas principales (saldo, metas, Dinex IA): bosque en modo claro, azul pizarra en oscuro (igual que la app). */
    val hero: List<Color> get() = if (dark) listOf(Color(0xFF26333F), Color(0xFF33445A), Color(0xFF43586D)) else listOf(forestDeep, forest, forest2)
}

internal val WebCardShape = RoundedCornerShape(22.dp)

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

// ---------------------------------------------------------------- Textos y formatos

internal fun webMonthLabel(key: Int) = "${currentMonthName(monthKeyMonth(key))} ${monthKeyYear(key)}"
internal fun webMonthShort(key: Int) = reportMonthShort(monthKeyMonth(key)).replaceFirstChar { it.uppercase() }

internal fun categoryColor(name: String, index: Int = 0): Color =
    categories.firstOrNull { it.name.equals(name, true) }?.color
        ?: listOf(Color(0xFF3E8EB8), Color(0xFFC2577F), Color(0xFF4F9D69), Color(0xFF2F5D8A), Color(0xFFB5893B))[index % 5]

internal fun longToday(): String {
    val today = civilDateFromEpochDays()
    return "${webWeekdaysLong[weekdayIndex(currentDayIndex())]}, ${today.day} de ${currentMonthName(today.month).lowercase()}"
}

internal fun shortMoney(value: Double): String {
    fun short(v: Double) = fixed(v, 1).removeSuffix(".0")
    val sign = if (value < 0) "-" else ""
    val abs = kotlin.math.abs(value)
    return sign + when {
        abs >= 1_000_000 -> "S/ ${short(abs / 1_000_000)}M"
        abs >= 1_000 -> "S/ ${short(abs / 1_000)}k"
        else -> "S/ ${fixed(abs, 0)}"
    }
}

internal fun percentLabel(part: Double, total: Double): String {
    if (total <= 0) return "0%"
    val value = part / total * 100
    return if (value in 0.01..9.99) "${fixed(value, 1)}%" else "${value.toInt()}%"
}

// ---------------------------------------------------------------- Marco

@Composable
internal fun WebWorkspace(
    model: DinexViewModel,
    state: DinexUiState,
    actions: WebActions,
    assistant: @Composable () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(WebPage.SUMMARY) }
    var palette by remember { mutableStateOf(false) }
    val data = state.data
    val name = state.activeAccount?.name ?: state.email.orEmpty().substringBefore('@')
    val health = remember(data) { financialHealth(data) }
    val toggleTheme = { model.setDarkMode(!state.darkMode) }
    val focus = remember { FocusRequester() }
    var anyFocus by remember { mutableStateOf(false) }
    // Al empezar y al cerrar el buscador, el foco vuelve al escritorio para que Ctrl K siga funcionando.
    LaunchedEffect(palette) { if (!palette) { kotlinx.coroutines.delay(200); runCatching { focus.requestFocus() } } }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(W.canvas).webBackdrop()
            .onPreviewKeyEvent { event ->
                // Ctrl K (o ⌘ K en Mac) abre el buscador de comandos desde cualquier página.
                if (event.type == KeyEventType.KeyDown && event.key == Key.K && (event.isCtrlPressed || event.isMetaPressed)) {
                    palette = !palette; true
                } else false
            }
            .focusRequester(focus).onFocusChanged { anyFocus = it.hasFocus }.focusable()
            // Si nada tiene el foco (por ejemplo, tras un clic en una tarjeta), un clic lo devuelve al
            // escritorio para que Ctrl K siga funcionando. No toca los campos de texto que ya lo tienen.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press && !anyFocus) runCatching { focus.requestFocus() }
                    }
                }
            },
    ) {
        val compact = maxWidth < 1180.dp
        val tall = maxHeight >= 820.dp
        Row(Modifier.fillMaxSize()) {
            WebSidebar(page, { page = it }, compact, name, state.email.orEmpty(), state.profilePhoto, health, actions, tall = tall, onPalette = { palette = true })
            Column(Modifier.weight(1f).fillMaxHeight()) {
                WebTopBar(page, name, data.activeStreak(), state.darkMode, state.profilePhoto, data.reminders, actions, toggleTheme) { page = it }
                val reduced = LocalDinexReducedMotion.current
                AnimatedContent(
                    targetState = page,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        val time = if (reduced) 0 else 320
                        (fadeIn(tween(time)) + slideInVertically(tween(time, easing = FastOutSlowInEasing)) { if (reduced) 0 else 26 }) togetherWith
                            (fadeOut(tween(time / 3)) + slideOutVertically(tween(time / 3)) { if (reduced) 0 else -10 })
                    },
                    label = "página web",
                ) { current ->
                    when (current) {
                        WebPage.SUMMARY -> SummaryPage(data, health, actions) { page = it }
                        WebPage.MOVEMENTS -> MovementsWebPage(data.movements, actions)
                        WebPage.BUDGET -> BudgetWebPage(model, data, actions)
                        WebPage.GOALS -> GoalsWebPage(data, actions)
                        WebPage.ANALYSIS -> AnalysisWebPage(data, health, actions)
                        WebPage.ASSISTANT -> assistant()
                        // Mercado y Consejos se muestran dentro del escritorio, sin tapar la barra lateral.
                        WebPage.MARKET -> MarketScreen(onClose = { page = WebPage.SUMMARY })
                        WebPage.TIPS -> TipsScreen(data, onClose = { page = WebPage.SUMMARY }, onAction = { action ->
                            when (action) {
                                TipAction.AddMovement -> actions.onExpense()
                                TipAction.Save -> actions.onSave(null)
                                TipAction.Plan -> page = WebPage.BUDGET
                                TipAction.Analysis -> page = WebPage.ANALYSIS
                                TipAction.Assistant -> page = WebPage.ASSISTANT
                            }
                        })
                    }
                }
            }
        }

        // Aviso flotante (guardado, eliminado, errores…).
        val message = state.message
        LaunchedEffect(message) {
            if (message != null) { kotlinx.coroutines.delay(4_000); model.showMessage(null) }
        }
        AnimatedVisibility(
            visible = message != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            var shown by remember { mutableStateOf("") }
            if (message != null) shown = message
            Row(
                Modifier.shadow(18.dp, CircleShape, ambientColor = W.forest, spotColor = W.forest).clip(CircleShape)
                    .background(W.forest).border(1.dp, Color.White.copy(alpha = .08f), CircleShape)
                    .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(W.lime), contentAlignment = Alignment.Center) {
                    Icon(DinexNavigationIcons.Check, null, tint = W.forest, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(shown, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = { model.showMessage(null) }) { Text("Cerrar", color = W.lime, fontWeight = FontWeight.SemiBold) }
            }
        }

        // Buscador de comandos.
        AnimatedVisibility(palette, enter = fadeIn(tween(140)), exit = fadeOut(tween(120))) {
            CommandPalette(
                commands = webCommands(state.darkMode, actions, toggleTheme) { page = it },
                onClose = { palette = false },
            )
        }
    }
}

/** Fondo del lienzo: dos brillos suaves de los colores de la marca. */
private fun Modifier.webBackdrop(): Modifier = drawBehind {
    val dark = DinexThemeState.dark
    drawCircle(
        Brush.radialGradient(listOf(W.lime.copy(alpha = if (dark) .05f else .16f), Color.Transparent), Offset(size.width * .92f, 0f), size.minDimension * .7f),
        size.minDimension * .7f, Offset(size.width * .92f, 0f),
    )
    drawCircle(
        Brush.radialGradient(listOf(W.brand.copy(alpha = if (dark) .07f else .06f), Color.Transparent), Offset(size.width * .35f, size.height), size.minDimension * .8f),
        size.minDimension * .8f, Offset(size.width * .35f, size.height),
    )
}

// ---------------------------------------------------------------- Barra lateral

@Composable
private fun WebSidebar(
    page: WebPage,
    onSelect: (WebPage) -> Unit,
    compact: Boolean,
    name: String,
    email: String,
    photo: String,
    health: FinancialHealth,
    actions: WebActions,
    tall: Boolean,
    onPalette: () -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp).fillMaxHeight().width(if (compact) 80.dp else 256.dp)
            .shadow(20.dp, shape, ambientColor = W.forest.copy(alpha = .3f), spotColor = W.forest.copy(alpha = .3f)).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF12291D), W.forest, W.forestDeep)))
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(W.lime.copy(alpha = .13f), Color.Transparent), Offset(0f, 0f), size.width * 1.1f), size.width * 1.1f, Offset(0f, 0f))
                // Puntos sutiles.
                val step = 18.dp.toPx()
                var y = step
                while (y < size.height * .3f) {
                    var x = step
                    while (x < size.width) { drawCircle(Color.White.copy(alpha = .035f), 1.dp.toPx(), Offset(x, y)); x += step }
                    y += step
                }
            }
            .padding(horizontal = if (compact) 14.dp else 16.dp, vertical = 20.dp),
        horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        var brandMenu by remember { mutableStateOf(false) }
        Box {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = if (compact) 0.dp else 4.dp)
            .clickable(onClickLabel = "Abrir menú de DINEX", onClick = { brandMenu = true })) {
            Box {
                Image(painterResource(Res.drawable.dinex_app_icon), "DINEX", Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)), contentScale = ContentScale.Crop)
                Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp).size(10.dp).clip(CircleShape).background(W.forest).padding(2.dp).clip(CircleShape).background(W.lime))
            }
            if (!compact) {
                Spacer(Modifier.width(11.dp))
                Column {
                    Text("DINEX", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.2.sp)
                    Text("Tu dinero, con propósito.", color = W.sidebarMuted, fontSize = 11.sp)
                }
            }
        }
        YupayBrandMenu(brandMenu, onDismiss = { brandMenu = false }, onOpen = { brandMenu = false; actions.onYupay() })
        }
        Spacer(Modifier.height(22.dp))
        PaletteLauncher(compact, onPalette)
        Spacer(Modifier.height(12.dp))
        NewMovementButton(compact, actions)
        Spacer(Modifier.height(22.dp))
        if (!compact) SidebarLabel("PRINCIPAL")
        WebPage.entries.take(5).forEach { item -> SidebarItem(item, page == item, compact) { onSelect(item) } }
        Spacer(Modifier.height(16.dp))
        if (!compact) SidebarLabel("HERRAMIENTAS")
        listOf(WebPage.ASSISTANT, WebPage.MARKET, WebPage.TIPS).forEach { item -> SidebarItem(item, page == item, compact) { onSelect(item) } }
        Spacer(Modifier.weight(1f).heightIn(min = 18.dp))
        if (!compact && tall) {
            SidebarHealth(health) { onSelect(WebPage.ANALYSIS) }
            Spacer(Modifier.height(12.dp))
        }
        val userShape = RoundedCornerShape(16.dp)
        Row(
            Modifier.fillMaxWidth().clip(userShape).background(Color.White.copy(alpha = .05f)).border(1.dp, Color.White.copy(alpha = .06f), userShape)
                .clickable(onClickLabel = "Abrir perfil", onClick = actions.onProfile).pointerHoverIcon(PointerIcon.Hand).padding(if (compact) 6.dp else 10.dp),
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
                Icon(DinexNavigationIcons.Settings, "Ajustes", tint = W.sidebarMuted, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun SidebarLabel(text: String) {
    Text(text, color = W.sidebarMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp,
        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
}

@Composable
private fun PaletteLauncher(compact: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.then(if (compact) Modifier.size(48.dp) else Modifier.fillMaxWidth().height(40.dp)).clip(shape)
            .background(Color.White.copy(alpha = if (hovered) .09f else .05f)).border(1.dp, Color.White.copy(alpha = .08f), shape)
            .hoverable(interaction).clickable(onClickLabel = "Buscar o ir a…", onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = if (compact) 0.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start,
    ) {
        Icon(DinexNavigationIcons.Search, "Buscar", tint = W.sidebarInk.copy(alpha = .75f), modifier = Modifier.size(16.dp))
        if (!compact) {
            Spacer(Modifier.width(9.dp))
            Text("Buscar o ir a…", color = W.sidebarMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Kbd("Ctrl K", dark = true)
        }
    }
}

@Composable
private fun SidebarItem(item: WebPage, active: Boolean, compact: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(12.dp)
    val reduced = LocalDinexReducedMotion.current
    val fill by animateColorAsState(
        when { active -> W.lime.copy(alpha = .13f); hovered -> Color.White.copy(alpha = .05f); else -> Color.Transparent }, tween(180), label = "fondo del menú",
    )
    val nudge by animateFloatAsState(if (hovered && !active && !reduced) 3f else 0f, tween(160), label = "desplazamiento del menú")
    val bar by animateFloatAsState(if (active) 1f else 0f, tween(if (reduced) 0 else 260), label = "marca del menú")
    Box(Modifier.padding(vertical = 2.dp).graphicsLayer { translationX = nudge * density }) {
        Row(
            Modifier.then(if (compact) Modifier.size(48.dp) else Modifier.fillMaxWidth().height(42.dp)).clip(shape).background(fill)
                .hoverable(interaction).clickable(onClickLabel = item.title, onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = if (compact) 0.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start,
        ) {
            Icon(item.icon(), item.title, tint = if (active) W.lime else W.sidebarInk.copy(alpha = .78f), modifier = Modifier.size(19.dp))
            if (!compact) {
                Spacer(Modifier.width(12.dp))
                Text(item.title, color = if (active) Color.White else W.sidebarInk.copy(alpha = .85f), fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
                if (hovered || active) Text(item.shortcut, color = W.sidebarMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (bar > 0f) Box(
            Modifier.align(Alignment.CenterStart).offset(x = if (compact) (-14).dp else (-16).dp).width(4.dp).height(24.dp * bar)
                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)).background(W.lime),
        )
    }
}

@Composable
private fun NewMovementButton(compact: Boolean, actions: WebActions) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Box {
        Row(
            Modifier.dinexPress(interaction).then(if (compact) Modifier.size(48.dp) else Modifier.fillMaxWidth().height(46.dp))
                .clip(RoundedCornerShape(13.dp)).background(Brush.horizontalGradient(listOf(W.lime, Color(0xFFA9E35C))))
                .clickable(interaction, LocalIndication.current, onClickLabel = "Nuevo movimiento") { open = true }.pointerHoverIcon(PointerIcon.Hand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(DinexNavigationIcons.Add, "Nuevo movimiento", tint = W.forest, modifier = Modifier.size(18.dp))
            if (!compact) {
                Spacer(Modifier.width(8.dp))
                Text("Nuevo movimiento", color = W.forest, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        DropdownMenu(open, { open = false }, containerColor = W.surface, shape = RoundedCornerShape(16.dp)) {
            MenuEntry("Registrar gasto", DinexNavigationIcons.ArrowDown, W.negative) { open = false; actions.onExpense() }
            MenuEntry("Registrar ingreso", DinexNavigationIcons.ArrowUp, W.positive) { open = false; actions.onIncome() }
            HorizontalDivider(color = W.line, modifier = Modifier.padding(vertical = 4.dp))
            MenuEntry("Escanear comprobante", DinexNavigationIcons.ScanReceipt, W.ink2) { open = false; actions.onScan() }
            MenuEntry("Importar Excel o CSV", DinexNavigationIcons.Swap, W.ink2) { open = false; actions.onImport() }
        }
    }
}

@Composable
private fun MenuEntry(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = W.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
        leadingIcon = {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
            }
        },
        onClick = onClick,
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
    )
}

/** Mini tarjeta con el puntaje de salud financiera (0–100). */
@Composable
private fun SidebarHealth(health: FinancialHealth, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val tint = healthColor(health.score)
    val grow = rememberGrow(health.score, 1200)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color.White.copy(alpha = .05f)).border(1.dp, Color.White.copy(alpha = .06f), shape)
            .clickable(onClickLabel = "Ver salud financiera", onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 5.dp.toPx()
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(Color.White.copy(alpha = .1f), -90f, 360f, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke))
                drawArc(tint, -90f, 360f * health.score / 100f * grow, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text("${(health.score * grow).toInt()}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Salud financiera", color = W.sidebarMuted, fontSize = 11.sp)
            Text(health.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Icon(DinexNavigationIcons.Chevron, null, tint = W.sidebarMuted, modifier = Modifier.size(14.dp))
    }
}

internal fun healthColor(score: Int): Color = when {
    score >= 70 -> Color(0xFF5ED49B)
    score >= 40 -> Color(0xFFF2C46B)
    else -> Color(0xFFF08B7B)
}

// ---------------------------------------------------------------- Encabezado

@Composable
private fun WebTopBar(
    page: WebPage,
    name: String,
    streak: Int,
    darkMode: Boolean,
    photo: String,
    reminders: List<PaymentReminder>,
    actions: WebActions,
    onToggleTheme: () -> Unit,
    onNavigate: (WebPage) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 30.dp, end = 26.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(W.brand))
                Spacer(Modifier.width(8.dp))
                Text(longToday().uppercase(), color = W.ink3, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (page == WebPage.SUMMARY) "Hola, ${name.substringBefore(' ').ifBlank { "de nuevo" }}" else page.title,
                color = W.ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(page.subtitle, color = W.ink2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val level = streakLevel(streak)
        Row(
            Modifier.clip(CircleShape).background(W.surface).border(1.dp, W.line, CircleShape).padding(start = 5.dp, end = 13.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StreakBadge(level, 26.dp, locked = streak == 0)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("$streak ${if (streak == 1) "día" else "días"}", color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp)
                Text("de racha · ${level.name}", color = W.ink3, fontSize = 10.sp, lineHeight = 12.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        RemindersBell(reminders, actions) { onNavigate(WebPage.BUDGET) }
        Spacer(Modifier.width(8.dp))
        IconSquare(if (darkMode) DinexNavigationIcons.Sun else DinexNavigationIcons.Moon, if (darkMode) "Modo claro" else "Modo oscuro", onToggleTheme)
        Spacer(Modifier.width(10.dp))
        ProfilePhoto(name, photo, 40.dp, Modifier.clip(CircleShape).border(2.dp, W.surface, CircleShape)
            .clickable(onClickLabel = "Abrir perfil", onClick = actions.onProfile).pointerHoverIcon(PointerIcon.Hand))
    }
}

@Composable
private fun RemindersBell(reminders: List<PaymentReminder>, actions: WebActions, onBudget: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconSquare(DinexNavigationIcons.Bell, "Próximos pagos") { open = true }
        if (reminders.isNotEmpty()) Box(
            Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(18.dp).clip(CircleShape).background(W.negative).border(2.dp, W.canvas, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("${reminders.size.coerceAtMost(9)}", color = Color.White, fontSize = 9.sp, lineHeight = 9.sp,
            textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-1).dp)) }
        DropdownMenu(open, { open = false }, containerColor = W.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.width(320.dp)) {
            Text("Próximos pagos", color = W.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            if (reminders.isEmpty()) Text("No tienes pagos pendientes.", color = W.ink3, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            reminders.sortedBy { it.date }.take(5).forEach { reminder ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(reminder.title, color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(reminder.date, color = W.ink3, fontSize = 11.sp)
                            }
                            Text(moneyGrouped(reminder.amount), color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    leadingIcon = { Icon(DinexNavigationIcons.Bell, null, tint = W.warning, modifier = Modifier.size(16.dp)) },
                    onClick = { open = false; actions.onResolveReminder(reminder) },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
            }
            HorizontalDivider(color = W.line, modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("Agregar recordatorio", color = W.brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(DinexNavigationIcons.Add, null, tint = W.brand, modifier = Modifier.size(16.dp)) },
                onClick = { open = false; actions.onAddReminder() },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            )
            DropdownMenuItem(
                text = { Text("Ver presupuesto", color = W.ink2, fontSize = 13.sp) },
                leadingIcon = { Icon(DinexNavigationIcons.Plan, null, tint = W.ink3, modifier = Modifier.size(16.dp)) },
                onClick = { open = false; onBudget() },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            )
        }
    }
}

// ---------------------------------------------------------------- Buscador de comandos (Ctrl K)

internal class WebCommand(val group: String, val label: String, val hint: String, val icon: ImageVector, val keywords: String = "", val run: () -> Unit)

private fun webCommands(darkMode: Boolean, actions: WebActions, toggleTheme: () -> Unit, go: (WebPage) -> Unit): List<WebCommand> =
    WebPage.entries.map { WebCommand("Ir a", it.title, it.subtitle, it.icon(), "pagina ${it.shortcut}") { go(it) } } + listOf(
        WebCommand("Acciones", "Registrar gasto", "Anota lo que acabas de pagar", DinexNavigationIcons.ArrowDown, "nuevo compra pagar", actions.onExpense),
        WebCommand("Acciones", "Registrar ingreso", "Sueldo, ventas o pagos recibidos", DinexNavigationIcons.ArrowUp, "nuevo cobro sueldo", actions.onIncome),
        WebCommand("Acciones", "Ahorrar en una meta", "Separa dinero para tus metas", DinexNavigationIcons.Target, "meta guardar") { actions.onSave(null) },
        WebCommand("Ir a", "Dinex YUPAY", "Mi alcancía inteligente", DinexNavigationIcons.Target, "alcancia monedas esp32", actions.onYupay),
        WebCommand("Acciones", "Agregar recordatorio de pago", "DINEX te avisa antes de pagar", DinexNavigationIcons.Bell, "pago recibo", actions.onAddReminder),
        WebCommand("Acciones", "Escanear comprobante", "Lee el monto desde una foto", DinexNavigationIcons.ScanReceipt, "foto boleta factura", actions.onScan),
        WebCommand("Acciones", "Importar Excel o CSV", "Trae movimientos de tu banco", DinexNavigationIcons.Swap, "archivo banco", actions.onImport),
        WebCommand("Acciones", "Exportar reporte", "PDF, Excel o CSV", DinexNavigationIcons.Send, "descargar pdf excel", actions.onExport),
        WebCommand("Preferencias", if (darkMode) "Cambiar a modo claro" else "Cambiar a modo oscuro", "Tema de la web", if (darkMode) DinexNavigationIcons.Sun else DinexNavigationIcons.Moon, "tema oscuro claro", toggleTheme),
        WebCommand("Preferencias", "Abrir mi perfil", "Cuenta, idioma y ajustes", DinexNavigationIcons.Settings, "cuenta ajustes perfil", actions.onProfile),
    )

private fun String.normalized(): String = lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u').replace('ñ', 'n')

@Composable
private fun CommandPalette(commands: List<WebCommand>, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var index by remember { mutableIntStateOf(0) }
    val filtered = remember(query, commands) {
        val q = query.trim().normalized()
        if (q.isEmpty()) commands else commands.filter { cmd -> q.split(' ').all { word -> "${cmd.label} ${cmd.hint} ${cmd.keywords} ${cmd.group}".normalized().contains(word) } }
    }
    LaunchedEffect(filtered.size) { index = index.coerceIn(0, (filtered.size - 1).coerceAtLeast(0)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val listState = rememberLazyListState()
    LaunchedEffect(index) { if (filtered.isNotEmpty()) listState.animateScrollToItem(index) }
    fun run(cmd: WebCommand) { onClose(); cmd.run() }

    Box(
        Modifier.fillMaxSize().background(Color(0xFF050B08).copy(alpha = .55f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClose),
        contentAlignment = Alignment.TopCenter,
    ) {
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier.padding(top = 90.dp).widthIn(max = 640.dp).fillMaxWidth(.9f)
                .shadow(40.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape).background(W.surface).border(1.dp, W.line, shape)
                .clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(DinexNavigationIcons.Search, null, tint = W.brand, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("¿Qué quieres hacer? Escribe una página o una acción…", color = W.ink3, fontSize = 16.sp)
                    BasicTextField(
                        query, { query = it.take(60); index = 0 }, singleLine = true,
                        textStyle = TextStyle(color = W.ink, fontSize = 16.sp, fontFamily = MaterialTheme.typography.bodyMedium.fontFamily),
                        cursorBrush = SolidColor(W.brand),
                        // En la web, Enter llega como acción del teclado y no como tecla: se atienden las dos.
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Go),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { filtered.getOrNull(index)?.let(::run) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> { if (filtered.isNotEmpty()) index = (index + 1) % filtered.size; true }
                                Key.DirectionUp -> { if (filtered.isNotEmpty()) index = (index - 1 + filtered.size) % filtered.size; true }
                                Key.Enter, Key.NumPadEnter -> { filtered.getOrNull(index)?.let(::run); true }
                                Key.Escape -> { onClose(); true }
                                else -> false
                            }
                        },
                    )
                }
                Kbd("Esc")
            }
            HorizontalDivider(color = W.line)
            if (filtered.isEmpty()) {
                Text("No encontramos “$query”. Prueba con “gasto”, “metas” o “exportar”.", color = W.ink3, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 28.dp))
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp), state = listState, contentPadding = PaddingValues(8.dp)) {
                    itemsIndexed(filtered) { i, cmd ->
                        if (i == 0 || filtered[i - 1].group != cmd.group) {
                            Text(cmd.group.uppercase(), color = W.ink3, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp,
                                modifier = Modifier.padding(start = 12.dp, top = if (i == 0) 6.dp else 12.dp, bottom = 6.dp))
                        }
                        val selected = i == index
                        val bg by animateColorAsState(if (selected) W.brandSoft else Color.Transparent, tween(120), label = "comando")
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg)
                                .clickable { run(cmd) }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (selected) W.brand else W.surfaceAlt), contentAlignment = Alignment.Center) {
                                Icon(cmd.icon, null, tint = if (selected) Color.White else W.ink2, modifier = Modifier.size(17.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(cmd.label, color = W.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(cmd.hint, color = W.ink3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (selected) Icon(DinexNavigationIcons.Chevron, null, tint = W.brand, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
            HorizontalDivider(color = W.line)
            Row(Modifier.fillMaxWidth().background(W.surfaceAlt).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Kbd("↑"); Spacer(Modifier.width(3.dp)); Kbd("↓"); Spacer(Modifier.width(6.dp)); Text("moverte", color = W.ink3, fontSize = 12.sp) }
                Row(verticalAlignment = Alignment.CenterVertically) { Kbd("Enter"); Spacer(Modifier.width(6.dp)); Text("abrir", color = W.ink3, fontSize = 12.sp) }
                Spacer(Modifier.weight(1f))
                Text("${filtered.size} resultados", color = W.ink3, fontSize = 12.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- Piezas reutilizables

@Composable
internal fun Kbd(text: String, dark: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        text, color = if (dark) W.sidebarInk.copy(alpha = .7f) else W.ink2, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(shape).background(if (dark) Color.White.copy(alpha = .07f) else W.surfaceAlt)
            .border(1.dp, if (dark) Color.White.copy(alpha = .1f) else W.line, shape).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Contenedor de página: centrado, con ancho máximo para que nada se estire de lado a lado. */
@Composable
internal fun PageFrame(scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 1280.dp).fillMaxWidth().fillMaxHeight()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(start = 30.dp, end = 26.dp, top = 14.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

/** Tarjeta base: se eleva un poco y su borde se tiñe de verde al pasar el mouse. */
@Composable
internal fun WCard(modifier: Modifier = Modifier, padding: Dp = 22.dp, content: @Composable ColumnScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val reduced = LocalDinexReducedMotion.current
    val lift by animateFloatAsState(if (hovered && !reduced) 1f else 0f, tween(200), label = "tarjeta elevada")
    Column(
        modifier.dinexEntrance().graphicsLayer { translationY = -lift * 3.dp.toPx() }
            .shadow((2 + lift * 16).dp, WebCardShape, ambientColor = W.forest.copy(alpha = .12f), spotColor = W.forest.copy(alpha = .12f))
            .hoverable(interaction).clip(WebCardShape).background(W.surface)
            .border(1.dp, lerp(W.line, W.brand, lift * .4f), WebCardShape).padding(padding),
        content = content,
    )
}

@Composable
internal fun CardTitle(title: String, subtitle: String? = null, icon: ImageVector? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        icon?.let {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(W.brandSoft), contentAlignment = Alignment.Center) {
                Icon(it, null, tint = W.brand, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = W.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
            subtitle?.let { Text(it, color = W.ink3, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        action?.invoke()
    }
}

@Composable
internal fun LinkButton(text: String, color: Color = W.brand, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(9.dp)).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Icon(DinexNavigationIcons.Chevron, null, tint = color, modifier = Modifier.size(14.dp))
    }
}

@Composable
internal fun WButton(text: String, icon: ImageVector? = null, primary: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = when {
        primary && hovered -> lerp(W.brand, Color.Black, .12f)
        primary -> W.brand
        hovered -> W.hover
        else -> W.surface
    }
    Row(
        Modifier.dinexPress(interaction).height(40.dp).clip(shape).background(bg)
            .then(if (primary) Modifier else Modifier.border(1.dp, W.line, shape))
            .hoverable(interaction).clickable(interaction, LocalIndication.current, onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = if (primary) Color.White else W.ink2, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = if (primary) Color.White else W.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun IconSquare(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier.size(40.dp).clip(shape).background(if (hovered) W.hover else W.surface).border(1.dp, W.line, shape)
            .hoverable(interaction).clickable(onClickLabel = label, onClick = onClick).pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (hovered) W.brand else W.ink2, modifier = Modifier.size(18.dp)) }
}

/** Selector de opciones con una pastilla que se desliza a la opción elegida. */
@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(W.surfaceAlt).border(1.dp, W.line, RoundedCornerShape(12.dp)).padding(3.dp)) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            val bg by animateColorAsState(if (active) W.surface else Color.Transparent, tween(180), label = "segmento")
            Box(
                Modifier.fillMaxHeight().then(if (active) Modifier.shadow(2.dp, RoundedCornerShape(9.dp)) else Modifier).clip(RoundedCornerShape(9.dp)).background(bg)
                    .clickable { onSelect(index) }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (active) W.ink else W.ink2, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium) }
        }
    }
}

@Composable
internal fun SelectMenu(label: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val current = options.firstOrNull { it.first == value }?.second ?: value
    Box {
        Row(
            Modifier.height(40.dp).clip(shape).background(W.surface).border(1.dp, if (open) W.brand else W.line, shape).clickable { open = true }
                .pointerHoverIcon(PointerIcon.Hand).padding(start = 13.dp, end = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = W.ink3, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text(current, color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
            Spacer(Modifier.width(4.dp))
            Icon(DinexNavigationIcons.Chevron, null, tint = W.ink3, modifier = Modifier.size(14.dp).rotate(if (open) -90f else 90f))
        }
        DropdownMenu(open, { open = false }, containerColor = W.surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(max = 360.dp)) {
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
internal fun ThinBar(progress: Float, color: Color, height: Dp = 6.dp, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(if (LocalDinexReducedMotion.current) 0 else 700, easing = FastOutSlowInEasing), label = "barra")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(W.line.copy(alpha = .7f))) {
        if (progress > 0f) Box(
            Modifier.fillMaxWidth(max(animated, .015f)).fillMaxHeight().clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = .7f), color))),
        )
    }
}

@Composable
internal fun CategoryIcon(category: String, income: Boolean, size: Dp = 38.dp) {
    val tint = if (income) W.positive else categoryColor(category)
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .32f)).background(tint.copy(alpha = if (DinexThemeState.dark) .2f else .12f)), contentAlignment = Alignment.Center) {
        Icon(if (income) DinexNavigationIcons.ArrowUp else DinexCategoryIcons.forCategory(category), null, tint = tint, modifier = Modifier.size(size * .5f))
    }
}

@Composable
internal fun EmptyNote(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(W.brandSoft), contentAlignment = Alignment.Center) {
            Icon(DinexNavigationIcons.Sparkle, null, tint = W.brand, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = W.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = W.ink3, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (action != null && onAction != null) { Spacer(Modifier.height(14.dp)); WButton(action, primary = true, onClick = onAction) }
    }
}

/** Chip que muestra cuánto cambió un valor; [goodWhenDown] pinta de verde las bajas (gastos). */
@Composable
internal fun DeltaChip(change: Double?, goodWhenDown: Boolean = false, onDark: Boolean = false) {
    if (change == null) {
        Text("Sin mes previo", color = if (onDark) W.sidebarMuted else W.ink3, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        return
    }
    val good = if (goodWhenDown) change <= 0 else change >= 0
    val tint = if (onDark) (if (good) W.mint else W.coral) else (if (good) W.positive else W.negative)
    Row(
        Modifier.clip(CircleShape).background(tint.copy(alpha = .14f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (change >= 0) DinexNavigationIcons.ArrowUp else DinexNavigationIcons.ArrowDown, null, tint = tint, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(3.dp))
        Text("${fixed(kotlin.math.abs(change), if (kotlin.math.abs(change) < 10) 1 else 0)}%", color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Va de 0 a 1 al aparecer: hace crecer gráficos y barras. */
@Composable
internal fun rememberGrow(key: Any? = Unit, durationMillis: Int = 900): Float {
    val reduced = LocalDinexReducedMotion.current
    val grow = remember(key) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(key) { grow.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing)) }
    return grow.value
}

/**
 * Dos columnas en pantallas anchas; una sola columna cuando no alcanza el espacio.
 * Con [equalHeight], ambas columnas miden lo mismo y la tarjeta que recibe `stretch`
 * se estira para que no queden huecos en blanco.
 */
@Composable
internal fun Columns(
    ratio: Float = 1.6f,
    gap: Dp = 20.dp,
    equalHeight: Boolean = true,
    left: @Composable ColumnScope.(stretch: Modifier) -> Unit,
    right: @Composable ColumnScope.(stretch: Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 860.dp) {
            Row(
                Modifier.fillMaxWidth().then(if (equalHeight) Modifier.height(IntrinsicSize.Max) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(ratio).then(if (equalHeight) Modifier.fillMaxHeight() else Modifier), verticalArrangement = Arrangement.spacedBy(gap)) {
                    left(if (equalHeight) Modifier.weight(1f) else Modifier)
                }
                Column(Modifier.weight(1f).then(if (equalHeight) Modifier.fillMaxHeight() else Modifier), verticalArrangement = Arrangement.spacedBy(gap)) {
                    right(if (equalHeight) Modifier.weight(1f) else Modifier)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) { left(Modifier); right(Modifier) }
        }
    }
}

/** Fila de tarjetas del mismo ancho que pasa a columna cuando no hay espacio. */
@Composable
internal fun TileRow(minWidth: Dp = 760.dp, tiles: List<@Composable (Modifier) -> Unit>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= minWidth) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            tiles.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
        } else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            tiles.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun Legend(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = if (dashed) 14.dp else 10.dp, height = if (dashed) 2.dp else 10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = W.ink3, fontSize = 12.sp)
    }
}

/** Valor grande con etiqueta, para franjas de resumen. */
@Composable
internal fun StripStat(label: String, value: String, tint: Color, modifier: Modifier, extra: (@Composable () -> Unit)? = null) {
    Column(modifier.padding(horizontal = 20.dp, vertical = 15.dp)) {
        Text(label, color = W.ink3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(value, color = tint, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.3).sp)
        extra?.let { Spacer(Modifier.height(5.dp)); it() }
    }
}

@Composable
internal fun StripDivider() { Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 14.dp).background(W.line)) }

/** Franja horizontal de datos dentro de una tarjeta. */
@Composable
internal fun StatStrip(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().dinexEntrance().clip(WebCardShape).background(W.surface).border(1.dp, W.line, WebCardShape).height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun TableHead(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(text.uppercase(), color = W.ink3, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .9.sp, modifier = modifier, textAlign = align)
}

/** Anillo de progreso simple (metas, presupuestos). */
@Composable
internal fun RingProgress(progress: Float, color: Color, size: Dp, stroke: Dp = 8.dp, track: Color = W.line, content: @Composable BoxScope.() -> Unit = {}) {
    val grow = rememberGrow(progress, 1000)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arc = Size(this.size.width - s, this.size.height - s)
            drawArc(track, -90f, 360f, false, Offset(s / 2, s / 2), arc, style = Stroke(s))
            val sweep = 360f * progress.coerceIn(0f, 1f) * grow
            if (sweep > 0f) drawArc(color, -90f, sweep, false, Offset(s / 2, s / 2), arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}
