package com.example.dinex

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Muted mineral palette: no fluorescent accents. Shared by Android, iOS and web.
internal val AppBg = Color(0xFF151C22)
internal val Night = Color(0xFFF2EEE7)
internal val NightSoft = Color(0xFF46535E)
internal val Green = Color(0xFF9ABFAF)
internal val GreenDark = Color(0xFFBED6CB)
internal val Mint = Color(0xFF314B43)
internal val Lime = Color(0xFFB9C6A1)
internal val Card = Color(0xFF202A32)
internal val CardRaised = Color(0xFF2A363F)
internal val SurfaceHover = Color(0xFF37464F)
internal val Red = Color(0xFFE0AAA1)
internal val Orange = Color(0xFFD9BE93)
internal val Indigo = Color(0xFFB6B3C9)
internal val ElectricBlue = Color(0xFFA6BDC9)
internal val Gray = Color(0xFFBAC3C8)
internal val Border = Color(0xFF46535D)

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
        modifier = modifier.dinexEntrance().then(
            if (onClick != null) Modifier.hoverable(interaction).clickable(onClick = onClick) else Modifier
        ),
        shape = RoundedCornerShape(RadiusLg),
        color = Color.Transparent,
        border = BorderStroke(1.dp, accent.copy(alpha = .22f)),
        shadowElevation = if (elevated) 8.dp else 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().background(lerp(bg, accent, if (vibrant) .06f else .02f)).padding(if (vibrant) 22.dp else 18.dp), content = content)
    }
}

@Composable
internal fun SectionHeader(title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Night, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = Gray, fontSize = 11.sp)
        }
        if (action != null && onAction != null) {
            Text(
                action,
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
