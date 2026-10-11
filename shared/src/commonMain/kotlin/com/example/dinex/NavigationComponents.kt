package com.example.dinex

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dinex.shared.generated.resources.Res
import dinex.shared.generated.resources.dinex_app_icon
import dinex.shared.generated.resources.dinex_mascot_ai
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ValidationHint(label: String, valid: Boolean) {
    Text(
        "${if (valid) "Cumple:" else "Requisito:"} $label",
        color = if (valid) Lime else Gray,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
internal fun DinexAppShell(
    page: Int,
    onSelectPage: (Int) -> Unit,
    onAdd: () -> Unit,
    onProfile: () -> Unit,
    onAssistant: () -> Unit,
    streak: Int,
    name: String,
    email: String,
    content: @Composable (PaddingValues) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(AppBg)) {
        DinexAuroraBackground()
        val wide = maxWidth >= DesktopBreakpoint
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                DinexSidebar(
                    page = page,
                    onSelectPage = onSelectPage,
                    onProfile = onProfile,
                    onAssistant = onAssistant,
                    name = name,
                    email = email,
                )
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    DinexDesktopTopBar(
                        streak = streak,
                        name = name,
                        email = email,
                        onProfile = onProfile,
                        onAdd = onAdd,
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        content(PaddingValues(0.dp))
                    }
                }
            }
            if (page != 4) {
                Box(Modifier.fillMaxSize().padding(end = 24.dp, bottom = 24.dp), contentAlignment = Alignment.BottomEnd) {
                    DinexAssistantBubble(onClick = onAssistant)
                }
            }
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = { DinexTopBar(streak = streak, name = name, email = email, onProfile = onProfile) },
                bottomBar = { BottomNavigation(page, onSelect = onSelectPage, onAdd = onAdd) },
                floatingActionButton = { if (page != 4) DinexAssistantBubble(onClick = onAssistant) },
                floatingActionButtonPosition = FabPosition.End,
            ) { padding ->
                content(padding)
            }
        }
    }
}

@Composable
internal fun DinexSidebar(
    page: Int,
    onSelectPage: (Int) -> Unit,
    onProfile: () -> Unit,
    onAssistant: () -> Unit,
    name: String,
    email: String,
) {
    Surface(
        modifier = Modifier.fillMaxHeight().width(228.dp)
            .background(Brush.linearGradient(listOf(Color(0xF01B2B4A), Color(0xF0101B35), Card.copy(alpha = .94f)))),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .16f), NavigationAqua.copy(alpha = .08f), Color.White.copy(alpha = .03f)))),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(Res.drawable.dinex_app_icon),
                    contentDescription = "Logo Dinex",
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("DINEX", color = Night, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.2.sp)
                    Text("Decide mejor. Ahorra más.", color = Gray, fontSize = 9.sp)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("MENÚ", color = Gray, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(8.dp))
            SidebarItem("Inicio", "IN", page == 0) { onSelectPage(0) }
            SidebarItem("Movimientos", "MO", page == 1) { onSelectPage(1) }
            SidebarItem("Plan", "PL", page == 2) { onSelectPage(2) }
            SidebarItem("Análisis", "AN", page == 3) { onSelectPage(3) }
            SidebarItem("Asistente", "IA", page == 4) { onAssistant() }
            Spacer(Modifier.height(10.dp))
            SidebarItem("Configuración", "CF", false) { onProfile() }
            Spacer(Modifier.weight(1f))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onProfile),
                shape = RoundedCornerShape(RadiusMd),
                color = CardRaised,
                border = BorderStroke(1.dp, Border),
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Indigo, ElectricBlue))),
                        contentAlignment = Alignment.Center,
                    ) { Text(name.firstOrNull()?.uppercase() ?: "D", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp) }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, color = Night, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(email, color = Gray, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SidebarItem(label: String, code: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when {
            selected -> Green.copy(alpha = .12f)
            hovered -> SurfaceHover
            else -> Color.Transparent
        },
        tween(if (LocalDinexReducedMotion.current) 0 else 140),
    )
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusSm)).background(bg)
            .hoverable(interaction).selectable(selected = selected, role = Role.Tab, onClick = onClick).padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = DinexNavigationIcons.forCode(code)
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) NavigationAqua else NavigationMuted, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
        } else {
            Text(code, color = if (selected) GreenDark else Gray, fontWeight = FontWeight.Black, fontSize = 9.sp, modifier = Modifier.width(36.dp))
        }
        Text(label, color = if (selected) Night else Gray, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
internal fun DinexDesktopTopBar(streak: Int, name: String, email: String, onProfile: () -> Unit, onAdd: () -> Unit) {
    val level = streakLevel(streak)
    Surface(color = AppBg.copy(alpha = .85f), border = BorderStroke(1.dp, Border.copy(alpha = .4f))) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Hola, ${name.substringBefore(' ')}", color = Night, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(currentDateLabel(), color = Gray, fontSize = 11.sp)
            }
            Surface(shape = RoundedCornerShape(20.dp), color = level.color.copy(alpha = .12f), border = BorderStroke(1.dp, level.color.copy(alpha = .32f))) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniWalletMedal(level.color, 22.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("$streak d · ${level.name}", color = level.color, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onAdd,
                shape = RoundedCornerShape(RadiusMd),
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color(0xFF062018)),
            ) { Text("+ Nuevo", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Indigo, ElectricBlue))).clickable(onClick = onProfile),
                contentAlignment = Alignment.Center,
            ) { Text(name.firstOrNull()?.uppercase() ?: email.firstOrNull()?.uppercase() ?: "D", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp) }
        }
    }
}

