package com.example.dinex

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.TimeSource

private const val RefreshEveryMillis = 60_000L

/** Last prices, kept while the app runs so reopening the screen does not spend API calls. */
private object MarketCache {
    var snapshot: MarketSnapshot? = null
    var updatedAt: TimeSource.Monotonic.ValueTimeMark? = null
}

private enum class MarketFilter(val label: String) { All("Todo"), Currencies("Divisas"), Metals("Metales"), Crypto("Cripto") }
private enum class CryptoSort(val label: String) { MarketCap("Capitalización"), Gainers("Más suben"), Losers("Más bajan") }

/** Shows prices in soles or dollars, as the person chose in the header. */
private class PriceDisplay(val inUsd: Boolean) {
    fun of(quote: MarketQuote, decimals: Int? = null): String = amount(if (inUsd) quote.priceUsd else quote.pricePen, decimals)
    fun amount(value: Double, decimals: Int? = null): String =
        "${if (inUsd) "US$" else "S/"} ${formatAmount(value, decimals ?: priceDecimals(value))}"
}

/** Full-screen live market: currency converter, exchange rates, metals and the top cryptocurrencies. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketScreen(onClose: () -> Unit) {
    var snapshot by remember { mutableStateOf(MarketCache.snapshot) }
    var loading by remember { mutableStateOf(MarketCache.snapshot == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var updatedAt by remember { mutableStateOf(MarketCache.updatedAt) }
    var now by remember { mutableStateOf(TimeSource.Monotonic.markNow()) }
    var inUsd by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(MarketFilter.All) }
    var converterCode by remember { mutableStateOf("usd") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

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
                    inUsd = inUsd,
                    onCurrencyChange = { inUsd = it },
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
                else -> PullToRefreshBox(
                    isRefreshing = loading,
                    onRefresh = { scope.launch { refresh() } },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    MarketContent(
                        data = data,
                        display = PriceDisplay(inUsd),
                        filter = filter,
                        onFilter = { filter = it },
                        converterCode = converterCode,
                        onConverterCode = { converterCode = it },
                        onPickCurrency = { code ->
                            converterCode = code
                            scope.launch { listState.animateScrollToItem(0) }
                        },
                        error = error,
                        wide = wide,
                        listState = listState,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketContent(
    data: MarketSnapshot,
    display: PriceDisplay,
    filter: MarketFilter,
    onFilter: (MarketFilter) -> Unit,
    converterCode: String,
    onConverterCode: (String) -> Unit,
    onPickCurrency: (String) -> Unit,
    error: String?,
    wide: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(CryptoSort.MarketCap) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val crypto = remember(data.crypto, query, sort) {
        val q = query.trim().lowercase()
        data.crypto
            .filter { q.isEmpty() || q in it.name.lowercase() || q in it.symbol.lowercase() }
            .let { list ->
                when (sort) {
                    CryptoSort.MarketCap -> list.sortedBy { it.rank ?: Int.MAX_VALUE }
                    CryptoSort.Gainers -> list.sortedByDescending { it.change24h ?: Double.NEGATIVE_INFINITY }
                    CryptoSort.Losers -> list.sortedBy { it.change24h ?: Double.POSITIVE_INFINITY }
                }
            }
    }
    val columns = if (wide) 4 else 2
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth().fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = if (wide) 28.dp else 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (error != null) item(key = "error") {
                Surface(shape = RoundedCornerShape(12.dp), color = Orange.copy(alpha = .12f)) {
                    Text(error, color = Orange, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(12.dp))
                }
            }
            item(key = "converter") { Converter(data, converterCode, onConverterCode) }
            item(key = "filters") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarketFilter.entries.forEach { option -> DinexChip(option.label, filter == option) { onFilter(option) } }
                }
            }
            if (filter == MarketFilter.All || filter == MarketFilter.Currencies) {
                item(key = "currencies-title") { MarketSection("Divisas", "Toca una para usarla en el convertidor") }
                items(data.currencies.chunked(columns), key = { row -> "cur-" + row.first().id }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { quote ->
                            CurrencyTile(quote, display, selected = quote.id == converterCode, Modifier.weight(1f)) { onPickCurrency(quote.id) }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (filter == MarketFilter.All || filter == MarketFilter.Metals) {
                item(key = "metals") {
                    MarketSection("Metales", "Precio por onza troy")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        data.metals.forEach { quote ->
                            MetalTile(quote, display, if (quote.id == "xau") Color(0xFFC9962B) else Color(0xFF8E9AA6), Modifier.weight(1f))
                        }
                    }
                }
            }
            if (filter == MarketFilter.All || filter == MarketFilter.Crypto) {
                item(key = "crypto-title") {
                    MarketSection("Criptomonedas", "Las ${data.crypto.size} más grandes · toca una para ver su gráfico")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(30) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar Bitcoin, SOL, Cardano...") },
                        leadingIcon = { Icon(DinexNavigationIcons.Search, null, tint = Gray, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (query.isNotEmpty()) Icon(DinexNavigationIcons.Close, "Borrar búsqueda", tint = Gray,
                                modifier = Modifier.size(32.dp).clip(CircleShape).clickable { query = "" }.padding(7.dp))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(RadiusMd),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CryptoSort.entries.forEach { option -> DinexChip(option.label, sort == option) { sort = option } }
                    }
                }
                if (crypto.isEmpty()) item(key = "crypto-empty") {
                    Text("No encontramos “${query.trim()}” entre las ${data.crypto.size} criptomonedas.", color = Gray, fontSize = 13.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
                items(crypto, key = { "coin-" + it.id }) { quote ->
                    CryptoCard(
                        quote = quote,
                        display = display,
                        penPerUsd = data.penPerUsd,
                        expanded = expandedId == quote.id,
                        onToggle = { expandedId = if (expandedId == quote.id) null else quote.id },
                    )
                }
            }
            item(key = "source") {
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

@Composable
private fun MarketHeader(
    status: String,
    live: Boolean,
    loading: Boolean,
    inUsd: Boolean,
    onCurrencyChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(DinexNavigationIcons.Back, "Volver", onClose)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text("Mercado en vivo", color = Night, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (live) Color(0xFF22C55E) else Orange))
                Spacer(Modifier.width(6.dp))
                Text(status, color = Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // Soles or dollars for every price on the screen.
        Surface(shape = RoundedCornerShape(50), color = CardRaised, border = BorderStroke(1.dp, Border)) {
            Row(Modifier.padding(3.dp)) {
                listOf(false to "S/", true to "US$").forEach { (usd, label) ->
                    val selected = inUsd == usd
                    Text(
                        label,
                        color = if (selected) Color.White else NightSoft,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) Green else Color.Transparent)
                            .clickable(role = Role.Tab, onClickLabel = "Ver precios en $label") { onCurrencyChange(usd) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
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

/** Soles ⇄ any listed currency, the hero of the screen. */
@Composable
private fun Converter(data: MarketSnapshot, code: String, onCode: (String) -> Unit) {
    var amountText by remember { mutableStateOf("100") }
    var fromSoles by remember { mutableStateOf(false) }
    val quote = data.currencies.firstOrNull { it.id == code } ?: data.currencies.first()
    val amount = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val result = if (fromSoles) amount / quote.pricePen else amount * quote.pricePen
    val resultDecimals = if (result != 0.0 && abs(result) < 1) 4 else 2
    val resultText = if (fromSoles) "${formatAmount(result, resultDecimals)} ${quote.symbol}" else "S/ ${formatAmount(result, resultDecimals)}"
    Surface(shape = RoundedCornerShape(RadiusLg), color = Color.Transparent, shadowElevation = 6.dp) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF0E5431), Green, Color(0xFF328859))))
                .padding(20.dp),
        ) {
            Text("CONVERTIDOR", color = Color.White.copy(alpha = .78f), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { text -> amountText = text.filter { it.isDigit() || it == '.' || it == ',' }.take(12) },
                    modifier = Modifier.weight(1f),
                    prefix = { Text(if (fromSoles) "S/ " else "${quote.symbol} ", color = Color.White.copy(alpha = .8f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White.copy(alpha = .8f),
                        unfocusedBorderColor = Color.White.copy(alpha = .35f),
                        cursorColor = Color.White,
                    ),
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = .16f))
                        .clickable(onClickLabel = "Invertir conversión", role = Role.Button) { fromSoles = !fromSoles },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(DinexNavigationIcons.Swap, "Invertir conversión", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(resultText, color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp, maxLines = 1)
            Text(
                "1 ${quote.symbol} = S/ ${formatAmount(quote.pricePen, priceDecimals(quote.pricePen).coerceAtLeast(3))} · ${quote.name}",
                color = Color.White.copy(alpha = .78f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                data.currencies.forEach { option ->
                    val selected = option.id == quote.id
                    Text(
                        option.symbol,
                        color = if (selected) Green else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .background(if (selected) Color.White else Color.White.copy(alpha = .14f))
                            .clickable(role = Role.Tab, onClickLabel = "Convertir con ${option.name}") { onCode(option.id) }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DinexChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Green,
            selectedLabelColor = Color.White,
            labelColor = NightSoft,
        ),
    )
}

@Composable
private fun MarketSection(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(title, color = Night, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(subtitle, color = Gray, fontSize = 12.sp)
    }
}

@Composable
private fun CurrencyTile(quote: MarketQuote, display: PriceDisplay, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) Green else Border, tween(160))
    Surface(
        modifier.clip(RoundedCornerShape(RadiusMd)).clickable(onClickLabel = "Usar ${quote.name} en el convertidor", onClick = onClick),
        shape = RoundedCornerShape(RadiusMd),
        color = if (selected) Green.copy(alpha = .08f) else Card,
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(quote.symbol, color = GreenDark, fontWeight = FontWeight.Black, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Mint).padding(horizontal = 7.dp, vertical = 3.dp))
                Spacer(Modifier.width(8.dp))
                Text(quote.name, color = Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Text(display.of(quote, priceDecimals(if (display.inUsd) quote.priceUsd else quote.pricePen).coerceAtLeast(3)),
                color = Night, fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1)
        }
    }
}

@Composable
private fun MetalTile(quote: MarketQuote, display: PriceDisplay, tint: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(RadiusMd), color = Card, border = BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(tint.copy(alpha = .55f), tint))), contentAlignment = Alignment.Center) {
                    Text(quote.symbol, color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(quote.name, color = Night, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(display.of(quote, 2), color = Night, fontWeight = FontWeight.Black, fontSize = 19.sp, maxLines = 1)
            Text(
                if (display.inUsd) "S/ ${formatAmount(quote.pricePen, 2)}" else "US$ ${formatAmount(quote.priceUsd, 2)}",
                color = Gray, fontSize = 12.sp, maxLines = 1,
            )
        }
    }
}

