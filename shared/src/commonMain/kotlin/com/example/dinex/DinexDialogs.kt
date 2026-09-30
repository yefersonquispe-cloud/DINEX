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
// DIALOGS & QUICK CAPTURE
// Expense/income/saving forms, camera review, loading and notifications.
// ═══════════════════════════════════════════════════════════════════════════
@Composable
internal fun QuickAddDialog(onDismiss: () -> Unit, onExpense: () -> Unit, onIncome: () -> Unit, onSaving: () -> Unit, onScan: () -> Unit, onAutomation: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp),
        title = { Column { Text("¿Qué quieres registrar?", fontWeight = FontWeight.Black); Text("Elige la forma más rápida", color = Gray, fontSize = 11.sp, fontWeight = FontWeight.Normal) } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            DialogAction("-", "Registrar gasto", "Monto, categoría y listo", Red.copy(alpha = .12f), Red, onExpense)
            DialogAction("+", "Registrar ingreso", "Aumenta tu saldo", Mint, GreenDark, onIncome)
            DialogAction("AH", "Súper registro de ahorro", "Activa tu racha y suma a tu tarjeta", Color(0xFF073F3A), Lime, onSaving)
            DialogAction("CAM", "Escanear compra", "Foto + reconocimiento automático", Indigo.copy(alpha = .14f), GreenDark, onScan)
            DialogAction("VOZ", "Voz y notificaciones", "Di un gasto o revisa alertas del teléfono", Color(0xFF173249), ElectricBlue, onAutomation)
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

@Composable
internal fun DialogAction(code: String, title: String, subtitle: String, bg: Color, tint: Color, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(17.dp), color = bg) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(39.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) { Text(code, color = Color.White, fontWeight = FontWeight.Black, fontSize = if (code == "CAM") 8.sp else 18.sp) }
            Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(title, color = tint, fontWeight = FontWeight.Black, fontSize = 13.sp); Text(subtitle, color = Gray, fontSize = 9.sp) }; Text(">", color = tint, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
internal fun AutomationDialog(
    automation: DeviceAutomation,
    onDismiss: () -> Unit,
    onVoice: (String) -> Unit,
    onImport: (List<CapturedTransaction>) -> Unit,
) {
    var listening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var captured by remember { mutableStateOf(automation.drainNotifications()) }
    var accessRefresh by remember { mutableIntStateOf(0) }
    val notificationAccess = accessRefresh.let { automation.notificationReaderAvailable }
    var hotwordActive by remember { mutableStateOf(automation.hotwordEnabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { Image(painterResource(Res.drawable.dinex_mascot_ai), "Dinex voz", Modifier.size(70.dp), contentScale = ContentScale.Fit) },
        title = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Registro automático", fontWeight = FontWeight.Black)
            Text("Voz, alertas y ubicación opcional", color = Gray, fontSize = 10.sp, textAlign = TextAlign.Center)
        } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(18.dp), color = ElectricBlue.copy(alpha = .10f), border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = .28f))) {
                    Column(Modifier.padding(13.dp)) {
                        Text("Comando de voz", color = ElectricBlue, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Text("Di: “Dinex registra 20 soles en comida”", color = Gray, fontSize = 10.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { listening = true; error = null; automation.startVoice(onResult = { listening = false; onVoice(it) }, onError = { listening = false; error = it }) },
                            enabled = automation.voiceAvailable && !listening,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        ) { Text(if (listening) "Escuchando…" else "Hablar con Dinex", fontWeight = FontWeight.Bold) }
                    }
                }
                Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF173249), border = androidx.compose.foundation.BorderStroke(1.dp, Indigo.copy(alpha = .30f))) {
                    Column(Modifier.padding(13.dp)) {
                        Text("Notificaciones del teléfono", color = GreenDark, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Text("Detecta alertas con “gastaste”, “compraste”, “pagaste”, “recibiste” y un monto.", color = Gray, fontSize = 10.sp)
                        Spacer(Modifier.height(7.dp))
                        if (!notificationAccess) {
                            Text("Acceso todavía bloqueado", color = Orange, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            Text("En Redmi/HyperOS: 1) abre Información de la app, 2) toca ⋮, 3) Permitir ajustes restringidos, 4) vuelve y activa Dinex en Acceso a notificaciones.", color = Gray, fontSize = 9.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { automation.openAppDetailsSettings() }, modifier = Modifier.weight(1f)) {
                                    Text("1. Info de app", color = Orange, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                }
                                TextButton(onClick = { automation.openNotificationSettings() }, modifier = Modifier.weight(1f)) {
                                    Text("2. Dar acceso", color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                }
                            }
                            TextButton(onClick = { accessRefresh++ }, modifier = Modifier.fillMaxWidth()) {
                                Text("Ya lo activé · comprobar", color = ElectricBlue, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        } else {
                            Text("✓ Acceso activo. Dinex ya puede detectar alertas financieras.", color = Lime, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { accessRefresh++; captured = automation.drainNotifications() },
                            enabled = notificationAccess,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                        ) { Text("Revisar alertas detectadas", fontWeight = FontWeight.Bold) }
                        if (captured.isNotEmpty()) {
                            Spacer(Modifier.height(7.dp))
                            captured.take(4).forEach { item ->
                                Text("${item.source} · ${money(item.amount)} · ${if (item.income) "ingreso" else "gasto"}", color = Night, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = { onImport(captured) }) { Text("Registrar ${captured.size} alertas", color = Lime, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(18.dp), color = Green.copy(alpha = .09f), border = androidx.compose.foundation.BorderStroke(1.dp, Green.copy(alpha = .30f))) {
                    Column(Modifier.padding(13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Oye Dinex", color = GreenDark, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                Text(if (hotwordActive) "Activo fuera de la aplicación" else "Actívalo para llamar a Dinex por voz", color = if (hotwordActive) Lime else Gray, fontSize = 9.sp)
                            }
                            Text(if (hotwordActive) "ACTIVO" else "APAGADO", color = if (hotwordActive) Lime else Gray, fontWeight = FontWeight.Black, fontSize = 8.sp)
                        }
                        Spacer(Modifier.height(7.dp))
                        Text("Con la pantalla desbloqueada di “Oye Dinex”. Se abrirá una interfaz para registrar gastos, ingresos o recordatorios. Android mostrará una notificación permanente mientras escucha.", color = Gray, fontSize = 9.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val requested = !hotwordActive
                                val result = automation.setHotwordEnabled(requested)
                                error = result
                                hotwordActive = if (result == null) requested else automation.hotwordEnabled
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = if (hotwordActive) Red.copy(alpha = .85f) else Green),
                        ) { Text(if (hotwordActive) "Desactivar Oye Dinex" else "Activar Oye Dinex", fontWeight = FontWeight.Bold) }
                        if (hotwordActive) {
                            TextButton(onClick = { automation.openBatterySettings() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Permitir Inicio automático en Redmi", color = Orange, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                    }
                }
                if (automation.locationAvailable) Text("Ubicación: ${automation.currentLocationLabel() ?: "disponible al registrar"}", color = Lime, fontSize = 9.sp)
                else {
                    Text("La ubicación es opcional y solo se solicita en Android con tu permiso.", color = Gray, fontSize = 9.sp)
                    TextButton(onClick = { automation.requestLocationPermission() }) { Text("Activar ubicación opcional", color = GreenDark, fontSize = 10.sp) }
                }
                error?.let { Text(it, color = Red, fontSize = 9.sp) }
                Text("Dinex solo lee alertas después de tu autorización. “Oye Dinex” usa el micrófono únicamente mientras el modo visible está activo.", color = Gray, fontSize = 9.sp, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { automation.stopVoice(); onDismiss() }) { Text("Cerrar") } },
    )
}

@Composable
internal fun MovementDialog(income: Boolean, nextId: Int, onDismiss: () -> Unit, onSave: (Movement) -> Unit) {
    var amount by remember { mutableStateOf("") }; var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(if (income) "Ingreso" else "Comida") }; var method by remember { mutableStateOf("Yape") }; var error by remember { mutableStateOf(false) }
    val tint = if (income) Green else Red
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp),
        title = { Column { Text(if (income) "Nuevo ingreso" else "Nuevo gasto", fontWeight = FontWeight.Black); Text("Solo lo esencial", color = Gray, fontSize = 10.sp, fontWeight = FontWeight.Normal) } },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }; error = false }, Modifier.fillMaxWidth(),
                label = { Text("Monto") }, prefix = { Text("S/ ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = error)
            Spacer(Modifier.height(9.dp)); OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(if (income) "¿De dónde viene?" else "¿Qué compraste?") }, singleLine = true)
            if (!income) { Spacer(Modifier.height(12.dp)); Text("Categoría", fontWeight = FontWeight.Bold, fontSize = 10.sp); LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(categories) { item -> FilterChip(category == item.name, { category = item.name }, label = { Text(item.name) }) } } }
            Spacer(Modifier.height(10.dp)); Text("Medio", fontWeight = FontWeight.Bold, fontSize = 10.sp); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Yape", "Plin", "Efectivo", "Tarjeta").forEach { item -> FilterChip(method == item, { method = item }, label = { Text(item) }) } }
        } },
        confirmButton = { Button(onClick = { val value = amount.replace(',', '.').toDoubleOrNull(); if (value == null || value <= 0) error = true else onSave(Movement(nextId, title.ifBlank { if (income) "Ingreso" else "Compra" }, category, value, income, currentDayOfMonth(), method)) }, colors = ButtonDefaults.buttonColors(containerColor = tint)) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
internal fun SavingDialog(onDismiss: () -> Unit, onSave: (SavingEntry) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = { Image(painterResource(Res.drawable.dinex_mascot_ai), "Mascota de ahorro", Modifier.size(76.dp), contentScale = ContentScale.Fit) },
        title = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("¿Cuánto ahorraste?", fontWeight = FontWeight.Black); Text("Este monto irá a tu tarjeta de ahorro", color = Gray, fontSize = 10.sp) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' || char == ',' }; error = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Monto ahorrado") },
                    prefix = { Text("S/ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = error,
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(purpose, { purpose = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("¿Para qué estás ahorrando?") }, placeholder = { Text("Ej. PlayStation, celular, emergencia") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                OutlinedTextField(target, { target = it.filter { char -> char.isDigit() || char == '.' || char == ',' } }, Modifier.fillMaxWidth(), label = { Text("Meta total (opcional)") }, placeholder = { Text("¿Cuánto necesitas reunir?") }, prefix = { Text("S/ ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(16.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = Lime.copy(alpha = .10f), border = androidx.compose.foundation.BorderStroke(1.dp, Lime.copy(alpha = .22f))) {
                    Text("Registrar un ahorro activa el día de tu racha. Solo cuenta una vez por día, aunque puedes agregar varios montos.", color = Lime, fontSize = 9.sp, modifier = Modifier.padding(11.dp))
                }
                if (error) Text("Ingresa un monto mayor que cero.", color = Red, fontSize = 9.sp)
            }
        },
        confirmButton = { Button(onClick = { val value = amount.replace(',', '.').toDoubleOrNull(); val targetValue = target.replace(',', '.').toDoubleOrNull() ?: 0.0; if (value == null || value <= 0) error = true else onSave(SavingEntry(value, purpose.trim(), targetValue)) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0C8D7F))) { Text("Guardar ahorro", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
internal fun InitialBalanceDialog(onSave: (Double) -> Unit) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(30.dp),
        icon = { Image(painterResource(Res.drawable.dinex_app_icon), "Dinex", Modifier.size(70.dp), contentScale = ContentScale.Fit) },
        title = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Comencemos con tu saldo", fontWeight = FontWeight.Black); Text("Así tus reportes empiezan desde cero y reflejan tu realidad.", color = Gray, fontSize = 10.sp, textAlign = TextAlign.Center) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, Modifier.fillMaxWidth(), label = { Text("Saldo disponible este mes") }, prefix = { Text("S/ ") }, placeholder = { Text("0.00") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(16.dp))
                Text("Puedes empezar sin saldo y registrar tus ingresos después desde el botón +.", color = Gray, fontSize = 10.sp)
            }
        },
        confirmButton = { Button(onClick = { onSave(amount.replace(',', '.').toDoubleOrNull() ?: 0.0) }, colors = ButtonDefaults.buttonColors(containerColor = Green), shape = RoundedCornerShape(14.dp)) { Text("Guardar y entrar", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { onSave(0.0) }) { Text("Empezar con S/ 0", color = Gray) } },
    )
}

@Composable
internal fun StreakLevelDialog(level: StreakLevel, streak: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(32.dp), color = Color(0xFF11141A), border = androidx.compose.foundation.BorderStroke(2.dp, level.color.copy(alpha = .68f)), shadowElevation = 26.dp) {
            Box(Modifier.fillMaxWidth().background(Brush.radialGradient(listOf(level.color.copy(alpha = .28f), Color.Transparent)))) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NUEVO NIVEL", color = level.color, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp)); Image(painterResource(Res.drawable.dinex_mascot_ai), "Mascota ${level.name}", Modifier.size(150.dp), contentScale = ContentScale.Fit)
                    MiniWalletMedal(level.color, 72.dp)
                    Spacer(Modifier.height(10.dp)); Text(level.name.uppercase(), color = level.color, fontWeight = FontWeight.Black, fontSize = 27.sp)
                    Text("¡Lograste $streak días ahorrando! Tu billetterita evolucionó.", color = Night, fontSize = 12.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp)); Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = level.color), shape = RoundedCornerShape(16.dp)) { Text("Continuar mi racha", color = Color(0xFF0A0C10), fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}

@Composable
internal fun GoalCelebrationDialog(goalName: String, amount: Double, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(32.dp), color = Color(0xFF081B27), border = androidx.compose.foundation.BorderStroke(2.dp, Orange.copy(alpha = .72f)), shadowElevation = 28.dp) {
            Box(Modifier.fillMaxWidth().background(Brush.radialGradient(listOf(Orange.copy(alpha = .26f), Color.Transparent)))) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✦  META CUMPLIDA  ✦", color = Orange, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("🎉  ✨  🎊  ✨  🎉", fontSize = 22.sp)
                    Image(painterResource(Res.drawable.dinex_mascot_ai), "Dinex celebra tu meta", Modifier.size(142.dp), contentScale = ContentScale.Fit)
                    Text("¡Enhorabuena!", color = Night, fontWeight = FontWeight.Black, fontSize = 27.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("Ya reuniste ${money(amount)} para $goalName.", color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(7.dp))
                    Text("Disfruta tu logro y sigue construyendo tus próximas metas financieras.", color = Gray, fontSize = 10.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Orange), shape = RoundedCornerShape(16.dp)) {
                        Text("Celebrar y continuar", color = Color(0xFF1A1610), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PremiumLoadingDialog(message: String) {
    Dialog(onDismissRequest = {}) {
        Surface(shape = RoundedCornerShape(30.dp), color = Color(0xFF11141A), border = androidx.compose.foundation.BorderStroke(1.dp, Lime.copy(alpha = .38f)), shadowElevation = 24.dp) {
            Column(Modifier.padding(horizontal = 26.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(Res.drawable.dinex_mascot_ai), "Dinex trabajando", Modifier.size(120.dp), contentScale = ContentScale.Fit)
                CircularProgressIndicator(color = Lime, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(14.dp)); Text(message, color = Night, fontWeight = FontWeight.Black, fontSize = 15.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(5.dp)); Text("Estamos afinando los datos para darte una mejor alternativa.", color = Gray, fontSize = 10.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
internal fun ReminderDialog(nextId: Int, onDismiss: () -> Unit, onSave: (PaymentReminder) -> Unit) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Column { Text("Nuevo recordatorio", fontWeight = FontWeight.Black); Text("No olvides tus próximos pagos", color = Gray, fontSize = 10.sp, fontWeight = FontWeight.Normal) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(title, { title = it; error = false }, Modifier.fillMaxWidth(), label = { Text("Nombre del pago") }, placeholder = { Text("Ej. Internet") }, singleLine = true)
                OutlinedTextField(date, { date = it.uppercase().take(12); error = false }, Modifier.fillMaxWidth(), label = { Text("Fecha") }, placeholder = { Text("Ej. 25 SEP") }, singleLine = true)
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }; error = false }, Modifier.fillMaxWidth(), label = { Text("Monto") }, prefix = { Text("S/ ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = error)
                if (error) Text("Completa el nombre, la fecha y un monto válido.", color = Red, fontSize = 9.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                val value = amount.replace(',', '.').toDoubleOrNull()
                if (title.isBlank() || date.isBlank() || value == null || value <= 0) error = true
                else onSave(PaymentReminder(nextId, title.trim(), date.trim(), value))
            }) { Text("Guardar recordatorio") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
internal fun ScanReviewDialog(result: ScannedPurchase, movementId: Int?, nextId: Int, onDismiss: () -> Unit, onSave: (Movement) -> Unit) {
    var title by remember { mutableStateOf(if (result.merchant != result.product) "${result.product} · ${result.merchant}" else result.product) }
    var amount by remember { mutableStateOf(result.amount?.let(::format2).orEmpty()) }; var category by remember { mutableStateOf(result.category) }; var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp),
        title = { Column { Text("Compra detectada", fontWeight = FontWeight.Black); Text("Revísala antes de registrarla. Nada se guarda sin tu confirmación.", color = Gray, fontSize = 10.sp, fontWeight = FontWeight.Normal) } },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Surface(shape = RoundedCornerShape(16.dp), color = Indigo.copy(alpha = .14f)) { Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Image(painterResource(Res.drawable.dinex_mascot_ai), "Dinex IA", Modifier.size(46.dp), contentScale = ContentScale.Fit); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(if (result.analyzedWithGemini) "Analizado con Dinex IA" else "Análisis local completado", color = GreenDark, fontWeight = FontWeight.Black, fontSize = 12.sp); Text("Confianza ${result.confidence}%${if (result.priceEstimated) " · precio estimado" else ""}", color = Gray, fontSize = 9.sp) }; Text("REVISAR", color = GreenDark, fontWeight = FontWeight.Black, fontSize = 8.sp) } }
            Spacer(Modifier.height(12.dp)); OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Producto o comercio") }, singleLine = true)
            Spacer(Modifier.height(9.dp)); OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }; error = false }, Modifier.fillMaxWidth(), label = { Text(if (result.priceEstimated) "Precio estimado" else "Precio detectado") }, prefix = { Text("S/ ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = error)
            Spacer(Modifier.height(10.dp)); Text("Categoría sugerida", fontWeight = FontWeight.Bold, fontSize = 10.sp); LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(categories) { item -> FilterChip(category == item.name, { category = item.name }, label = { Text(item.name) }) } }
            Spacer(Modifier.height(10.dp)); Text(if (result.analyzedWithGemini) "Gemini procesa la foto para extraer los datos; Dinex no conserva la imagen." else "El reconocimiento de respaldo ocurrió en el dispositivo.", color = Gray, fontSize = 9.sp)
        } },
        confirmButton = { Button(onClick = { val value = amount.replace(',', '.').toDoubleOrNull(); if (value == null || value <= 0) error = true else onSave(Movement(movementId ?: nextId, title.ifBlank { "Compra escaneada" }, category, value, false, currentDayOfMonth(), if (result.analyzedWithGemini) "Foto + Gemini" else "Escáner local")) }) { Text("Registrar gasto") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Descartar foto", color = Red, fontWeight = FontWeight.Bold) } })
}

@Composable
internal fun ImportMovementsDialog(onDismiss: () -> Unit, onImport: (List<Movement>) -> Unit) {
    var content by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Column { Text("Importar movimientos", fontWeight = FontWeight.Black); Text("Pega aquí el contenido CSV exportado por Yape, Plin o tu banco.", color = Gray, fontSize = 10.sp) } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(content, { content = it; error = "" }, Modifier.fillMaxWidth().heightIn(min = 150.dp), label = { Text("CSV de movimientos") }, placeholder = { Text("fecha,descripcion,categoria,monto,tipo,medio") })
            Text("También puedes abrir tu Excel, guardarlo como CSV y pegarlo aquí. Tus datos se quedan en el dispositivo.", color = Gray, fontSize = 9.sp)
            if (error.isNotBlank()) Text(error, color = Red, fontSize = 9.sp)
        } },
        confirmButton = { Button(onClick = { val parsed = parseImportedMovements(content); if (parsed.isEmpty()) error = "No encontramos filas válidas. Revisa las columnas y el separador." else onImport(parsed) }) { Text("Importar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray) } },
    )
}

@Composable
internal fun ExportReportDialog(movements: List<Movement>, onDismiss: () -> Unit) {
    val csv = remember(movements) { exportMovementsCsv(movements) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Column { Text("Reporte listo", fontWeight = FontWeight.Black); Text("Este formato es compatible con Excel y se puede guardar como PDF desde imprimir/compartir.", color = Gray, fontSize = 10.sp) } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) { Text("${movements.size} movimientos · ingresos, egresos y fechas", color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 11.sp); OutlinedTextField(csv, {}, Modifier.fillMaxWidth().heightIn(min = 170.dp), readOnly = true, label = { Text("CSV para Excel") }, textStyle = LocalTextStyle.current.copy(fontSize = 9.sp)) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo", color = GreenDark, fontWeight = FontWeight.Bold) } },
    )
}

@Composable
internal fun InlineMessage(message: String, onDismiss: () -> Unit) {
    Surface(
        Modifier.padding(16.dp).fillMaxWidth(.9f).clickable(onClick = onDismiss),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF191A22),
        border = androidx.compose.foundation.BorderStroke(1.dp, Indigo.copy(alpha = .55f)),
        shadowElevation = 14.dp,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Lime.copy(alpha = .14f)),
                contentAlignment = Alignment.Center,
            ) { Text("✓", color = Lime, fontWeight = FontWeight.Black, fontSize = 15.sp) }
            Spacer(Modifier.width(11.dp))
            Text(message, color = Night, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("×", color = Gray, fontSize = 18.sp)
        }
    }
}

@Composable
internal fun Dashes(color: Color) {
    Canvas(Modifier.fillMaxWidth().height(1.dp)) { var x = 0f; while (x < size.width) { drawLine(color, Offset(x, 0f), Offset((x + 7.dp.toPx()).coerceAtMost(size.width), 0f), 1.dp.toPx()); x += 12.dp.toPx() } }
}
