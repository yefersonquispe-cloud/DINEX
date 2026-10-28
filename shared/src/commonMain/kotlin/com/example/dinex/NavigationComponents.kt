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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dinex.shared.generated.resources.Res
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
    onWalletClick: () -> Unit,
    onMarket: () -> Unit,
    darkMode: Boolean,
    onToggleTheme: () -> Unit,
    streak: Int,
    name: String,
    email: String,
    profilePhoto: String,
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
                    onAdd = onAdd,
                    onProfile = onProfile,
                    onAssistant = onAssistant,
                    name = name,
                    email = email,
                    profilePhoto = profilePhoto,
                )
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    DinexDesktopTopBar(
                        streak = streak,
                        name = name,
                        email = email,
                        profilePhoto = profilePhoto,
                        onProfile = onProfile,
                        onWalletClick = onWalletClick,
                        onMarket = onMarket,
                        darkMode = darkMode,
                        onToggleTheme = onToggleTheme,
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        content(PaddingValues(0.dp))
                    }
                }
            }
            Box(Modifier.fillMaxSize().padding(end = 24.dp, bottom = 24.dp), contentAlignment = Alignment.BottomEnd) {
                DinexAssistantBubble(onClick = onAssistant)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                DinexTopBar(streak = streak, name = name, email = email, profilePhoto = profilePhoto, onProfile = onProfile, onWalletClick = onWalletClick,
                    onMarket = onMarket, darkMode = darkMode, onToggleTheme = onToggleTheme)
                // The dock floats over the page; scrollable screens add only end-of-list space.
                Box(Modifier.weight(1f)) { content(PaddingValues(0.dp)) }
            }
            Box(Modifier.align(Alignment.BottomCenter)) { BottomNavigation(page, onSelect = onSelectPage, onAdd = onAdd) }
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 100.dp).navigationBarsPadding()) { DinexAssistantBubble(onClick = onAssistant) }
        }
    }
}

@Composable
internal fun DinexSidebar(
    page: Int,
    onSelectPage: (Int) -> Unit,
    onAdd: () -> Unit,
    onProfile: () -> Unit,
    onAssistant: () -> Unit,
    name: String,
    email: String,
    profilePhoto: String,
) {
    Row(Modifier.fillMaxHeight()) {
        Column(
            Modifier.fillMaxHeight().width(256.dp).background(Card.copy(alpha = .94f))
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Mint),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(Res.drawable.dinex_mascot_ai),
                        contentDescription = "Logo Dinex",
                        modifier = Modifier.size(36.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text("DINEX", color = Night, fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = 1.6.sp)
                    Text("Decide mejor. Ahorra más.", color = Gray, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, hoveredElevation = 3.dp),
            ) {
                Icon(DinexNavigationIcons.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Registrar movimiento", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(24.dp))
            SidebarSection("Menú")
            SidebarItem("Inicio", "IN", page == 0) { onSelectPage(0) }
            SidebarItem("Movimientos", "MO", page == 1) { onSelectPage(1) }
            SidebarItem("Plan", "PL", page == 2) { onSelectPage(2) }
            SidebarItem("Análisis", "AN", page == 3) { onSelectPage(3) }
            Spacer(Modifier.height(18.dp))
            SidebarSection("Más")
            SidebarItem("Dinex IA", "IA", false) { onAssistant() }
            SidebarItem("Configuración", "CF", false) { onProfile() }
            Spacer(Modifier.weight(1f))
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusMd)).clickable(onClickLabel = "Abrir perfil", onClick = onProfile),
                shape = RoundedCornerShape(RadiusMd),
                color = CardRaised,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfilePhoto(name, profilePhoto, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, color = Night, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(email, color = Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(DinexNavigationIcons.Settings, null, tint = Gray, modifier = Modifier.size(18.dp))
                }
            }
        }
        Box(Modifier.fillMaxHeight().width(1.dp).background(Border.copy(alpha = .7f)))
    }
}

@Composable
private fun SidebarSection(title: String) {
    Text(
        title.uppercase(),
        color = Gray.copy(alpha = .8f),
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
    )
}