/** A coin row that opens into an interactive 7-day chart and its key numbers. */
@Composable
private fun CryptoCard(quote: MarketQuote, display: PriceDisplay, penPerUsd: Double, expanded: Boolean, onToggle: () -> Unit) {
    val change = quote.change24h
    // The line covers 7 days, so its colour follows the week, not the last 24 h.
    val trendColor = changeColor(quote.trend.takeIf { it.size > 1 }?.let { it.last() - it.first() } ?: change)
    val border by animateColorAsState(if (expanded) trendColor.copy(alpha = .5f) else Border, tween(160))
    val toDisplay: (Double) -> Double = { usd -> if (display.inUsd) usd else usd * penPerUsd }
    Surface(
        shape = RoundedCornerShape(RadiusMd),
        color = Card,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.clip(RoundedCornerShape(RadiusMd)).clickable(onClickLabel = if (expanded) "Ocultar detalle" else "Ver detalle de ${quote.name}", onClick = onToggle),
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(coinColor(quote.id)), contentAlignment = Alignment.Center) {
                    Text(quote.symbol.take(4), color = Color.White, fontWeight = FontWeight.Black, fontSize = if (quote.symbol.length > 3) 9.sp else 11.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(quote.name, color = Night, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(quote.rank?.let { "#$it" }, quote.symbol).joinToString(" · "), color = Gray, fontSize = 12.sp, maxLines = 1)
                }
                if (!expanded && quote.trend.size > 1) {
                    Sparkline(quote.trend, trendColor, Modifier.width(60.dp).height(26.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 96.dp)) {
                    Text(display.of(quote), color = Night, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    if (change != null) ChangePill(change, "24 horas", Modifier.padding(top = 3.dp))
                }
            }
            AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    if (quote.trend.size > 1) {
                        InteractiveChart(quote.trend.map(toDisplay), toDisplay(quote.priceUsd), trendColor, display)
                        Spacer(Modifier.height(12.dp))
                    }
                    val stats = listOfNotNull(
                        quote.high24hUsd?.let { "Máx. 24 h" to display.amount(toDisplay(it)) },
                        quote.low24hUsd?.let { "Mín. 24 h" to display.amount(toDisplay(it)) },
                        quote.change7d?.let { "7 días" to "${if (it >= 0) "+" else "−"}${formatAmount(abs(it), 2)}%" },
                        quote.marketCapUsd?.let { "Capitalización" to compactAmount(toDisplay(it), display.inUsd) },
                        quote.volumeUsd?.let { "Volumen 24 h" to compactAmount(toDisplay(it), display.inUsd) },
                        quote.rank?.let { "Ranking" to "#$it en el mundo" },
                    )
                    stats.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { (label, value) ->
                                Surface(Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = CardRaised) {
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                        Text(label, color = Gray, fontSize = 11.sp)
                                        Text(value, color = Night, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangePill(change: Double, period: String, modifier: Modifier = Modifier) {
    val up = change >= 0
    val color = changeColor(change)
    val label = "${if (up) "+" else "−"}${formatAmount(abs(change), 2)}%"
    Surface(
        modifier = modifier.semantics { contentDescription = "${if (up) "Sube" else "Baja"} $label en $period" },
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = .13f),
    ) {
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
    }
}

@Composable
private fun Sparkline(points: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawPath(trendPath(points, size.width, size.height), color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** 7-day hourly chart: drag or tap on it to read the price at that moment. */
@Composable
private fun InteractiveChart(points: List<Double>, current: Double, color: Color, display: PriceDisplay) {
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val low = points.min()
    val high = points.max()
    val index = selected
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (index == null) "Precio actual" else hoursAgoLabel(points.size - 1 - index),
                    color = Gray, fontSize = 12.sp,
                )
                Text(display.amount(if (index == null) current else points[index]), color = Night, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1)
            }
            Text(
                if (index == null) "Toca o desliza\nsobre el gráfico" else "Máx. ${display.amount(high)}\nMín. ${display.amount(low)}",
                color = Gray, fontSize = 11.sp, lineHeight = 15.sp, textAlign = TextAlign.End, maxLines = 2,
            )
        }
        Spacer(Modifier.height(8.dp))
        fun pick(x: Float, width: Int) {
            selected = ((x / width.coerceAtLeast(1)) * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
        }
        Canvas(
            Modifier.fillMaxWidth().height(150.dp)
                .pointerInput(points) { detectTapGestures { pick(it.x, size.width) } }
                .pointerInput(points) {
                    detectHorizontalDragGestures(onDragStart = { pick(it.x, size.width) }) { change, _ -> pick(change.position.x, size.width) }
                },
        ) {
            val line = trendPath(points, size.width, size.height)
            val fill = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .28f), Color.Transparent)))
            drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (index != null) {
                val x = size.width * index / (points.size - 1)
                val range = (high - low).takeIf { it > 0 } ?: 1.0
                val y = size.height * (1 - ((points[index] - low) / range).toFloat())
                drawLine(color.copy(alpha = .6f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(x, y))
                drawCircle(color, radius = 5.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

private fun trendPath(points: List<Double>, width: Float, height: Float): Path {
    val min = points.min()
    val range = (points.max() - min).takeIf { it > 0 } ?: 1.0
    return Path().apply {
        points.forEachIndexed { i, value ->
            val x = width * i / (points.size - 1)
            val y = height * (1 - ((value - min) / range).toFloat())
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
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

private fun changeColor(change: Double?): Color = if ((change ?: 0.0) >= 0) Color(0xFF16A34A) else Color(0xFFDC2626)

private val CoinColors = mapOf(
    "bitcoin" to Color(0xFFF7931A),
    "ethereum" to Color(0xFF627EEA),
    "tether" to Color(0xFF26A17B),
    "binancecoin" to Color(0xFFD9A400),
    "ripple" to Color(0xFF23292F),
    "usd-coin" to Color(0xFF2775CA),
    "solana" to Color(0xFF8B5CF6),
    "tron" to Color(0xFFEB0029),
    "dogecoin" to Color(0xFFC2A633),
    "cardano" to Color(0xFF0033AD),
    "chainlink" to Color(0xFF2A5ADA),
    "litecoin" to Color(0xFF345D9D),
    "avalanche-2" to Color(0xFFE84142),
)
private val CoinPalette = listOf(Color(0xFF0F766E), Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFF2563EB), Color(0xFFEA580C), Color(0xFF475569))

private fun coinColor(id: String): Color = CoinColors[id] ?: CoinPalette[abs(id.hashCode()) % CoinPalette.size]

private fun priceDecimals(value: Double): Int = when {
    abs(value) >= 1 -> 2
    abs(value) >= 0.01 -> 4
    else -> 6
}

private fun elapsedLabel(seconds: Long): String = when {
    seconds < 5 -> "ahora"
    seconds < 60 -> "hace $seconds s"
    else -> "hace ${seconds / 60} min"
}

/** The 7-day trend has one point per hour, newest last. */
private fun hoursAgoLabel(hours: Int): String = when {
    hours <= 0 -> "Ahora"
    hours < 24 -> "Hace $hours h"
    hours % 24 == 0 -> "Hace ${hours / 24} d"
    else -> "Hace ${hours / 24} d ${hours % 24} h"
}

/** Big amounts in Spanish short scale: 1.62 bill. (billones), 24.1 mil M, 950.3 M. */
internal fun compactAmount(value: Double, inUsd: Boolean): String {
    val prefix = if (inUsd) "US$" else "S/"
    val (scaled, unit) = when {
        abs(value) >= 1e12 -> value / 1e12 to " bill."
        abs(value) >= 1e9 -> value / 1e9 to " mil M"
        abs(value) >= 1e6 -> value / 1e6 to " M"
        else -> value to ""
    }
    return "$prefix ${formatAmount(scaled, if (unit.isEmpty()) 0 else 2)}$unit"
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
