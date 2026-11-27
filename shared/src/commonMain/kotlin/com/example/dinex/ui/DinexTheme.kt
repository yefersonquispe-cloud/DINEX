package com.example.dinex

import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Tokens reactivos compartidos por Android, iOS y web. Los gráficos conservan
// sus propios colores para que las categorías sigan siendo distinguibles.
internal object DinexThemeState { var dark by mutableStateOf(false) }
fun isDinexDarkModeEnabled(): Boolean = DinexRepository().darkMode()

/** Opción "Reducir movimiento" de Dinex, para las vistas nativas de Android (burbuja de voz). */
fun isDinexReducedMotionEnabled(): Boolean = DinexRepository().reduceMotion()
// Modo oscuro: fondos gris carbón neutros (no verdosos); el verde queda para botones y detalles.
internal val AppBg get() = if (DinexThemeState.dark) Color(0xFF1C1F23) else Color(0xFFF7FAF7)
internal val Night get() = if (DinexThemeState.dark) Color(0xFFF2F4F6) else Color(0xFF183326)
internal val NightSoft get() = if (DinexThemeState.dark) Color(0xFFC8CFD5) else Color(0xFF365442)
internal val Green get() = Color(0xFF176B43)
internal val GreenDark get() = if (DinexThemeState.dark) Color(0xFF9CE3B5) else Color(0xFF115B38)
internal val Mint get() = if (DinexThemeState.dark) Color(0xFF283630) else Color(0xFFE5F3E9)
internal val Lime get() = if (DinexThemeState.dark) Color(0xFFA8DA99) else Color(0xFF438A53)
internal val Card get() = if (DinexThemeState.dark) Color(0xFF262A2F) else Color(0xFFFFFFFF)
internal val CardRaised get() = if (DinexThemeState.dark) Color(0xFF2F3439) else Color(0xFFF0F7F1)
internal val SurfaceHover get() = if (DinexThemeState.dark) Color(0xFF383D43) else Color(0xFFE7F1E9)
internal val Red get() = if (DinexThemeState.dark) Color(0xFFEF9CA5) else Color(0xFFB84754)
internal val Orange get() = if (DinexThemeState.dark) Color(0xFFEAC081) else Color(0xFFA66822)
internal val Indigo get() = if (DinexThemeState.dark) Color(0xFFB7A6E6) else Color(0xFF6959A5)
internal val ElectricBlue get() = if (DinexThemeState.dark) Color(0xFF9AC8DD) else Color(0xFF326D91)
internal val Gray get() = if (DinexThemeState.dark) Color(0xFFA9B2B9) else Color(0xFF536A5A)
internal val Border get() = if (DinexThemeState.dark) Color(0xFF3B4249) else Color(0xFFD4E4D8)

/** Tarjeta principal (saldo, mercado, consejos): verde en modo claro, azul pizarra más claro en oscuro. */
internal val HeroGradient: List<Color> get() = if (DinexThemeState.dark)
    listOf(Color(0xFF2E3D4C), Color(0xFF3B4E60), Color(0xFF4B6176))
else listOf(Color(0xFF0E5431), Color(0xFF176B43), Color(0xFF328859))
internal val HeroBase get() = if (DinexThemeState.dark) Color(0xFF3B4E60) else Color(0xFF176B43)
/** Cajitas de ingresos y gastos dentro de la tarjeta principal. */
internal val HeroInset get() = if (DinexThemeState.dark) Color(0xFF1C2630) else Color(0xFF082B1D)

// Las medallas conservan su material; sus rótulos se oscurecen sobre blanco.
internal fun readableAccent(color: Color): Color = lerp(color, Night, .48f)

internal val RadiusSm = 12.dp
internal val RadiusMd = 18.dp
internal val RadiusLg = 24.dp
internal val DesktopBreakpoint = 900.dp
internal val LocalDinexReducedMotion = staticCompositionLocalOf { false }

internal val dinexTypography = Typography(
    // Leave color unspecified: Material 3 supplies the correct contrasting
    // content color for buttons, dialogs, fields and disabled states.
    displayLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 32.sp, letterSpacing = (-0.4).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 12.sp),
)

internal val dinexShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(RadiusSm),
    medium = RoundedCornerShape(RadiusMd),
    large = RoundedCornerShape(RadiusLg),
    extraLarge = RoundedCornerShape(26.dp),
)

internal fun currentDateLabel(): String {
    val date = civilDateFromEpochDays()
    return "${date.day} ${currentMonthName(date.month)} ${date.year}"
}

@Composable
internal fun DinexCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    accent: Color = Border,
    onClick: (() -> Unit)? = null,
    vibrant: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered && onClick != null) SurfaceHover else if (elevated) CardRaised else Card, tween(if (LocalDinexReducedMotion.current) 0 else 160))
    Surface(
        modifier = modifier.dinexEntrance().dinexPress(interaction, .985f).then(
            if (onClick != null) Modifier.hoverable(interaction).clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick) else Modifier
        ),
        shape = RoundedCornerShape(RadiusLg),
        color = Color.Transparent,
        // Modo oscuro: borde sólido y apenas teñido para que la tarjeta se separe del fondo sin verse verdosa.
        border = BorderStroke(1.dp, if (DinexThemeState.dark) lerp(Border, accent, .18f) else accent.copy(alpha = .22f)),
        shadowElevation = if (elevated) 8.dp else 0.dp,
    ) {
        val tint = when {
            DinexThemeState.dark && vibrant -> .035f
            DinexThemeState.dark -> .012f
            vibrant -> .06f
            else -> .02f
        }
        Column(Modifier.fillMaxWidth().background(lerp(bg, accent, tint)).padding(if (vibrant) 22.dp else 18.dp), content = content)
    }
}

