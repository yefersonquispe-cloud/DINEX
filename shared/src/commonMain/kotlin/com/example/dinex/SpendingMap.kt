package com.example.dinex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** One visited place with the total spent there. */
data class SpendingPlace(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val total: Double,
    val visits: Int,
)

/** Heat colors from little spending (yellow) to the most (deep red), shared by map and legend. */
internal val HeatColors = listOf(
    Color(0xFFFFE08A),
    Color(0xFFFDB44B),
    Color(0xFFF2762E),
    Color(0xFFD7263D),
    Color(0xFF8E0F2E),
)
internal val HeatStops = listOf(0.15f, 0.35f, 0.6f, 0.85f, 1f)

/** Groups geotagged expenses by a ~11 m grid (same shop or door), so repeated visits add up. */
internal fun spendingPlaces(movements: List<Movement>): List<SpendingPlace> {
    val grouped = movements.asSequence()
        .filter { !it.income }
        .filter { movement ->
            val lat = movement.latitude
            val lng = movement.longitude
            lat != null && lng != null && lat.isFinite() && lng.isFinite() &&
                lat in -90.0..90.0 && lng in -180.0..180.0
        }
        .groupBy { "${(it.latitude!! * 10_000).roundToInt()}|${(it.longitude!! * 10_000).roundToInt()}" }
    return grouped.map { (_, items) ->
        val label = items.asSequence()
            .map { it.location.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            .orEmpty()
        SpendingPlace(
            label = label.ifBlank { "Lugar visitado" },
            latitude = items.sumOf { it.latitude!! } / items.size,
            longitude = items.sumOf { it.longitude!! } / items.size,
            total = items.sumOf { it.amount },
            visits = items.size,
        )
    }.sortedByDescending { it.total }
}

/** Expenses that only have a text location, for devices without coordinates yet. */
internal fun namedSpendingPlaces(movements: List<Movement>): List<Pair<String, Double>> =
    movements.asSequence()
        .filter { !it.income && it.location.isNotBlank() }
        .groupBy { it.location.trim() }
        .map { (name, items) -> name to items.sumOf { it.amount } }
        .sortedByDescending { it.second }

@Composable
internal fun SpendingPlacesSection(
    movements: List<Movement>,
    locationAvailable: Boolean,
    onRequestLocation: () -> Unit,
) {
    val places = remember(movements) { spendingPlaces(movements) }
    val named = remember(movements) { namedSpendingPlaces(movements) }
    DinexCard(accent = Red) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("¿Dónde gastas más?", color = Night, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Mapa de calor de tus lugares", color = Gray, fontSize = 11.sp)
            }
            if (places.isNotEmpty()) {
                Text("${places.size} ${if (places.size == 1) "lugar" else "lugares"}", color = Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            places.isNotEmpty() -> {
                SpendingHeatmap(
                    places = places,
                    modifier = Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(RadiusMd)),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "Donde más gastas, el color es más intenso. El mapa necesita internet; la lista de abajo funciona sin conexión.",
                    color = Gray, fontSize = 9.sp, lineHeight = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                HeatLegend()
                Spacer(Modifier.height(10.dp))
                val top = places.first().total.coerceAtLeast(1.0)
                places.take(5).forEach { place -> PlaceSpendRow(place, top) }
                Spacer(Modifier.height(4.dp))
                Text(
                    "El lugar se guarda solo al registrar un gasto y con tu permiso de ubicación.",
                    color = Gray, fontSize = 9.sp,
                )
            }
            !locationAvailable -> {
                Text(
                    "Activa la ubicación y Dinex guardará el lugar de cada gasto para armar tu mapa de calor.",
                    color = Gray, fontSize = 12.sp, lineHeight = 17.sp,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onRequestLocation,
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                ) { Text("Permitir ubicación", fontWeight = FontWeight.Bold) }
            }
            else -> {
                Text(
                    "Aún no hay gastos con ubicación. Registra un gasto y el lugar se guardará automáticamente.",
                    color = Gray, fontSize = 12.sp, lineHeight = 17.sp,
                )
            }
        }
        if (named.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Lugares por nombre", color = Night, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            named.take(4).forEach { (name, total) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = Gray, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(money(total), color = Night, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PlaceSpendRow(place: SpendingPlace, top: Double) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(place.label, color = Night, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(money(place.total), color = Red, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${place.visits} ${if (place.visits == 1) "visita" else "visitas"}",
                color = Gray, fontSize = 9.sp, modifier = Modifier.width(58.dp),
            )
            DinexProgress((place.total / top).toFloat().coerceIn(0f, 1f), Red, height = 5.dp)
        }
    }
}

@Composable
private fun HeatLegend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Menos", color = Gray, fontSize = 9.sp)
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.weight(1f).height(8.dp).clip(CircleShape)
                .background(Brush.horizontalGradient(HeatColors)),
        )
        Spacer(Modifier.width(6.dp))
        Text("Más", color = Gray, fontSize = 9.sp)
    }
}

/** Android shows a real street map with a heat layer; iOS/web use a friendly fallback. */
@Composable
expect fun SpendingHeatmap(places: List<SpendingPlace>, modifier: Modifier = Modifier)

@Composable
internal fun SpendingHeatmapFallback(places: List<SpendingPlace>, modifier: Modifier = Modifier, message: String) {
    Box(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusMd))
            .background(Brush.linearGradient(listOf(Mint, Card))),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Mapa de calor", color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(message, color = Gray, fontSize = 10.sp, textAlign = TextAlign.Center, lineHeight = 14.sp)
            if (places.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                places.take(3).forEach { place ->
                    Text(
                        "${place.label} · ${money(place.total)}",
                        color = Night, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