@Composable
internal fun SidebarItem(label: String, code: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when {
            selected -> Green.copy(alpha = .13f)
            hovered -> SurfaceHover
            else -> Color.Transparent
        },
        tween(if (LocalDinexReducedMotion.current) 0 else 140),
    )
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).height(44.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .hoverable(interaction).selectable(selected = selected, role = Role.Tab, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A short accent bar marks the current page without relying on color alone.
        Box(
            Modifier.width(4.dp).height(20.dp).clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                .background(if (selected) GreenDark else Color.Transparent),
        )
        Spacer(Modifier.width(10.dp))
        val icon = DinexNavigationIcons.forCode(code)
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) GreenDark else NavigationMuted, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(12.dp))
        } else {
            Text(code, color = if (selected) GreenDark else Gray, fontWeight = FontWeight.Black, fontSize = 9.sp, modifier = Modifier.width(33.dp))
        }
        Text(label, color = if (selected) Night else NightSoft, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
internal fun DinexDesktopTopBar(
    streak: Int, name: String, email: String, profilePhoto: String, onProfile: () -> Unit, onWalletClick: () -> Unit,
    onMarket: () -> Unit, darkMode: Boolean, onToggleTheme: () -> Unit,
) {
    val level = streakLevel(streak)
    Column {
        Row(
            Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Hola, ${name.substringBefore(' ')}", color = Night, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(currentDateLabel(), color = Gray, fontSize = 12.sp)
            }
            Surface(
                modifier = Modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
                    .clickable(onClickLabel = "Ver mi racha y ahorro", role = Role.Button, onClick = onWalletClick),
                shape = RoundedCornerShape(20.dp),
                color = level.color.copy(alpha = .12f),
                border = BorderStroke(1.dp, level.color.copy(alpha = .32f)),
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniWalletMedal(level.color, 24.dp)
                    Spacer(Modifier.width(7.dp))
                    Text("$streak d · ${level.name}", color = readableAccent(level.color), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(10.dp))
            Surface(shape = CircleShape, color = Card, border = BorderStroke(1.dp, Border)) {
                MarketButton(onMarket, 40.dp)
            }
            Spacer(Modifier.width(10.dp))
            Surface(shape = CircleShape, color = Card, border = BorderStroke(1.dp, Border)) {
                ThemeToggle(darkMode, onToggleTheme, 40.dp)
            }
            Spacer(Modifier.width(10.dp))
            ProfilePhoto(name, profilePhoto, 40.dp, Modifier.clip(CircleShape).clickable(onClickLabel = "Abrir perfil", onClick = onProfile))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Border.copy(alpha = .55f)))
    }
}

