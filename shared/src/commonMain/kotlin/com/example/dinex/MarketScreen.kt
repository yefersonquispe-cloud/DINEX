package com.example.dinex

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.TimeSource

private const val RefreshEveryMillis = 60_000L

/** Last prices, kept while the app runs so reopening the screen does not spend API calls. */
private object MarketCache {
    var snapshot: MarketSnapshot? = null
    var updatedAt: TimeSource.Monotonic.ValueTimeMark? = null
}

/** Full-screen live market: soles exchange rate, gold, silver and crypto. */
@Composable
internal fun MarketScreen(onClose: () -> Unit) {
    var snapshot by remember { mutableStateOf(MarketCache.snapshot) }
    var loading by remember { mutableStateOf(MarketCache.snapshot == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var updatedAt by remember { mutableStateOf(MarketCache.updatedAt) }
    var now by remember { mutableStateOf(TimeSource.Monotonic.markNow()) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        runCatching { loadMarketSnapshot() }
            .onSuccess {
                snapshot = it; error = null; updatedAt = TimeSource.Monotonic.markNow()
                MarketCache.snapshot = it; MarketCache.updatedAt = updatedAt
            }
            .onFailure { failure ->
                // CoinGecko's free tier answers 429 when one network asks too often.
                error = if (failure.message.orEmpty().contains("429")) "Hay muchas consultas seguidas. Reintentamos en un minuto."
                else "No pudimos actualizar los precios. Revisa tu conexión."
            }
        loading = false
    }
    // Prices refresh every minute while the screen is open, a pace the free API allows.
    LaunchedEffect(Unit) {
        updatedAt?.let { last ->
            val wait = RefreshEveryMillis - last.elapsedNow().inWholeMilliseconds
            if (wait > 0) delay(wait)
        }
        while (true) {
            refresh()
            delay(RefreshEveryMillis)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = TimeSource.Monotonic.markNow()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(AppBg)) {
        DinexAuroraBackground()
        val wide = maxWidth >= DesktopBreakpoint
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.widthIn(max = 1020.dp).fillMaxWidth().align(Alignment.CenterHorizontally)) {
                MarketHeader(
                    status = when {
                        loading && snapshot == null -> "Conectando con el mercado..."
                        error != null && snapshot == null -> "Sin datos todavía"
                        error != null -> "Mostrando los últimos precios · ${updatedAt?.let { elapsedLabel((now - it).inWholeSeconds) }.orEmpty()}"
                        loading -> "Actualizando..."
                        else -> updatedAt?.let { "Actualizado ${elapsedLabel((now - it).inWholeSeconds)}" }.orEmpty()
                    },
                    live = error == null && snapshot != null,
                    loading = loading,
                    onClose = onClose,
                    onRefresh = { scope.launch { refresh() } },
                )
            }
            val data = snapshot
            when {
                data == null && error != null -> MarketMessage(error!!, onRetry = { scope.launch { refresh() } })
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Green)
                }
                else -> LazyColumn(
                    modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth().fillMaxHeight().align(Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(horizontal = if (wide) 28.dp else 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { CurrencyHero(data.currencies) }
                    item {
                        MarketSection("Metales", "Precio por onza troy")
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            data.metals.forEachIndexed { index, quote ->
                                MetalTile(quote, if (index == 0) Color(0xFFC9962B) else Color(0xFF8E9AA6), Modifier.weight(1f))
                            }
                        }
                    }
                    item {
                        MarketSection("Criptomonedas", "Variación en las últimas 24 horas")
                        Spacer(Modifier.height(10.dp))
                        Surface(shape = RoundedCornerShape(RadiusMd), color = Card, border = BorderStroke(1.dp, Border)) {
                            Column {
                                data.crypto.forEachIndexed { index, quote ->
                                    if (index > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Border.copy(alpha = .6f)))
                                    CryptoRow(quote)
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            "Fuente: CoinGecko. Precios referenciales que se actualizan cada minuto; " +
                                "pueden diferir de tu banco o casa de cambio. No es una recomendación de inversión.",
                            color = Gray, fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketHeader(status: String, live: Boolean, loading: Boolean, onClose: () -> Unit, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(DinexNavigationIcons.Back, "Volver", onClose)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text("Mercado en vivo", color = Night, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (live) Color(0xFF22C55E) else Orange))
                Spacer(Modifier.width(6.dp))
                Text(status, color = Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (loading) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Green, strokeWidth = 2.dp)
            }
        } else {
            RoundIconButton(DinexNavigationIcons.Refresh, "Actualizar precios", onRefresh)
        }
    }
}

@Composable
private fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Night, modifier = Modifier.size(22.dp))
    }
}

/** Dollar and euro in soles: what most people in Peru check first. */
@Composable
private fun CurrencyHero(currencies: List<MarketQuote>) {
    val dollar = currencies.firstOrNull { it.id == "usd" }
    val euro = currencies.firstOrNull { it.id == "eur" }
    Surface(
        shape = RoundedCornerShape(RadiusLg),
        color = Color.Transparent,
        shadowElevation = 6.dp,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF0E5431), Green, Color(0xFF328859))))
                .padding(22.dp),
        ) {
            Text("TIPO DE CAMBIO", color = Color.White.copy(alpha = .78f), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(6.dp))
            if (dollar != null) {
                Text("US$ 1 = S/ ${formatAmount(dollar.pricePen, 3)}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
                Text("Dólar estadounidense", color = Color.White.copy(alpha = .78f), fontSize = 13.sp)
            }
            if (euro != null) {
                Spacer(Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = Color.Black.copy(alpha = .22f)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("EUR", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.width(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Euro", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("1 EUR = US$ ${formatAmount(euro.priceUsd, 3)}", color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
                        }
                        Text("S/ ${formatAmount(euro.pricePen, 3)}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketSection(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(title, color = Night, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(subtitle, color = Gray, fontSize = 12.sp)
    }
}

@Composable
private fun MetalTile(quote: MarketQuote, tint: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(RadiusMd), color = Card, border = BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(tint.copy(alpha = .55f), tint))), contentAlignment = Alignment.Center) {
                    Text(quote.symbol.take(2), color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(quote.name.substringBefore(" ·"), color = Night, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text("S/ ${formatAmount(quote.pricePen, 2)}", color = Night, fontWeight = FontWeight.Black, fontSize = 19.sp, maxLines = 1)
            Text("US$ ${formatAmount(quote.priceUsd, 2)}", color = Gray, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun CryptoRow(quote: MarketQuote) {
    val change = quote.change24h
    val up = (change ?: 0.0) >= 0
    val trendColor = if (up) Color(0xFF16A34A) else Color(0xFFDC2626)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(cryptoColor(quote.id)), contentAlignment = Alignment.Center) {
            Text(quote.symbol.take(4), color = Color.White, fontWeight = FontWeight.Black, fontSize = if (quote.symbol.length > 3) 9.sp else 11.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(quote.name, color = Night, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(quote.symbol, color = Gray, fontSize = 12.sp)
        }
        if (quote.trend.size > 1) {
            Sparkline(quote.trend, trendColor, Modifier.width(64.dp).height(28.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 104.dp)) {
            Text("S/ ${formatAmount(quote.pricePen, priceDecimals(quote.pricePen))}", color = Night, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            if (change != null) {
                val label = "${if (up) "+" else "−"}${formatAmount(abs(change), 2)}%"
                Surface(
                    modifier = Modifier.padding(top = 3.dp).semantics { contentDescription = "${if (up) "Sube" else "Baja"} $label en 24 horas" },
                    shape = RoundedCornerShape(8.dp),
                    color = trendColor.copy(alpha = .13f),
                ) {
                    Text(label, color = trendColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun Sparkline(points: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val min = points.min()
        val range = (points.max() - min).takeIf { it > 0 } ?: 1.0
        val path = Path()
        points.forEachIndexed { i, value ->
            val x = size.width * i / (points.size - 1)
            val y = size.height * (1 - ((value - min) / range).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun MarketMessage(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(DinexNavigationIcons.Market, null, tint = Gray, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, color = Night, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) { Text("Intentar de nuevo", color = GreenDark, fontWeight = FontWeight.Bold) }
    }
}

private fun cryptoColor(id: String): Color = when (id) {
    "bitcoin" -> Color(0xFFF7931A)
    "ethereum" -> Color(0xFF627EEA)
    "solana" -> Color(0xFF8B5CF6)
    "ripple" -> Color(0xFF23292F)
    "dogecoin" -> Color(0xFFC2A633)
    else -> Color(0xFF64748B)
}

private fun priceDecimals(value: Double): Int = when {
    value >= 1 -> 2
    value >= 0.01 -> 4
    else -> 6
}

private fun elapsedLabel(seconds: Long): String = when {
    seconds < 5 -> "ahora"
    seconds < 60 -> "hace $seconds s"
    else -> "hace ${seconds / 60} min"
}

/** 276380.5 → "276,380.50": thousands with commas, as prices are read in Peru. */
internal fun formatAmount(value: Double, decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val scaled = (abs(value) * factor).roundToLong()
    val whole = (scaled / factor.toLong()).toString()
    val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    if (decimals == 0) return sign + grouped
    val fraction = (scaled % factor.toLong()).toString().padStart(decimals, '0')
    return "$sign$grouped.$fraction"
}
