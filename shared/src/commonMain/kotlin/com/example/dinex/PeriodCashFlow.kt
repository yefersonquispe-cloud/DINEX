package com.example.dinex

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PeriodCashFlow(movements: List<Movement>, dates: List<String>) {
    var selected by remember(dates) { mutableStateOf(dates.lastOrNull().orEmpty()) }
    val totals = dates.map { date ->
        val items = movements.filter { it.dateIso() == date }
        items.filter { it.income }.sumOf { it.amount } to items.filterNot { it.income }.sumOf { it.amount }
    }
    val peak = totals.maxOfOrNull { maxOf(it.first, it.second) }?.coerceAtLeast(1.0) ?: 1.0
    val index = dates.indexOf(selected).coerceAtLeast(0)
    DinexCard {
        SectionHeader("Ingresos y gastos del periodo", "Verde: ingresos · Rosa: gastos. Desliza y toca un día.")
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(160.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            dates.forEachIndexed { i, date ->
                Column(Modifier.width(40.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(if (date == selected) CardRaised else Card).clickable { selected = date }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Row(Modifier.height(120.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.width(12.dp).height((totals[i].first / peak * 110).coerceAtLeast(2.0).dp).clip(RoundedCornerShape(4.dp)).background(Green))
                        Box(Modifier.width(12.dp).height((totals[i].second / peak * 110).coerceAtLeast(2.0).dp).clip(RoundedCornerShape(4.dp)).background(Red))
                    }
                    Text(date.takeLast(2), color = Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val amount = totals.getOrNull(index) ?: (0.0 to 0.0)
        Text(selected, color = Night, fontWeight = FontWeight.Bold)
        Text("Entró ${money(amount.first)} · Salió ${money(amount.second)}", color = Gray, fontSize = 13.sp)
        Text("Balance del día: ${money(amount.first - amount.second)}", color = Night, fontSize = 13.sp)
    }
}
