package com.example.dinex

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HistoryPage(movements: List<Movement>, onAdd: () -> Unit, onDelete: (Movement) -> Unit, onImport: () -> Unit, onExport: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var categoryFilter by remember { mutableStateOf("Todas") }
    var methodFilter by remember { mutableStateOf("Todos") }
    var dayFilter by remember { mutableStateOf("Todos") }
    var selected by remember { mutableStateOf<Movement?>(null) }
    val methods = listOf("Todos") + movements.map { it.method }.distinct()
    val days = listOf("Todos") + movements.map { it.dateIso() }.distinct().sortedDescending()
    val categoryOptions = listOf("Todas") + (categories.map { it.name } + movements.map { it.category } + "Ingreso").distinct()
    val visible = movements.filter { movement ->
        val matchesType = filter == 0 || (filter == 1 && !movement.income) || (filter == 2 && movement.income)
        val matchesQuery = query.isBlank() ||
            movement.title.contains(query, ignoreCase = true) ||
            movement.category.contains(query, ignoreCase = true) ||
            movement.method.contains(query, ignoreCase = true)
        val matchesCategory = categoryFilter == "Todas" || movement.category == categoryFilter
        val matchesMethod = methodFilter == "Todos" || movement.method == methodFilter
        val matchesDay = dayFilter == "Todos" || movement.dateIso() == dayFilter
        matchesType && matchesQuery && matchesCategory && matchesMethod && matchesDay
    }.sortedWith(compareByDescending<Movement> { it.dateIso() }.thenByDescending { it.id })
    val income = visible.filter { it.income }.sumOf { it.amount }
    val expense = visible.filterNot { it.income }.sumOf { it.amount }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= 760.dp
        LazyColumn(
            Modifier.fillMaxSize().widthIn(max = 960.dp),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = if (wide) 18.dp else 132.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DinexPageHeading("Tu actividad", "Movimientos", "Todo lo que entra y sale, en un solo lugar.", modifier = Modifier.weight(1f))
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar por nombre, categoría o método") },
                    singleLine = true,
                    shape = RoundedCornerShape(RadiusMd),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f), shape = RoundedCornerShape(RadiusMd)) {
                        Text("Importar Excel/CSV", fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f), shape = RoundedCornerShape(RadiusMd)) {
                        Text("Exportar reporte", fontSize = 11.sp)
                    }
                }
            }
            item { MonthPass(income, expense, visible.size) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Todos", "Gastos", "Ingresos").forEachIndexed { i, label ->
                        FilterChip(filter == i, { filter = i }, label = { Text(label, fontSize = 11.sp) })
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categoryOptions.forEach { item ->
                        FilterChip(categoryFilter == item, { categoryFilter = item }, label = { Text(item, fontSize = 10.sp) })
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    methods.forEach { item ->
                        FilterChip(methodFilter == item, { methodFilter = item }, label = { Text(item, fontSize = 10.sp) })
                    }
                    days.forEach { item ->
                        FilterChip(
                            dayFilter == item,
                            { dayFilter = item },
                            label = { Text(if (item == "Todos") "Todas las fechas" else item, fontSize = 10.sp) },
                        )
                    }
                }
            }
            if (visible.isEmpty()) {
                item {
                    DinexCard {
                        DinexEmptyState(
                            "Sin movimientos todavía",
                            if (movements.isEmpty()) "Registra tu primer ingreso o gasto para comenzar." else "Ningún movimiento coincide con los filtros.",
                            if (movements.isEmpty()) "+ Registrar movimiento" else null,
                            if (movements.isEmpty()) onAdd else null,
                        )
                    }
                }
            } else {
                item {
                    DinexCard {
                        Text("${visible.size} movimientos", color = Gray, fontSize = 12.sp)
                    }
                }
                items(visible, key = { it.id }) { movement ->
                    DinexCard { MovementListRow(movement) { selected = movement } }
                }
            }
        }
    }
    selected?.let { movement ->
        MovementDetailDialog(
            movement = movement,
            onDismiss = { selected = null },
            onDelete = { onDelete(movement); selected = null },
        )
    }
}

@Composable
internal fun MovementListRow(movement: Movement, onClick: () -> Unit) {
    MovementRow(movement, onClick)
}

@Composable
internal fun MonthPass(income: Double, expense: Double, movementCount: Int) {
    DinexCard(elevated = true, accent = GreenDark, vibrant = true) {
        Text("BALANCE DE LOS MOVIMIENTOS VISIBLES", color = Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text(money(income - expense), color = Night, fontWeight = FontWeight.Black, fontSize = 26.sp)
        Spacer(Modifier.height(12.dp))
        Row {
            PassValue("Entró", money(income), Lime, Modifier.weight(1f))
            PassValue("Salió", money(expense), Red, Modifier.weight(1f))
            PassValue("Movs.", "$movementCount", Night, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun PassValue(label: String, text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Gray, fontSize = 12.sp)
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
internal fun Ticket(movement: Movement, onDelete: () -> Unit) {
    MovementRow(movement, onClick = onDelete)
}

@Composable
internal fun MovementDetailDialog(movement: Movement, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val tint = movementTint(movement)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = Card,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryMark(movementCode(movement), tint, 40.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(movement.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Night, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(movement.category, color = Gray, fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine("Importe", (if (movement.income) "+ " else "− ") + money(movement.amount))
                DetailLine("Fecha", movement.dateLabel())
                DetailLine("Método", movement.method)
                if (movement.location.isNotBlank()) DetailLine("Ubicación", movement.location)
                DetailLine("ID", "#${movement.id.toString().padStart(4, '0')}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) { Text("Eliminar", color = Red, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Gray, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value, color = Night, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    }
}