@Composable
internal fun SectionHeader(title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t(title), color = Night, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (subtitle != null) Text(t(subtitle), color = Gray, fontSize = 11.sp)
        }
        if (action != null && onAction != null) {
            Text(
                t(action),
                color = GreenDark,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).wrapContentHeight().padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
internal fun DinexProgress(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 7.dp) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(if (LocalDinexReducedMotion.current) 0 else 520))
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier.fillMaxWidth().height(height).clip(CircleShape),
        color = color,
        trackColor = color.copy(alpha = .12f),
    )
}

@Composable
internal fun AmountText(amount: Double, income: Boolean, size: Int = 13) {
    Text(
        (if (income) "+ " else "− ") + money(amount),
        color = if (income) GreenDark else Red,
        fontWeight = FontWeight.Bold,
        fontSize = size.sp,
    )
}

@Composable
internal fun CategoryMark(code: String, tint: Color, size: Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(RadiusSm)).background(tint.copy(alpha = .12f)),
        contentAlignment = Alignment.Center,
    ) {
        val icon = when (code) {
            "IN" -> DinexNavigationIcons.ArrowUp
            "TR" -> DinexNavigationIcons.Movements
            "ES" -> DinexNavigationIcons.Plan
            "OC" -> DinexNavigationIcons.Sparkle
            else -> DinexNavigationIcons.Wallet
        }
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * .52f))
    }
}

/** Marca del método de pago en la fila de movimiento. */
@Composable
internal fun MethodMark(method: String, tint: Color, size: Dp = 26.dp) {
    if (isBrandPayment(method)) {
        // Mismo cuadro y mismo tamaño visible que los demás métodos: el logo va pequeño sobre un fondo suave.
        val brand = if (method == "Yape") Color(0xFF742284) else Color(0xFF1C74E8)
        Box(
            Modifier.size(size).clip(RoundedCornerShape(RadiusSm)).background(brand.copy(alpha = .12f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(size * .62f).clip(RoundedCornerShape(size * .16f))) { PaymentBrandBadge(method, size * .62f) }
        }
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(RadiusSm)).background(tint.copy(alpha = .12f)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                DinexPaymentIcons.forMethod(method),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * .5f),
            )
        }
    }
}

@Composable
internal fun DinexEmptyState(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(CardRaised),
            contentAlignment = Alignment.Center,
        ) { androidx.compose.material3.Icon(DinexNavigationIcons.Wallet, null, tint = Green, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(12.dp))
        Text(title, color = Night, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, color = Gray, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier.clickable(onClick = onAction),
                shape = RoundedCornerShape(RadiusMd),
                color = Green.copy(alpha = .14f),
                border = BorderStroke(1.dp, Green.copy(alpha = .35f)),
            ) {
                Text(action, color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

internal fun movementTint(movement: Movement): Color {
    val info = categories.firstOrNull { it.name == movement.category }
    return if (movement.income) Green else info?.color ?: Gray
}

internal fun movementCode(movement: Movement): String {
    val info = categories.firstOrNull { it.name == movement.category }
    return if (movement.income) "IN" else info?.code ?: "GT"
}

internal val colors get() = if (DinexThemeState.dark) darkColorScheme(
    primary = GreenDark,
    onPrimary = Color(0xFF103C27),
    primaryContainer = Mint,
    onPrimaryContainer = GreenDark,
    secondary = Indigo,
    onSecondary = Color(0xFF2A2044),
    secondaryContainer = CardRaised,
    onSecondaryContainer = Indigo,
    tertiary = Orange,
    onTertiary = Color(0xFF4A3019),
    tertiaryContainer = CardRaised,
    onTertiaryContainer = Orange,
    background = AppBg,
    onBackground = Night,
    surface = Card,
    onSurface = Night,
    surfaceVariant = CardRaised,
    onSurfaceVariant = Gray,
    // Dialogs, menus and sheets use these tones; without them Material falls back to its lilac defaults.
    surfaceContainerLowest = Card,
    surfaceContainerLow = Card,
    surfaceContainer = Card,
    surfaceContainerHigh = Card,
    surfaceContainerHighest = CardRaised,
    outline = Border,
    error = Red,
    onError = Color(0xFF3E1821),
) else lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = GreenDark,
    secondary = Indigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9F7),
    onSecondaryContainer = Color(0xFF3D316E),
    tertiary = Orange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBD4),
    onTertiaryContainer = Color(0xFF704111),
    background = AppBg,
    onBackground = Night,
    surface = Card,
    onSurface = Night,
    surfaceVariant = CardRaised,
    onSurfaceVariant = Gray,
    // Dialogs, menus and sheets use these tones; without them Material falls back to its lilac defaults.
    surfaceContainerLowest = Card,
    surfaceContainerLow = Card,
    surfaceContainer = Card,
    surfaceContainerHigh = Card,
    surfaceContainerHighest = CardRaised,
    outline = Border,
    error = Red,
    onError = Color.White,
)