@Composable
internal fun DinexAssistantBubble(onClick: () -> Unit) {
    val pulse: State<Float> = if (LocalDinexReducedMotion.current) remember { mutableStateOf(1f) }
    else rememberInfiniteTransition(label = "assistant invitation").animateFloat(1f, 1.07f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "halo")
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(63.dp).graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
            .clip(CircleShape).background(Brush.linearGradient(listOf(Green.copy(alpha = .22f), Mint))))
        Surface(
            modifier = Modifier.size(58.dp).clickable(onClick = onClick),
            shape = CircleShape,
            color = Card,
            border = BorderStroke(1.dp, Green.copy(alpha = .28f)),
            shadowElevation = 12.dp,
        ) {
            Image(
                painter = painterResource(Res.drawable.dinex_mascot_ai),
                contentDescription = "Abrir Dinex IA, tu guía de dinero",
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
internal fun DinexTopBar(
    streak: Int, name: String, email: String, profilePhoto: String, onProfile: () -> Unit, onWalletClick: () -> Unit,
    onMarket: () -> Unit, darkMode: Boolean, onToggleTheme: () -> Unit,
) {
    val level = streakLevel(streak)
    Surface(modifier = Modifier.statusBarsPadding(), color = AppBg.copy(alpha = .88f)) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(Res.drawable.dinex_mascot_ai),
                contentDescription = "Logo Dinex",
                modifier = Modifier.size(38.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("DINEX", color = Night, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.2.sp)
                Text(currentDateLabel(), color = Gray, fontSize = 10.sp)
            }
            Surface(
                modifier = Modifier.clickable(onClickLabel = "Ver mi racha y ahorro", role = Role.Button, onClick = onWalletClick),
                shape = RoundedCornerShape(16.dp),
                color = level.color.copy(alpha = .12f),
                border = BorderStroke(1.dp, level.color.copy(alpha = .32f)),
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniWalletMedal(level.color, 22.dp)
                    Spacer(Modifier.width(5.dp))
                    Text("$streak", color = readableAccent(level.color), fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(4.dp))
            MarketButton(onMarket, 40.dp)
            ThemeToggle(darkMode, onToggleTheme, 40.dp)
            Spacer(Modifier.width(4.dp))
            ProfilePhoto(name, profilePhoto, 44.dp, Modifier.clickable(onClickLabel = "Abrir perfil y accesibilidad", onClick = onProfile))
        }
    }
}

/** Opens the full-screen live market (exchange rate, metals, crypto). */
@Composable
internal fun MarketButton(onClick: () -> Unit, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .clickable(onClickLabel = "Abrir mercado en vivo", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(DinexNavigationIcons.Market, contentDescription = "Mercado en vivo", tint = GreenDark, modifier = Modifier.size(size * .56f))
    }
}

/** Moon in light mode, sun in dark mode: one tap switches the theme. */
@Composable
internal fun ThemeToggle(darkMode: Boolean, onToggle: () -> Unit, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .clickable(onClickLabel = if (darkMode) "Cambiar a modo claro" else "Cambiar a modo oscuro", role = Role.Button, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (darkMode) DinexNavigationIcons.Sun else DinexNavigationIcons.Moon,
            contentDescription = if (darkMode) "Modo claro" else "Modo oscuro",
            tint = if (darkMode) Orange else Night,
            modifier = Modifier.size(size * .58f),
        )
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
    darkMode: Boolean,
    onDarkMode: (Boolean) -> Unit,
    budgetAlerts: Boolean,
    onBudgetAlerts: (Boolean) -> Unit,
    saveReminderEnabled: Boolean,
    onSaveReminderEnabled: (Boolean) -> Unit,
    saveReminderHour: Int,
    onSaveReminderHour: (Int) -> Unit,
    notificationsSupported: Boolean,
    notificationsEnabled: Boolean,
    onRequestNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    profilePhoto: String,
    onProfilePhoto: (String?) -> Boolean,
    onAutomation: () -> Unit,
) {
    val income = movements.filter { it.income }.sumOf { it.amount }
    val expenses = movements.filterNot { it.income }.sumOf { it.amount }
    val level = streakLevel(streak)
    val next = nextStreakLevel(streak)
    var photoError by remember { mutableStateOf("") }
    val files = rememberFileTransfer(
        onImport = { file ->
            if (file != null) runCatching { validatedProfilePhoto(file) }
                .onSuccess { photoError = if (onProfilePhoto(it)) "" else "No se pudo guardar la foto. Prueba con una imagen más pequeña." }
                .onFailure { photoError = it.message ?: "No pudimos leer la imagen." }
        }, onSaved = {}, onError = { photoError = it },
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = AppBg) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("‹  Volver", color = GreenDark) }
                    Spacer(Modifier.weight(1f))
                    Text("MI ESPACIO", color = Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    Spacer(Modifier.height(4.dp))
                    ProfilePhoto(name, profilePhoto, 100.dp)
                    Text(name, fontWeight = FontWeight.Black, fontSize = 27.sp, color = Night)
                    Text(email, color = Gray, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { files.chooseImport() }) { Text(if (profilePhoto.isBlank()) "Agregar foto" else "Cambiar foto") }
                        if (profilePhoto.isNotBlank()) TextButton(onClick = {
                            photoError = if (onProfilePhoto(null)) "" else "No se pudo quitar la foto."
                        }) { Text("Quitar", color = Red) }
                    }
                    if (photoError.isNotBlank()) Text(photoError, color = Red, fontSize = 12.sp)
                    Text("Tu foto se guarda solo en este dispositivo.", color = Gray, fontSize = 11.sp)
                    Surface(shape = RoundedCornerShape(RadiusLg), color = Card, border = BorderStroke(1.dp, Border)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            MiniWalletMedal(level.color, 46.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Nivel ${level.name}", color = readableAccent(level.color), fontWeight = FontWeight.Bold)
                                Text(if (next == null) "Completaste los 365 días" else "${next.startsAt - streak} días para ${next.name}", color = Gray, fontSize = 12.sp)
                            }
                            Text("$streak días", color = Night, fontWeight = FontWeight.Black)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProfileStat("SALDO", money(income - expenses - savings), Lime, Modifier.weight(1f))
                        ProfileStat("AHORRADO", money(savings), GreenDark, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProfileStat("INGRESOS", money(income), GreenDark, Modifier.weight(1f))
                        ProfileStat("EGRESOS", money(expenses), Red, Modifier.weight(1f))
                    }
                    Text("${movements.size} movimientos · datos en este dispositivo", color = Gray, fontSize = 12.sp)
                    Surface(shape = RoundedCornerShape(RadiusLg), color = Card, border = BorderStroke(1.dp, Border)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Preferencias", color = Night, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            ProfileSetting("Modo oscuro", if (darkMode) "Verde bosque" else "Verde y blanco", darkMode, onDarkMode)
                            ProfileSetting("Reducir movimiento", "Menos animaciones", reduceMotion, onReduceMotion)
                            ProfileSetting("Texto más grande", "Respeta el tamaño del sistema", largeText, onLargeText)
                        }
                    }
                    if (notificationsSupported) Surface(shape = RoundedCornerShape(RadiusLg), color = Card, border = BorderStroke(1.dp, Border)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Recordatorios", color = Night, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Te avisamos en el momento justo para cuidar tu presupuesto y tu racha.", color = Gray, fontSize = 11.sp)
                            ProfileSetting("Aviso de presupuesto", "Cuando superes tu límite diario", budgetAlerts, onBudgetAlerts)
                            ProfileSetting(
                                "Recordatorio para ahorrar",
                                if (saveReminderEnabled) "Si aún no ahorras, a las ${reminderHourLabel(saveReminderHour)}"
                                else "Un aviso al día para no romper tu racha",
                                saveReminderEnabled,
                            ) { enabled ->
                                onSaveReminderEnabled(enabled)
                                if (enabled && !notificationsEnabled) onRequestNotifications()
                            }
                            if (saveReminderEnabled) {
                                Text("Hora del recordatorio", color = Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(18, 20, 21).forEach { hour ->
                                        FilterChip(
                                            selected = saveReminderHour == hour,
                                            onClick = { onSaveReminderHour(hour) },
                                            label = { Text(reminderHourLabel(hour), fontSize = 11.sp) },
                                        )
                                    }
                                }
                            }
                            if (!notificationsEnabled) {
                                Text(
                                    "Las notificaciones están bloqueadas en Android. Actívalas para que Dinex pueda avisarte.",
                                    color = Orange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = onRequestNotifications) {
                                        Text("Permitir notificaciones", color = GreenDark, fontWeight = FontWeight.Bold)
                                    }
                                    TextButton(onClick = onOpenNotificationSettings) {
                                        Text("Abrir ajustes", color = Gray, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = onAutomation, modifier = Modifier.fillMaxWidth()) { Text("Voz y notificaciones") }
                    TextButton(onClick = onLogout) { Text("Cerrar sesión", color = Red) }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileSetting(label: String, detail: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Night, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(detail, color = Gray, fontSize = 11.sp)
        }
        Switch(value, onChange, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
internal fun ProfileStat(label: String, value: String, tint: Color, modifier: Modifier) {
    val vivid = label == "INGRESOS" || label == "EGRESOS"
    val foreground = when (label) {
        "INGRESOS" -> if (DinexThemeState.dark) Color(0xFF56FF9C) else Color(0xFF087C40)
        "EGRESOS" -> if (DinexThemeState.dark) Color(0xFFFF7182) else Color(0xFFD90F32)
        else -> tint
    }
    Surface(modifier, shape = RoundedCornerShape(RadiusSm), color = tint.copy(alpha = .09f),
        border = BorderStroke(1.dp, foreground.copy(alpha = if (vivid) .35f else .25f))) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(value, color = foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
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
    // Soft white glass surface with a green rim and accessible contrast.
    Surface(
        modifier = Modifier.fillMaxWidth(.88f).widthIn(max = 540.dp).navigationBarsPadding().padding(bottom = 6.dp)
            .shadow(18.dp, shape, ambientColor = Green.copy(alpha = .10f), spotColor = NavigationAqua.copy(alpha = .10f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Card.copy(alpha = .90f), CardRaised.copy(alpha = .82f)))),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(Green.copy(alpha = .28f), Border, Border))),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 70.dp).padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            BottomItem(DinexNavigationIcons.Home, "Inicio", page == 0, Modifier.weight(1f)) { onSelect(0) }
            BottomItem(DinexNavigationIcons.Movements, "Movimientos", page == 1, Modifier.weight(1.3f)) { onSelect(1) }
            Box(Modifier.weight(1.05f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(54.dp).shadow(9.dp, CircleShape, spotColor = NavigationAqua.copy(alpha = .35f)).clip(CircleShape)
                        .background(Green)
                        .semantics { contentDescription = "Registrar gasto, ingreso o ahorro" }
                        .clickable(role = Role.Button, onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(DinexNavigationIcons.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(29.dp))
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
    val highlight by animateColorAsState(if (selected) Mint else Color.Transparent, tween(if (LocalDinexReducedMotion.current) 0 else 160))
    Column(
        modifier.heightIn(min = 58.dp).clip(RoundedCornerShape(21.dp)).background(highlight)
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

private val NavigationAqua get() = if (DinexThemeState.dark) GreenDark else Green
private val NavigationMuted get() = Gray
