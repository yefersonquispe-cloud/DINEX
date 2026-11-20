@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.dinex

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource

// ═══════════════════════════════════════════════════════════════════════════
// MOVIMIENTOS · PRESUPUESTO · METAS · ANÁLISIS
// ═══════════════════════════════════════════════════════════════════════════

// ---------------------------------------------------------------- Movimientos

private enum class MovementSort(val label: String) { RECENT("Más recientes"), OLDEST("Más antiguos"), HIGHEST("Mayor monto"), LOWEST("Menor monto") }

@Composable
internal fun MovementsWebPage(movements: List<Movement>, actions: WebActions) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableIntStateOf(0) }
    var category by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf("") }
    var monthFilter by rememberSaveable { mutableIntStateOf(-1) }
    var sort by rememberSaveable { mutableStateOf(MovementSort.RECENT) }
    val months = remember(movements) { movements.map { it.webMonthKey() }.distinct().sortedDescending() }
    val visible = remember(movements, query, type, category, method, monthFilter, sort) {
        val q = query.trim().lowercase()
        val filtered = movements.filter { m ->
            (type == 0 || (type == 1 && !m.income) || (type == 2 && m.income)) &&
                (category.isEmpty() || m.category == category) && (method.isEmpty() || m.method == method) &&
                (monthFilter < 0 || m.webMonthKey() == monthFilter) &&
                (q.isEmpty() || m.title.lowercase().contains(q) || m.category.lowercase().contains(q) || m.method.lowercase().contains(q) ||
                    m.location.lowercase().contains(q) || fixed(m.amount, 2).contains(q))
        }
        when (sort) {
            MovementSort.RECENT -> filtered.sortedWith(compareByDescending<Movement> { it.webEpochDay() }.thenByDescending { it.id })
            MovementSort.OLDEST -> filtered.sortedWith(compareBy<Movement> { it.webEpochDay() }.thenBy { it.id })
            MovementSort.HIGHEST -> filtered.sortedByDescending { it.amount }
            MovementSort.LOWEST -> filtered.sortedBy { it.amount }
        }
    }
    val income = visible.filter { it.income }.sumOf { it.amount }
    val expense = visible.filterNot { it.income }.sumOf { it.amount }
    val filtersOn = query.isNotEmpty() || type != 0 || category.isNotEmpty() || method.isNotEmpty() || monthFilter >= 0
    PageFrame(scroll = false) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchField(query, { query = it }, Modifier.weight(1f).widthIn(max = 460.dp))
            Spacer(Modifier.weight(.01f))
            WButton("Importar", DinexNavigationIcons.Swap, onClick = actions.onImport)
            WButton("Exportar", DinexNavigationIcons.Send, primary = true, onClick = actions.onExport)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Segmented(listOf("Todos", "Gastos", "Ingresos"), type) { type = it }
            SelectMenu("Categoría", category, listOf("" to "Todas") + movements.map { it.category }.distinct().sorted().map { it to it }) { category = it }
            SelectMenu("Medio", method, listOf("" to "Todos") + movements.map { it.method }.distinct().sorted().map { it to it }) { method = it }
            SelectMenu("Mes", monthFilter.toString(), listOf("-1" to "Todos") + months.map { it.toString() to webMonthLabel(it) }) { monthFilter = it.toInt() }
            SelectMenu("Orden", sort.name, MovementSort.entries.map { it.name to it.label }) { sort = MovementSort.valueOf(it) }
            if (filtersOn) LinkButton("Limpiar filtros", W.negative) { query = ""; type = 0; category = ""; method = ""; monthFilter = -1 }
        }
        StatStrip {
            StripStat("Movimientos", "${visible.size}", W.ink, Modifier.weight(1f))
            StripDivider()
            StripStat("Entradas", "+ ${moneyGrouped(income)}", W.positive, Modifier.weight(1f))
            StripDivider()
            StripStat("Salidas", "- ${moneyGrouped(expense)}", W.negative, Modifier.weight(1f))
            StripDivider()
            StripStat("Neto", moneyGrouped(income - expense), if (income - expense < 0) W.negative else W.brand, Modifier.weight(1.3f)) {
                CompositionBar(visible.filterNot { it.income })
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).dinexEntrance().clip(WebCardShape).background(W.surface).border(1.dp, W.line, WebCardShape)) {
            Row(Modifier.fillMaxWidth().background(W.surfaceAlt).padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                // Agrupado por día (solo cuando se ordena por fecha), con el total del día en el encabezado.
                val byDate = sort == MovementSort.RECENT || sort == MovementSort.OLDEST
                val groups = remember(visible, byDate) { if (byDate) visible.groupBy { it.webEpochDay() }.toList() else listOf(-1 to visible) }
                val today = currentDayIndex()
                LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState()) {
                    groups.forEach { (day, items) ->
                        if (day >= 0) stickyHeader(key = "h$day") { DayHeader(day, today, items) }
                        items(items, key = { "${it.id}-${it.webEpochDay()}-${it.title}-${it.amount}" }) { item ->
                            Box(if (LocalDinexReducedMotion.current) Modifier else Modifier.animateItem()) { MovementTableRow(item) { actions.onDeleteMovement(item) } }
                        }
                    }
                }
            }
        }
    }
}

