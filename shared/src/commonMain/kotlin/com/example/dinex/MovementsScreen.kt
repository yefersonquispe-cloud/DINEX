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
// HISTORY
// Movements, monthly pass and delete flows.
// ═══════════════════════════════════════════════════════════════════════════
@Composable
internal fun HistoryPage(movements: List<Movement>, onAdd: () -> Unit, onDelete: (Movement) -> Unit, onImport: () -> Unit, onExport: () -> Unit) {
    var filter by remember { mutableIntStateOf(0) }
    val visible = movements.filter { filter == 0 || (filter == 1 && !it.income) || (filter == 2 && it.income) }
    val income = movements.filter { it.income }.sumOf { it.amount }
    val expense = movements.filterNot { it.income }.sumOf { it.amount }
    LazyColumn(Modifier.fillMaxSize().widthIn(max = 820.dp), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Movimientos", fontWeight = FontWeight.Black, fontSize = 25.sp); Text(currentMonthYearLabel(), color = Gray, fontSize = 11.sp) }
                Button(onClick = onAdd, shape = RoundedCornerShape(13.dp)) { Text("Nuevo") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Text("Importar Excel/CSV", fontSize = 11.sp) }
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) { Text("Exportar reporte", fontSize = 11.sp) }
            }
        }
        item { MonthPass(income, expense, movements.size) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Todos", "Gastos", "Ingresos").forEachIndexed { i, label -> FilterChip(filter == i, { filter = i }, label = { Text(label) }) }
            }
        }
        items(visible, key = { it.id }) { movement -> Ticket(movement, onDelete = { onDelete(movement) }) }
    }
}

@Composable
internal fun MonthPass(income: Double, expense: Double, movementCount: Int) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF171A24), Color(0xFF27203C)))).padding(20.dp)) {
        Column {
            Text("BALANCE DEL MES", color = Lime, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            Text(money(income - expense), color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
            Spacer(Modifier.height(16.dp)); Dashes(Color.White.copy(alpha = .18f)); Spacer(Modifier.height(14.dp))
            Row { PassValue("ENTRÓ", money(income), Lime, Modifier.weight(1f)); PassValue("SALIÓ", money(expense), Color(0xFFFFA19B), Modifier.weight(1f)); PassValue("MOVS.", "$movementCount", Color.White, Modifier.weight(1f)) }
        }
    }
}

@Composable
internal fun PassValue(label: String, text: String, color: Color, modifier: Modifier) {
    Column(modifier) { Text(label, color = Color.White.copy(alpha = .5f), fontSize = 8.sp); Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

@Composable
internal fun Ticket(movement: Movement, onDelete: () -> Unit) {
    val info = categories.firstOrNull { it.name == movement.category }
    val tint = if (movement.income) Green else info?.color ?: Gray
    Surface(shape = RoundedCornerShape(18.dp), color = Card, border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = .18f))) {
        Column {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    Text(if (movement.income) "IN" else info?.code ?: "GT", color = tint, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
                    Text(movement.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("${movement.category}  ·  ${movement.method}", color = Gray, fontSize = 9.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text((if (movement.income) "+" else "-") + money(movement.amount), color = if (movement.income) GreenDark else Red, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text("${movement.day} ${currentMonthShort()}", color = Gray, fontSize = 8.sp)
                }
            }
            Dashes(tint.copy(alpha = .14f)); Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("COMPROBANTE DINEX", color = Gray, fontSize = 7.sp, letterSpacing = .8.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Eliminar",
                    color = Red,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp)).clickable(onClick = onDelete)
                        .background(Red.copy(alpha = .10f)).padding(horizontal = 9.dp, vertical = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                Text("#${movement.id.toString().padStart(4, '0')}", color = Gray, fontSize = 7.sp)
            }
        }
    }
}