@Composable
internal fun DinexAssistantBubble(onClick: () -> Unit) {
    val pulse: State<Float> = if (LocalDinexReducedMotion.current) remember { mutableStateOf(1f) }
    else rememberInfiniteTransition(label = "assistant invitation").animateFloat(1f, 1.07f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "halo")
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(63.dp).graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
            .clip(CircleShape).background(Brush.linearGradient(listOf(Indigo.copy(alpha = .65f), Green.copy(alpha = .4f)))))
        Surface(
            modifier = Modifier.size(58.dp).clickable(onClick = onClick),
            shape = CircleShape,
            color = Color(0xFF182746),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .30f)),
            shadowElevation = 12.dp,
        ) {
            Image(
                painter = painterResource(Res.drawable.dinex_mascot_ai),
                contentDescription = "Asistente Dinex IA",
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Box(
            Modifier.align(Alignment.BottomEnd).offset((-4).dp, (-4).dp).size(11.dp)
                .clip(CircleShape).background(AppBg).padding(2.dp).clip(CircleShape).background(Lime)
        )
    }
}

@Composable
internal fun DinexTopBar(streak: Int, name: String, email: String, onProfile: () -> Unit) {
    val level = streakLevel(streak)
    Surface(modifier = Modifier.statusBarsPadding(), color = AppBg.copy(alpha = .88f)) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(Res.drawable.dinex_app_icon),
                contentDescription = "Logo Dinex",
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("DINEX", color = Night, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.2.sp)
                Text(currentDateLabel(), color = Gray, fontSize = 10.sp)
            }
            Surface(shape = RoundedCornerShape(16.dp), color = level.color.copy(alpha = .12f), border = BorderStroke(1.dp, level.color.copy(alpha = .32f))) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniWalletMedal(level.color, 22.dp)
                    Spacer(Modifier.width(5.dp))
                    Text("$streak", color = level.color, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(CardRaised).clickable(onClickLabel = "Abrir perfil y accesibilidad", onClick = onProfile),
                contentAlignment = Alignment.Center,
            ) { Text(name.firstOrNull()?.uppercase() ?: email.firstOrNull()?.uppercase() ?: "D", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp) }
        }
    }
}

@Composable
internal fun ProfileDialog(
    name: String,
    email: String,
    streak: Int,
    savings: Double,
    movements: List<Movement>,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    reduceMotion: Boolean,
    onReduceMotion: (Boolean) -> Unit,
    largeText: Boolean,
    onLargeText: (Boolean) -> Unit,
    onAutomation: () -> Unit,
) {
    val income = movements.filter { it.income }.sumOf { it.amount }
    val expenses = movements.filterNot { it.income }.sumOf { it.amount }
    val level = streakLevel(streak)
    val next = nextStreakLevel(streak)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Card,
        icon = { Image(painterResource(Res.drawable.dinex_mascot_ai), "Mascota Dinex", Modifier.size(72.dp), contentScale = ContentScale.Fit) },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Night)
                Text(email, color = Gray, fontSize = 13.sp)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(RadiusMd), color = level.color.copy(alpha = .12f), border = BorderStroke(1.dp, level.color.copy(alpha = .28f))) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        MiniWalletMedal(level.color, 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Nivel ${level.name}", color = level.color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(if (next == null) "Completaste los 365 días" else "${next.startsAt - streak} días para ${next.name}", color = Gray, fontSize = 10.sp)
                        }
                        Text("$streak", color = Night, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileStat("SALDO", money(income - expenses), Lime, Modifier.weight(1f))
                    ProfileStat("AHORRADO", money(savings), Green, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileStat("INGRESOS", money(income), GreenDark, Modifier.weight(1f))
                    ProfileStat("EGRESOS", money(expenses), Red, Modifier.weight(1f))
                }
                Text("${movements.size} movimientos · datos en este dispositivo", color = Gray, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Reducir movimiento", color = Night, fontSize = 13.sp)
                        Text("Entrada breve y transiciones suaves", color = Gray, fontSize = 11.sp)
                    }
                    Switch(checked = reduceMotion, onCheckedChange = onReduceMotion)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Texto más grande", color = Night, fontSize = 14.sp)
                        Text("Aumenta un 20% y respeta el tamaño del sistema", color = Gray, fontSize = 12.sp)
                    }
                    Switch(checked = largeText, onCheckedChange = onLargeText)
                }
                OutlinedButton(onClick = onAutomation, modifier = Modifier.fillMaxWidth()) {
                    Text("Voz y notificaciones")
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color(0xFF062018))) { Text("Volver") } },
        dismissButton = { TextButton(onClick = onLogout) { Text("Cerrar sesión", color = Red) } },
    )
}