/** Barrita con la proporción de cada categoría en los gastos filtrados. */
@Composable
private fun CompositionBar(expenses: List<Movement>) {
    val total = expenses.sumOf { it.amount }
    if (total <= 0) return
    val groups = expenses.groupBy { it.budgetCategory() }.map { it.key to it.value.sumOf { m -> m.amount } }.sortedByDescending { it.second }
    Row(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)) {
        groups.forEachIndexed { i, (name, value) ->
            Box(Modifier.weight((value / total).toFloat().coerceAtLeast(.001f)).fillMaxHeight().background(categoryColor(name, i)))
        }
    }
}

@Composable
private fun DayHeader(day: Int, today: Int, items: List<Movement>) {
    val date = civilDateFromEpochDays(day)
    val title = when (day) {
        today -> "Hoy"
        today - 1 -> "Ayer"
        else -> "${webWeekdaysLong[weekdayIndex(day)]} ${date.day} de ${currentMonthName(date.month).lowercase()}${if (date.year != civilDateFromEpochDays(today).year) " ${date.year}" else ""}"
    }
    val net = items.sumOf { if (it.income) it.amount else -it.amount }
    Row(Modifier.fillMaxWidth().background(W.surface).padding(horizontal = 22.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = W.ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.width(8.dp))
        Text(countLabel(items.size, "movimiento", "movimientos"), color = W.ink3, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Text("${if (net >= 0) "+" else "-"} ${moneyGrouped(kotlin.math.abs(net))}", color = if (net >= 0) W.positive else W.ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(44.dp))
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(modifier.height(40.dp).clip(shape).background(W.surface).border(1.dp, W.line, shape).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(DinexNavigationIcons.Search, null, tint = W.ink3, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Buscar por nombre, categoría, lugar o monto…", color = W.ink3, fontSize = 13.sp, maxLines = 1)
            BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = W.ink, fontSize = 13.sp, fontFamily = MaterialTheme.typography.bodyMedium.fontFamily),
                cursorBrush = SolidColor(W.brand), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) Icon(DinexNavigationIcons.Close, "Limpiar búsqueda", tint = W.ink3, modifier = Modifier.size(16.dp).clickable { onChange("") }.pointerHoverIcon(PointerIcon.Hand))
    }
}

@Composable
private fun MovementTableRow(item: Movement, onDelete: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().background(if (hovered) W.hover else Color.Transparent).hoverable(interaction).padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(2.4f), verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(item.category, item.income, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(item.title, color = W.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.location.isNotBlank()) Text(item.location, color = W.ink3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.weight(1.3f)) {
            val tint = if (item.income) W.positive else categoryColor(item.category)
            Row(Modifier.clip(CircleShape).background(tint.copy(alpha = .1f)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(tint))
                Spacer(Modifier.width(6.dp))
                Text(item.category, color = W.ink2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(item.method, color = W.ink2, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${item.day} ${reportMonthShort(item.month)} ${item.year}", color = W.ink2, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text((if (item.income) "+ " else "- ") + moneyGrouped(item.amount), color = if (item.income) W.positive else W.ink, fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
        Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterEnd) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(if (hovered) W.negative.copy(alpha = .1f) else Color.Transparent)
                    .clickable(onClickLabel = "Eliminar ${item.title}", onClick = onDelete).pointerHoverIcon(PointerIcon.Hand),
                contentAlignment = Alignment.Center,
            ) { Icon(DinexNavigationIcons.Close, "Eliminar", tint = if (hovered) W.negative else W.ink3.copy(alpha = .4f), modifier = Modifier.size(15.dp)) }
        }
    }
}

// ---------------------------------------------------------------- Presupuesto

@Composable
internal fun BudgetWebPage(model: DinexViewModel, data: FinancialData, actions: WebActions) {
    val today = civilDateFromEpochDays()
    val todayKey = monthKeyOf(today)
    val month = remember(data.movements) { data.movements.filter { it.webMonthKey() == todayKey } }
    val suggestions = remember(data.movements) { suggestedCategoryBudgets(month, data.movements) }
    var editor by remember { mutableStateOf(false) }
    PageFrame {
        Columns(ratio = 1f, left = { stretch ->
            DailyLimitCard(model, data, stretch)
        }, right = { stretch ->
            WCard(stretch.fillMaxWidth()) {
                CardTitle("Presupuesto por categoría", "${currentMonthName()} · sugerido según tus ingresos e historial", DinexNavigationIcons.Plan) {
                    WButton("Editar", DinexNavigationIcons.Settings) { editor = true }
                }
                Spacer(Modifier.height(16.dp))
                val rows = categories.map { info ->
                    val limit = data.categoryBudgets[info.name] ?: suggestions[info.name] ?: 0.0
                    Triple(info, limit, month.filter { !it.income && it.budgetCategory() == info.name }.sumOf { it.amount })
                }
                val totalLimit = rows.sumOf { it.second }; val totalSpent = rows.sumOf { it.third }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RingProgress(if (totalLimit > 0) (totalSpent / totalLimit).toFloat() else 0f, if (totalSpent > totalLimit) W.negative else W.brand, 84.dp, 9.dp) {
                        Text(percentLabel(totalSpent, totalLimit), color = W.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Usaste", color = W.ink3, fontSize = 12.sp)
                        Text("${moneyGrouped(totalSpent)} de ${moneyGrouped(totalLimit)}", color = W.ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text(if (totalSpent <= totalLimit) "Te quedan ${moneyGrouped(totalLimit - totalSpent)} este mes" else "Te pasaste por ${moneyGrouped(totalSpent - totalLimit)}",
                            color = if (totalSpent <= totalLimit) W.positive else W.negative, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                rows.forEach { (info, limit, spent) ->
                    val over = limit > 0 && spent > limit
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryIcon(info.name, false, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(info.name, color = W.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(when { limit <= 0 -> "Sin límite"; over -> "Te pasaste por ${moneyGrouped(spent - limit)}"; else -> "Quedan ${moneyGrouped(limit - spent)}" },
                                    color = if (over) W.negative else W.ink3, fontSize = 12.sp)
                            }
                            Text("${moneyGrouped(spent)} / ${moneyGrouped(limit)}", color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        ThinBar(if (limit > 0) (spent / limit).toFloat() else 0f, if (over) W.negative else info.color, 7.dp)
                    }
                }
                if (data.categoryBudgets.isNotEmpty()) LinkButton("Volver a los límites sugeridos") { model.resetCategoryBudgets() }
            }
        })
        Columns(ratio = 1.4f, left = { stretch ->
            SavingsSimulatorCard(data, todayKey, stretch)
        }, right = { stretch ->
            RemindersCard(data.reminders, actions, stretch)
        })
    }
    if (editor) CategoryBudgetEditorDialog(suggestions, data.categoryBudgets, onDismiss = { editor = false },
        onSave = { if (model.setCategoryBudgets(it)) editor = false }, onReset = { model.resetCategoryBudgets(); editor = false })
}

@Composable
private fun DailyLimitCard(model: DinexViewModel, data: FinancialData, modifier: Modifier) {
    val todayIndex = currentDayIndex()
    val monday = todayIndex - weekdayIndex(todayIndex)
    val week = remember(data.movements) {
        (0..6).map { offset -> data.movements.filter { !it.income && it.webEpochDay() == monday + offset }.sumOf { it.amount } }
    }
    val limit = data.dailyLimit.toDouble()
    val overDays = week.take(weekdayIndex(todayIndex) + 1).count { it > limit }
    WCard(modifier.fillMaxWidth()) {
        CardTitle("Límite diario", "Lo máximo que quieres gastar en un día", DinexNavigationIcons.Wallet)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(moneyGrouped(rememberCountUp(limit)), color = W.ink, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Spacer(Modifier.width(8.dp))
            Text("por día · ${moneyGrouped(limit * 30)} al mes", color = W.ink3, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        Slider(
            value = data.dailyLimit, onValueChange = { model.setDailyLimit((it / 5f).toInt() * 5f) }, valueRange = 20f..120f,
            colors = SliderDefaults.colors(thumbColor = W.brand, activeTrackColor = W.brand, inactiveTrackColor = W.line),
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        )
        Row { Text("S/ 20", color = W.ink3, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text("S/ 120", color = W.ink3, fontSize = 12.sp) }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Esta semana", color = W.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (overDays == 0) "Sin pasarte ningún día" else "Te pasaste $overDays ${if (overDays == 1) "día" else "días"}",
                color = if (overDays == 0) W.positive else W.negative, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        SimpleBars(week, webWeekdaysShort, Modifier.fillMaxWidth().weight(1f).heightIn(min = 170.dp), highlight = weekdayIndex(todayIndex), limit = limit,
            tooltipTitle = { i -> civilDateFromEpochDays(monday + i).let { "${webWeekdaysLong[i]} ${it.day}" } })
    }
}

/** "¿Y si gastas menos?": elige una categoría y cuánto recortar para ver lo que ahorrarías. */
@Composable
private fun SavingsSimulatorCard(data: FinancialData, todayKey: Int, modifier: Modifier) {
    var category by rememberSaveable { mutableStateOf(categories.first().name) }
    var percent by rememberSaveable { mutableFloatStateOf(20f) }
    val simulation = remember(data.movements, category, percent) { simulateCut(data.movements, category, percent.toDouble(), todayKey) }
    val baseline = remember(data.movements) { averageMonthlyNet(data.movements, todayKey) }
    val goal = data.savingGoals().filter { it.target > it.saved }.maxByOrNull { it.saved / it.target.coerceAtLeast(1.0) }
    WCard(modifier.fillMaxWidth()) {
        CardTitle("¿Y si gastas menos?", "Simulador de ahorro con el promedio de tus últimos 3 meses", DinexNavigationIcons.Sparkle)
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { info ->
                val active = info.name == category
                val bg by animateColorAsState(if (active) info.color else W.surfaceAlt, tween(180), label = "categoría")
                Row(
                    Modifier.clip(CircleShape).background(bg).border(1.dp, if (active) info.color else W.line, CircleShape)
                        .clickable { category = info.name }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(DinexCategoryIcons.forCategory(info.name), null, tint = if (active) Color.White else info.color, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(info.name, color = if (active) Color.White else W.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gastar", color = W.ink2, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text("${percent.toInt()}% menos", color = W.brand, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(6.dp))
            Text("en ${category.lowercase()}", color = W.ink2, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text("Hoy gastas ${moneyGrouped(simulation.monthlyAverage)} al mes", color = W.ink3, fontSize = 12.sp)
        }
        Slider(
            value = percent, onValueChange = { percent = (it / 5f).toInt() * 5f }, valueRange = 5f..60f,
            colors = SliderDefaults.colors(thumbColor = W.brand, activeTrackColor = W.brand, inactiveTrackColor = W.line),
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        )
        Spacer(Modifier.height(8.dp))
        // Fila simple (sin BoxWithConstraints): esta tarjeta está dentro de columnas de igual altura.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SimTile("Al mes", simulation.monthlySaving, W.brand, Modifier.weight(1f))
            SimTile("En un año", simulation.yearlySaving, W.brand, Modifier.weight(1f))
            SimTile("En 5 años al 5 %", simulation.fiveYears, W.warning, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        val note = when {
            simulation.monthlyAverage <= 0 -> "Aún no tienes gastos en ${category.lowercase()} en los últimos 3 meses."
            goal != null -> {
                val remaining = goal.target - goal.saved
                val now = monthsToGoal(remaining, baseline)
                val with = monthsToGoal(remaining, baseline.coerceAtLeast(0.0) + simulation.monthlySaving)
                when {
                    with == null -> "Con este cambio empezarías a ahorrar para ${displayGoalName(goal.name)}."
                    now == null -> "Con este recorte, ${displayGoalName(goal.name)} estaría lista en $with ${if (with == 1) "mes" else "meses"}."
                    now > with -> "Tu meta ${displayGoalName(goal.name)} llegaría ${now - with} ${if (now - with == 1) "mes" else "meses"} antes ($with en vez de $now)."
                    else -> "A tu ritmo, ${displayGoalName(goal.name)} llega en $now ${if (now == 1) "mes" else "meses"}. Este ahorro extra te deja ${moneyGrouped(simulation.monthlySaving)} libres al mes."
                }
            }
            else -> "Separa ese dinero en una meta para verlo crecer."
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(W.brandSoft).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(DinexNavigationIcons.Target, null, tint = W.brand, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(note, color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SimTile(label: String, value: Double, tint: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(W.surfaceAlt).border(1.dp, W.line, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Text(label, color = W.ink3, fontSize = 12.sp)
        Text(moneyGrouped(rememberCountUp(value)), color = tint, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- Metas

@Composable
internal fun GoalsWebPage(data: FinancialData, actions: WebActions) {
    val goals = data.savingGoals()
    val saved = goals.sumOf { it.saved }
    val target = goals.sumOf { it.target }
    PageFrame {
        Row(
            Modifier.fillMaxWidth().dinexEntrance().clip(WebCardShape)
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(W.forestDeep, W.forest, W.forest2))).padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RingProgress(if (target > 0) (saved / target).toFloat() else 0f, W.lime, 104.dp, 11.dp, track = Color.White.copy(alpha = .1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(percentLabel(saved, target), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("avance", color = W.sidebarMuted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text("TOTAL AHORRADO", color = W.lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(moneyGrouped(rememberCountUp(saved)), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                Text("de ${moneyGrouped(target)} en ${countLabel(goals.size, "meta", "metas")} · faltan ${moneyGrouped((target - saved).coerceAtLeast(0.0))}",
                    color = Color.White.copy(alpha = .6f), fontSize = 13.sp)
            }
            Row(
                Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(W.lime).clickable { actions.onSave(null) }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DinexNavigationIcons.Add, null, tint = W.forest, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ahorrar en una meta", color = W.forest, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        if (goals.isEmpty()) {
            WCard(Modifier.fillMaxWidth()) { EmptyNote("Aún no tienes metas", "Crea una meta para darle un propósito a tu ahorro.", "Crear mi primera meta") { actions.onSave(null) } }
            return@PageFrame
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when { maxWidth >= 1050.dp -> 3; maxWidth >= 680.dp -> 2; else -> 1 }
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                goals.chunked(columns).forEach { row ->
                    Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        row.forEach { goal -> GoalCard(goal, actions, Modifier.weight(1f).fillMaxHeight()) }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        GoalPlannerCard(goals, data.movements)
    }
}

@Composable
private fun GoalCard(goal: SavingsGoal, actions: WebActions, modifier: Modifier) {
    val theme = goalTheme(goal.name)
    val progress = if (goal.target > 0) (goal.saved / goal.target).toFloat() else 0f
    val done = progress >= 1f
    WCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingProgress(progress, if (done) W.positive else W.brand, 92.dp, 8.dp) {
                Box(Modifier.size(62.dp).clip(CircleShape).background(theme.top.copy(alpha = if (DinexThemeState.dark) .22f else .14f)), contentAlignment = Alignment.Center) {
                    Image(painterResource(theme.main), null, Modifier.size(42.dp), contentScale = ContentScale.Fit)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(displayGoalName(goal.name), color = W.ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(percentLabel(goal.saved, goal.target), color = if (done) W.positive else W.brand, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (done) "¡Meta cumplida!" else "Faltan ${moneyGrouped((goal.target - goal.saved).coerceAtLeast(0.0))}",
                    color = if (done) W.positive else W.ink3, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("Ahorrado", color = W.ink3, fontSize = 11.sp)
                Text(moneyGrouped(goal.saved), color = W.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Meta", color = W.ink3, fontSize = 11.sp)
                Text(moneyGrouped(goal.target), color = W.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(1f).heightIn(min = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WButton("Ahorrar", DinexNavigationIcons.Add, primary = true) { actions.onSave(goal.id) }
            WButton("Ver detalle") { actions.onOpenGoal(goal.id) }
        }
    }
}

/** Planificador: cuánto ahorrar al mes y cuándo llegarías a la meta, con su curva. */
@Composable
private fun GoalPlannerCard(goals: List<SavingsGoal>, movements: List<Movement>) {
    val open = goals.filter { it.target > it.saved }
    if (open.isEmpty()) return
    var selectedId by rememberSaveable { mutableIntStateOf(open.first().id) }
    val goal = open.firstOrNull { it.id == selectedId } ?: open.first()
    val remaining = goal.target - goal.saved
    // Punto de partida: lo necesario para llegar en un año.
    var monthly by rememberSaveable(goal.id) { mutableFloatStateOf(((remaining / 12 / 10).toInt() * 10).coerceIn(20, 1000).toFloat()) }
    val months = monthsToGoal(remaining, monthly.toDouble())
    val projection = remember(goal, monthly) { goalProjection(goal.saved, goal.target, monthly.toDouble()) }
    val today = civilDateFromEpochDays()
    WCard(Modifier.fillMaxWidth()) {
        CardTitle("Planificador de metas", "Mueve el monto y mira cuándo llegarías", DinexNavigationIcons.Plan)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            open.take(6).forEach { g ->
                val active = g.id == goal.id
                Row(
                    Modifier.clip(CircleShape).background(if (active) W.brand else W.surfaceAlt).border(1.dp, if (active) W.brand else W.line, CircleShape)
                        .clickable { selectedId = g.id }.pointerHoverIcon(PointerIcon.Hand).padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(painterResource(goalTheme(g.name).main), null, Modifier.size(22.dp), contentScale = ContentScale.Fit)
                    Spacer(Modifier.width(6.dp))
                    Text(displayGoalName(g.name), color = if (active) Color.White else W.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Columns(ratio = .9f, equalHeight = false, left = {
            Text("Si ahorras al mes", color = W.ink3, fontSize = 12.sp)
            Text(moneyGrouped(monthly.toDouble()), color = W.ink, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
            Slider(
                value = monthly, onValueChange = { monthly = (it / 10f).toInt() * 10f }, valueRange = 20f..1000f,
                colors = SliderDefaults.colors(thumbColor = W.brand, activeTrackColor = W.brand, inactiveTrackColor = W.line),
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(W.brandSoft).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(DinexNavigationIcons.Target, null, tint = W.brand, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    if (months != null) {
                        val end = addMonths(today, months)
                        Text("Llegarías en $months ${if (months == 1) "mes" else "meses"}", color = W.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Para ${currentMonthName(end.month).lowercase()} de ${end.year} tendrías ${moneyGrouped(goal.target)}", color = W.ink2, fontSize = 12.sp)
                    }
                    Text("Tu ritmo promedio de ahorro: ${moneyGrouped(averageMonthlyNet(movements, monthKeyOf(today)))} al mes", color = W.ink3, fontSize = 11.sp)
                }
            }
        }, right = {
            LineAreaChart(
                listOf(ChartSeries(displayGoalName(goal.name), projection, W.brand)),
                labels = projection.indices.map { i -> addMonths(today, i).let { "${reportMonthShort(it.month)} ${it.year % 100}" } },
                modifier = Modifier.fillMaxWidth().height(210.dp),
                reference = goal.target, referenceLabel = "Meta ${shortMoney(goal.target)}",
                tooltipTitle = { i -> if (i == 0) "Hoy" else addMonths(today, i).let { "${currentMonthName(it.month)} ${it.year}" } },
            )
        })
    }
}

// ---------------------------------------------------------------- Análisis

@Composable
internal fun AnalysisWebPage(data: FinancialData, health: FinancialHealth, actions: WebActions) {
    val movements = data.movements
    val todayKey = monthKeyOf(civilDateFromEpochDays())
    val months = remember(movements) { (movements.map { it.webMonthKey() } + todayKey).distinct().sortedDescending() }
    var selected by rememberSaveable { mutableIntStateOf(months.first()) }
    val inMonth = remember(movements, selected) { movements.filter { it.webMonthKey() == selected } }
    val expenses = inMonth.filterNot { it.income }
    val history = remember(movements, selected) { monthlyTotals(movements, selected, 6) }
    val current = history.last(); val previous = history[history.size - 2]
    PageFrame {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(months.take(12)) { key ->
                    val active = key == selected
                    val bg by animateColorAsState(if (active) W.brand else W.surface, tween(180), label = "mes")
                    Text(
                        "${webMonthShort(key)} ${monthKeyYear(key) % 100}", color = if (active) Color.White else W.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).background(bg).border(1.dp, if (active) W.brand else W.line, CircleShape)
                            .clickable { selected = key }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            WButton("Exportar reporte", DinexNavigationIcons.Send, primary = true, onClick = actions.onExport)
        }
        StatStrip {
            StripStat("Ingresos", "+ ${moneyGrouped(current.income)}", W.positive, Modifier.weight(1f)) { DeltaChip(percentChange(current.income, previous.income)) }
            StripDivider()
            StripStat("Gastos", "- ${moneyGrouped(current.expense)}", W.negative, Modifier.weight(1f)) { DeltaChip(percentChange(current.expense, previous.expense), goodWhenDown = true) }
            StripDivider()
            StripStat("Tasa de ahorro", if (current.income > 0) "${(current.savingRate * 100).toInt()}%" else "—", W.ink, Modifier.weight(1f)) {
                Text(if (current.savingRate >= .2) "Meta del 20 % cumplida" else "Meta: 20 % de tus ingresos", color = W.ink3, fontSize = 11.sp)
            }
            StripDivider()
            StripStat("Gasto promedio por día", moneyGrouped(current.expense / (if (selected == todayKey) civilDateFromEpochDays().day else daysInMonthOf(monthKeyYear(selected), monthKeyMonth(selected)))),
                W.ink, Modifier.weight(1f)) { Text("${expenses.size} gastos en el mes", color = W.ink3, fontSize = 11.sp) }
        }
        Columns(ratio = 1.6f, left = { stretch ->
            WCard(stretch.fillMaxWidth()) {
                CardTitle("Ingresos, gastos y ahorro", "Últimos 6 meses · haz clic en un mes para verlo", DinexNavigationIcons.Analysis)
                Spacer(Modifier.height(16.dp))
                MonthBarsChart(history, selected, { selected = it }, Modifier.fillMaxWidth().weight(1f).heightIn(min = 250.dp))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { Legend(W.brand, "Ingresos"); Legend(W.negative, "Gastos"); Legend(W.warning, "Tasa de ahorro", dashed = true) }
            }
        }, right = { stretch ->
            val totals = weekdayTotals(expenses)
            val busiest = totals.indices.maxByOrNull { totals[it] } ?: 0
            WCard(stretch.fillMaxWidth()) {
                CardTitle("Por día de la semana", if (expenses.isEmpty()) "Sin gastos en el mes" else "Gastas más los ${webWeekdaysLong[busiest].lowercase()}")
                Spacer(Modifier.height(8.dp))
                WeekdayRadial(totals, Modifier.fillMaxWidth().weight(1f).heightIn(min = 250.dp))
            }
        })
        Columns(ratio = 1.6f, left = { stretch ->
            val groups = remember(expenses) { expenses.groupBy { it.budgetCategory() }.map { it.key to it.value.sumOf { m -> m.amount } }.sortedByDescending { it.second } }
            WCard(stretch.fillMaxWidth()) {
                CardTitle("Mapa de categorías", "El tamaño de cada bloque es lo que gastaste en esa categoría", DinexNavigationIcons.Plan)
                Spacer(Modifier.height(16.dp))
                if (groups.isEmpty()) EmptyNote("Sin gastos", "Elige otro mes o registra un gasto.")
                else CategoryTreemap(groups, Modifier.fillMaxWidth().weight(1f).heightIn(min = 260.dp))
            }
        }, right = { stretch ->
            WCard(stretch.fillMaxWidth(), padding = 0.dp) {
                Box(Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 6.dp)) {
                    CardTitle("Tus gastos más grandes", "Del mes elegido", DinexNavigationIcons.ArrowDown)
                }
                val top = expenses.sortedByDescending { it.amount }.take(6)
                if (top.isEmpty()) Box(Modifier.padding(horizontal = 22.dp)) { EmptyNote("Sin gastos", "Elige otro mes.") }
                top.forEach { MovementLine(it) }
                Spacer(Modifier.height(10.dp))
            }
        })
        Columns(ratio = 1f, equalHeight = false, left = {
            WCard(Modifier.fillMaxWidth()) {
                CardTitle("Salud financiera", "${health.score} de 100 · ${health.label}", DinexNavigationIcons.Shield)
                Spacer(Modifier.height(10.dp))
                HealthGauge(health.score, Modifier.fillMaxWidth().height(130.dp))
                Spacer(Modifier.height(10.dp))
                health.factors.forEach { factor ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RingProgress(factor.points.toFloat() / factor.max, healthColor(factor.points * 100 / factor.max), 34.dp, 4.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(factor.label, color = W.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(factor.detail, color = W.ink3, fontSize = 12.sp)
                        }
                        Text("${factor.points}/${factor.max}", color = W.ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }, right = {
            WCard(Modifier.fillMaxWidth()) {
                CardTitle("Dónde gastas", "Gastos registrados con ubicación")
                Spacer(Modifier.height(14.dp))
                val places = remember(movements) { spendingPlaces(movements) }
                if (places.isEmpty()) EmptyNote("Sin ubicaciones", "Registra gastos con ubicación desde el celular para ver tu mapa de calor.")
                else Box(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(14.dp))) { SpendingHeatmap(places, Modifier.fillMaxSize()) }
            }
        })
    }
}
