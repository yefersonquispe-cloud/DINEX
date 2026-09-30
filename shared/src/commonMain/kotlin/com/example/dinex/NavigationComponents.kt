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

// ═══════════════════════════════════════════════════════════════════════════
// SHARED SHELL
// Top bar, profile, mascot bubble and bottom navigation.
// ═══════════════════════════════════════════════════════════════════════════
@Composable
internal fun ValidationHint(label: String, valid: Boolean) {
    Text(
        "${if (valid) "✓" else "○"}  $label",
        color = if (valid) Lime else Red.copy(alpha = .9f),
        fontSize = 9.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
internal fun DinexAssistantBubble(onClick: () -> Unit) {
    Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(64.dp).clickable(onClick = onClick),
            shape = RoundedCornerShape(22.dp),
            color = CardRaised,
            border = androidx.compose.foundation.BorderStroke(2.dp, Lime.copy(alpha = .7f)),
            shadowElevation = 18.dp,
        ) {
            Box(
                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Mint, Green.copy(alpha = .82f)))),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.dinex_mascot_ai),
                    contentDescription = "Asistente Dinex IA",
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).offset((-2).dp, (-3).dp).size(14.dp)
                .clip(CircleShape).background(AppBg).padding(3.dp).clip(CircleShape).background(Lime)
        )
    }
}

@Composable
internal fun DinexTopBar(streak: Int, name: String, email: String, onProfile: () -> Unit) {
    val level = streakLevel(streak)
    Surface(modifier = Modifier.statusBarsPadding(), color = AppBg, shadowElevation = 0.dp) {
        Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(Res.drawable.dinex_app_icon),
                contentDescription = "Logo Dinex",
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(11.dp))
            Column {
                Text("DINEX", color = Night, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.4.sp)
                Text("Decide mejor. Ahorra más.", color = Gray, fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(18.dp), color = level.color.copy(alpha = .12f), border = androidx.compose.foundation.BorderStroke(1.dp, level.color.copy(alpha = .38f))) {
                Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniWalletMedal(level.color, 28.dp)
                    Spacer(Modifier.width(6.dp))
                    Column { Text("$streak DÍAS", color = level.color, fontWeight = FontWeight.Black, fontSize = 10.sp); Text(level.name.uppercase(), color = Gray, fontSize = 7.sp) }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Indigo, ElectricBlue))).clickable(onClick = onProfile),
                contentAlignment = Alignment.Center,
            ) { Text(name.firstOrNull()?.uppercase() ?: email.firstOrNull()?.uppercase() ?: "D", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp) }
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
) {
    val income = movements.filter { it.income }.sumOf { it.amount }
    val expenses = movements.filterNot { it.income }.sumOf { it.amount }
    val level = streakLevel(streak)
    val next = nextStreakLevel(streak)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { Image(painterResource(Res.drawable.dinex_mascot_ai), "Mascota Dinex", Modifier.size(88.dp), contentScale = ContentScale.Fit) },
        title = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(name, fontWeight = FontWeight.Black, fontSize = 22.sp); Text(email, color = Gray, fontSize = 10.sp); Spacer(Modifier.height(5.dp)); Text("DASHBOARD PERSONAL", color = level.color, fontWeight = FontWeight.Black, fontSize = 8.sp, letterSpacing = 1.3.sp) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(18.dp), color = level.color.copy(alpha = .12f), border = androidx.compose.foundation.BorderStroke(1.dp, level.color.copy(alpha = .3f))) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        MiniWalletMedal(level.color, 48.dp); Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text("NIVEL ${level.name.uppercase()}", color = level.color, fontWeight = FontWeight.Black, fontSize = 12.sp); Text(if (next == null) "¡Completaste los 365 días!" else "${next.startsAt - streak} días para ${next.name}", color = Gray, fontSize = 9.sp) }; Text("$streak", color = Night, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileStat("SALDO", money(income - expenses), Lime, Modifier.weight(1f))
                    ProfileStat("AHORRADO", money(savings), Color(0xFF57E6CC), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileStat("INGRESOS", money(income), GreenDark, Modifier.weight(1f))
                    ProfileStat("EGRESOS", money(expenses), Red, Modifier.weight(1f))
                }
                Text("${movements.size} movimientos · tus datos están guardados en este dispositivo", color = Gray, fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Volver") } },
        dismissButton = { TextButton(onClick = onLogout) { Text("Cerrar sesión", color = Red) } },
    )
}

@Composable
internal fun ProfileStat(label: String, value: String, tint: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(15.dp), color = tint.copy(alpha = .10f), border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = .20f))) {
        Column(Modifier.padding(11.dp)) {
            Text(label, color = Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text(value, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
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
    Surface(modifier = Modifier.navigationBarsPadding(), color = Color(0xFF0A1C27), shadowElevation = 12.dp) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BottomItem("IN", "Inicio", page == 0, Modifier.weight(1f)) { onSelect(0) }
            BottomItem("MO", "Movimientos", page == 1, Modifier.weight(1f)) { onSelect(1) }
            Box(Modifier.weight(.8f), contentAlignment = Alignment.Center) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Indigo, ElectricBlue))).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                    Text("+", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
                }
            }
            BottomItem("PL", "Plan", page == 2, Modifier.weight(1f)) { onSelect(2) }
            BottomItem("AN", "Análisis", page == 3, Modifier.weight(1f)) { onSelect(3) }
        }
    }
}

@Composable
internal fun BottomItem(code: String, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(code, color = if (selected) GreenDark else Gray, fontWeight = FontWeight.Black, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (selected) GreenDark else Gray, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