@Composable
internal fun ProfileStat(label: String, value: String, tint: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(RadiusSm), color = tint.copy(alpha = .10f), border = BorderStroke(1.dp, tint.copy(alpha = .18f))) {
        Column(Modifier.padding(10.dp)) {
            Text(label, color = Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(value, color = tint, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        }
    }
}

@Composable
internal fun MiniWalletMedal(tint: Color, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(size * .28f))
                .background(Brush.linearGradient(listOf(tint.copy(alpha = .95f), tint.copy(alpha = .52f)))),
        )
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = size * .18f), horizontalArrangement = Arrangement.spacedBy(size * .045f), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.width(size * .10f).height(size * .18f).clip(RoundedCornerShape(2.dp)).background(Color.White))
            Box(Modifier.width(size * .10f).height(size * .28f).clip(RoundedCornerShape(2.dp)).background(Color.White))
            Box(Modifier.width(size * .10f).height(size * .39f).clip(RoundedCornerShape(2.dp)).background(Color.White))
        }
        Box(Modifier.align(Alignment.TopStart).offset(size * .08f, (-size * .08f)).width(size * .55f).height(size * .16f).clip(RoundedCornerShape(50)).background(Color(0xFFD92532)))
    }
}

@Composable
internal fun BottomNavigation(page: Int, onSelect: (Int) -> Unit, onAdd: () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    // Layered translucent tints and a reflective rim give a glass appearance
    // without an expensive platform-specific backdrop filter on every frame.
    Surface(
        modifier = Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = .35f), spotColor = NavigationAqua.copy(alpha = .10f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xF0273D61), Color(0xEE111E37), Color(0xF213233F)))),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .32f), NavigationAqua.copy(alpha = .14f), Color.White.copy(alpha = .06f)))),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 82.dp).padding(horizontal = 7.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            BottomItem(DinexNavigationIcons.Home, "Inicio", page == 0, Modifier.weight(1f)) { onSelect(0) }
            BottomItem(DinexNavigationIcons.Movements, "Movimientos", page == 1, Modifier.weight(1.3f)) { onSelect(1) }
            Box(Modifier.weight(1.05f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(58.dp).shadow(9.dp, CircleShape, spotColor = NavigationAqua.copy(alpha = .35f)).clip(CircleShape)
                        .background(NavigationAqua)
                        .semantics { contentDescription = "Registrar gasto, ingreso o ahorro" }
                        .clickable(role = Role.Button, onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(DinexNavigationIcons.Add, contentDescription = null, tint = Color(0xFF06212B), modifier = Modifier.size(29.dp))
                }
            }
            BottomItem(DinexNavigationIcons.Plan, "Plan", page == 2, Modifier.weight(1f)) { onSelect(2) }
            BottomItem(DinexNavigationIcons.Analysis, "Análisis", page == 3, Modifier.weight(1f)) { onSelect(3) }
        }
    }
}

@Composable
internal fun BottomItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint by animateColorAsState(if (selected) NavigationAqua else NavigationMuted, tween(if (LocalDinexReducedMotion.current) 0 else 160))
    val highlight by animateColorAsState(if (selected) Color.White.copy(alpha = .075f) else Color.Transparent, tween(if (LocalDinexReducedMotion.current) 0 else 160))
    Column(
        modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(21.dp)).background(highlight)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick).padding(horizontal = 2.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(25.dp))
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            color = if (selected) Night else NavigationMuted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val NavigationAqua = Green
private val NavigationMuted = Gray
