package com.example.dinex

import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer

@Composable
actual fun rememberReceiptScanner(
    onResult: (ScannedPurchase) -> Unit,
    onError: (String) -> Unit,
    onAnalyzing: () -> Unit,
    onCancelled: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentResult by rememberUpdatedState(onResult)
    val currentError by rememberUpdatedState(onError)
    val currentAnalyzing by rememberUpdatedState(onAnalyzing)
    val currentCancelled by rememberUpdatedState(onCancelled)
    val scope = rememberCoroutineScope()
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val labeler = remember { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }
    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128,
                )
                .enableAllPotentialBarcodes()
                .build(),
        )
    }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    DisposableEffect(Unit) {
        onDispose { recognizer.close(); labeler.close(); barcodeScanner.close() }
    }

    fun analyze(uri: Uri) {
        scope.launch {
            val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrElse {
                currentError("No pudimos abrir la foto tomada.")
                return@launch
            }
            val text = runCatching { recognizer.process(image).await() }.getOrNull()
            val labels = runCatching { labeler.process(image).await() }.getOrDefault(emptyList())
            val barcode = runCatching {
                barcodeScanner.process(image).await().firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
            }.getOrNull()
            val openFoodProduct = barcode?.let { runCatching { lookupOpenFoodFacts(it) }.getOrNull() }
            val raw = text?.text.orEmpty()
            val reading = raw.takeIf { it.isNotBlank() }?.let(::readReceiptText)
            val localResult = reading?.takeIf { it.kind != ReceiptKind.PRODUCTO && it.total != null }?.toPurchase()
                ?: parsePurchase(text, labels, barcode, openFoodProduct)
            val geminiResult = readGeminiApiKey(context).takeIf { it.isNotBlank() }?.let { apiKey ->
                runCatching { analyzePurchaseWithGemini(context, uri, apiKey, localResult, barcode, raw) }.getOrNull()
            }
            currentResult(geminiResult?.mergeReliableLocalData(localResult, reading) ?: localResult)
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = pendingUri
        pendingUri = null
        if (!captured || uri == null) {
            uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            currentCancelled()
            return@rememberLauncherForActivityResult
        }
        currentAnalyzing()
        analyze(uri)
    }

    return {
        runCatching {
            val folder = File(context.cacheDir, "dinex_receipts").apply { mkdirs() }
            val file = File.createTempFile("receipt_", ".jpg", folder)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).also { pendingUri = it }
        }.onSuccess { launcher.launch(it) }
            .onFailure { currentError("No se pudo iniciar la cámara.") }
    }
}

private suspend fun analyzePurchaseWithGemini(
    context: android.content.Context,
    uri: Uri,
    apiKey: String,
    local: ScannedPurchase,
    barcode: String?,
    ocrText: String,
): ScannedPurchase {
    val imageBytes = withContext(Dispatchers.IO) {
        val original = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("No se pudo leer la imagen")
        optimizeImage(original)
    }
    val ocrHint = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }.take(60).joinToString("\n").take(2500)
    val prompt = """
        Analiza esta foto para registrar un gasto personal en Perú. Primero decide qué es:
        boleta electrónica, factura electrónica, ticket de caja, nota de venta, voucher de tarjeta (Niubiz, Izipay),
        captura de Yape o Plin, transferencia bancaria, recibo de servicio (luz, agua, teléfono, internet, gas),
        recibo por honorarios, boleto (bus, avión, cine, concierto), pedido por app (Rappi, PedidosYa) o la foto de un producto.
        Reglas para el monto (amount), en soles:
        - En boletas, facturas y tickets usa el IMPORTE TOTAL pagado. Nunca el subtotal, la op. gravada, el IGV, los descuentos,
          el efectivo entregado ni el vuelto.
        - En Yape, Plin o transferencias usa el monto enviado.
        - En recibos de servicio usa el TOTAL A PAGAR del mes, no consumos parciales ni deudas anteriores por separado.
        - En vouchers usa el monto aprobado (TOTAL o IMPORTE).
        - Si el total está en dólares, conviértelo a soles y dilo en product.
        - Si es un producto sin precio visible, estima un precio minorista razonable en Perú y marca priceEstimated=true.
        product: lo comprado en pocas palabras (un producto, "Almuerzo", "Recibo de luz noviembre", "3 productos de bodega").
        merchant: el comercio, la empresa o la persona a quien se pagó.
        date: fecha de emisión o del pago en formato AAAA-MM-DD (no la de vencimiento), o "" si no se ve.
        paymentMethod: Yape, Plin, Tarjeta, Efectivo o Transferencia si el documento lo indica; si no, "".
        category: exactamente una de Comida, Transporte, Estudios, Entretenimiento, Otros (servicios del hogar = Otros).
        Texto leído por el OCR del teléfono (puede tener errores; úsalo para confirmar números):
        ${ocrHint.ifBlank { "(sin texto)" }}
        El análisis local encontró "${local.product}" de "${local.merchant}"${local.amount?.let { " por S/ ${format2(it)}" }.orEmpty()}${barcode?.let { " y código $it" }.orEmpty()}.
        Devuelve exclusivamente JSON válido, sin Markdown, con esta forma:
        {"documentType":"boleta", "product":"nombre", "merchant":"comercio", "amount":12.90, "date":"2026-11-08", "paymentMethod":"",
         "category":"Comida", "confidence":85, "priceEstimated":false}
        amount debe ser un número positivo. confidence debe estar entre 0 y 100.
    """.trimIndent()
    val parts = JSONArray()
        .put(
            JSONObject().put(
                "inline_data",
                JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", Base64.encodeToString(imageBytes, Base64.NO_WRAP))
                },
            ),
        )
        .put(JSONObject().put("text", prompt))
    val response = generateGeminiContent(
        apiKey = apiKey,
        parts = parts,
        systemPrompt = "Eres el analizador visual de Dinex. Extraes compras con precisión y nunca incluyes texto fuera del JSON solicitado.",
        maxOutputTokens = 400,
        temperature = 0.1,
        preferVision = true,
    )
    val jsonText = response.substring(response.indexOf('{').takeIf { it >= 0 } ?: 0,
        (response.lastIndexOf('}').takeIf { it >= 0 } ?: (response.length - 1)) + 1)
    val json = JSONObject(jsonText)
    val allowedCategories = setOf("Comida", "Transporte", "Estudios", "Entretenimiento", "Otros")
    val category = json.optString("category").takeIf(allowedCategories::contains) ?: "Otros"
    val amount = json.optDouble("amount", Double.NaN).takeIf { (!it.isNaN() && it > 0) }
        ?: error("Gemini no detectó un precio válido")
    val product = json.optString("product").trim().ifBlank { "Producto fotografiado" }
    val merchant = json.optString("merchant").trim().ifBlank { product }
    val date = Regex("(20\\d{2})-(\\d{1,2})-(\\d{1,2})").find(json.optString("date"))?.destructured?.let { (y, m, d) ->
        ReceiptDate(d.toInt(), m.toInt(), y.toInt()).takeIf { it.day in 1..31 && it.month in 1..12 }
    }
    val method = json.optString("paymentMethod").trim().takeIf { it in setOf("Yape", "Plin", "Tarjeta", "Efectivo", "Transferencia") }
    val kind = json.optString("documentType").trim().lowercase()
    return ScannedPurchase(
        product = product,
        merchant = merchant,
        amount = amount,
        category = category,
        confidence = json.optInt("confidence", 75).coerceIn(0, 100),
        priceEstimated = json.optBoolean("priceEstimated", false),
        analyzedWithGemini = true,
        kind = geminiKinds[kind] ?: if (kind.isBlank() || "producto" in kind) "" else kind.replaceFirstChar(Char::uppercase),
        date = date,
        method = method,
    )
}

private val geminiKinds = mapOf(
    "boleta" to ReceiptKind.BOLETA.label, "boleta electrónica" to ReceiptKind.BOLETA.label, "factura" to ReceiptKind.FACTURA.label,
    "factura electrónica" to ReceiptKind.FACTURA.label, "ticket" to ReceiptKind.TICKET.label, "ticket de caja" to ReceiptKind.TICKET.label,
    "nota de venta" to ReceiptKind.NOTA_VENTA.label, "voucher" to ReceiptKind.VOUCHER.label, "voucher de tarjeta" to ReceiptKind.VOUCHER.label,
    "yape" to ReceiptKind.YAPE.label, "plin" to ReceiptKind.PLIN.label, "transferencia" to ReceiptKind.TRANSFERENCIA.label,
    "recibo de servicio" to ReceiptKind.SERVICIO.label, "recibo por honorarios" to ReceiptKind.HONORARIOS.label,
    "boleto" to ReceiptKind.BOLETO.label, "pedido por app" to ReceiptKind.DELIVERY.label,
)

/** Resultado armado solo con el lector de comprobantes (sin internet). */
private fun ReceiptReading.toPurchase(): ScannedPurchase {
    val product = when {
        items.size == 1 -> items.single()
        items.size > 1 -> "${items.size} productos"
        kind == ReceiptKind.SERVICIO -> "Recibo de ${merchant ?: "servicio"}"
        kind == ReceiptKind.YAPE || kind == ReceiptKind.PLIN -> "Pago a ${merchant ?: "contacto"}"
        else -> kind.label
    }
    return ScannedPurchase(
        product = product,
        merchant = merchant ?: kind.label,
        amount = total,
        category = category ?: "Otros",
        confidence = totalConfidence.coerceIn(35, 98),
        priceEstimated = false,
        analyzedWithGemini = false,
        kind = kind.label,
        date = date,
        method = method,
    )
}

private fun optimizeImage(original: ByteArray): ByteArray {
    val bitmap = BitmapFactory.decodeByteArray(original, 0, original.size) ?: return original
    val maxSide = maxOf(bitmap.width, bitmap.height)
    val resized = if (maxSide > 1600) {
        val ratio = 1600f / maxSide
        bitmap.scale((bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    } else bitmap
    return ByteArrayOutputStream().use { stream ->
        resized.compress(Bitmap.CompressFormat.JPEG, 88, stream)
        if (resized !== bitmap) resized.recycle()
        bitmap.recycle()
        stream.toByteArray()
    }
}

private data class RemoteProduct(val name: String, val brand: String, val categories: String)

private data class CatalogProduct(
    val name: String,
    val brand: String,
    val aliases: List<String>,
    val category: String = "Comida",
    val typicalPrice: Double,
)

private val peruCatalog = listOf(
    CatalogProduct("Inca Kola 500 ml", "Inca Kola", listOf("inca kola", "inka kola"), typicalPrice = 4.0),
    CatalogProduct("Coca-Cola 500 ml", "Coca-Cola", listOf("coca cola", "coca-cola", "cocacola"), typicalPrice = 4.0),
    CatalogProduct("Pepsi 500 ml", "Pepsi", listOf("pepsi"), typicalPrice = 3.5),
    CatalogProduct("Agua San Mateo 600 ml", "San Mateo", listOf("san mateo"), typicalPrice = 2.0),
    CatalogProduct("Agua Cielo 625 ml", "Cielo", listOf("agua cielo"), typicalPrice = 1.8),
    CatalogProduct("Chocolate Sublime", "Nestlé", listOf("sublime"), typicalPrice = 2.5),
    CatalogProduct("Galletas Oreo", "Oreo", listOf("oreo"), typicalPrice = 2.5),
    CatalogProduct("Galletas Soda Field", "Field", listOf("soda field", "galletas field"), typicalPrice = 2.2),
    CatalogProduct("Galletas Casino", "Victoria", listOf("galletas casino", "casino sabor"), typicalPrice = 1.5),
    CatalogProduct("Papas Lay's", "Lay's", listOf("lay's", "lays"), typicalPrice = 4.0),
    CatalogProduct("Leche evaporada Gloria", "Gloria", listOf("leche gloria", "gloria leche"), typicalPrice = 4.5),
    CatalogProduct("Leche Pura Vida", "Pura Vida", listOf("pura vida"), typicalPrice = 3.8),
    CatalogProduct("Fideos Don Vittorio", "Don Vittorio", listOf("don vittorio"), typicalPrice = 4.2),
    CatalogProduct("Sopa instantánea Ajinomen", "Ajinomen", listOf("ajinomen"), typicalPrice = 2.0),
    CatalogProduct("Atún Florida", "Florida", listOf("atun florida", "atún florida"), typicalPrice = 6.5),
    CatalogProduct("Yogurt Gloria", "Gloria", listOf("yogurt gloria", "yoghurt gloria"), typicalPrice = 3.5),
    CatalogProduct("Galleta Cua Cua", "Nestlé", listOf("cua cua"), typicalPrice = 1.5),
    CatalogProduct("Galletas Morochas", "Nestlé", listOf("morochas"), typicalPrice = 3.0),
    CatalogProduct("Galletas Pícaras", "Winter's", listOf("picaras", "pícaras"), typicalPrice = 3.0),
    CatalogProduct("Bebida Frugos", "Frugos", listOf("frugos"), typicalPrice = 3.5),
    CatalogProduct("Gaseosa Kola Real", "KR", listOf("kola real", "gaseosa kr"), typicalPrice = 3.0),
    CatalogProduct("Bebida Sporade", "Sporade", listOf("sporade"), typicalPrice = 3.0),
    CatalogProduct("Arroz Costeño", "Costeño", listOf("arroz costeno", "arroz costeño"), typicalPrice = 5.5),
    CatalogProduct("Aceite Primor", "Primor", listOf("aceite primor"), typicalPrice = 11.5),
    CatalogProduct("Yogurt Laive", "Laive", listOf("yogurt laive", "yoghurt laive"), typicalPrice = 3.5),
)

private suspend fun lookupOpenFoodFacts(barcode: String): RemoteProduct? = withContext(Dispatchers.IO) {
    val safeBarcode = barcode.filter(Char::isDigit).takeIf { it.length in 8..14 } ?: return@withContext null
    val connection = (URL(
        "https://world.openfoodfacts.org/api/v2/product/$safeBarcode.json?fields=product_name,brands,quantity,categories_tags"
    ).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 4_000
        readTimeout = 5_000
        setRequestProperty("User-Agent", "Dinex-Mobile/1.0 (product recognition)")
    }
    try {
        if (connection.responseCode !in 200..299) return@withContext null
        val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        if (json.optInt("status") != 1) return@withContext null
        val product = json.optJSONObject("product") ?: return@withContext null
        val baseName = product.optString("product_name").trim()
        if (baseName.isBlank()) return@withContext null
        val quantity = product.optString("quantity").trim()
        val name = if (quantity.isNotBlank() && !baseName.contains(quantity, ignoreCase = true)) "$baseName $quantity" else baseName
        RemoteProduct(
            name,
            product.optString("brands").substringBefore(',').trim(),
            product.opt("categories_tags")?.toString().orEmpty(),
        )
    } finally {
        connection.disconnect()
    }
}

private fun parsePurchase(
    text: Text?,
    labels: List<ImageLabel>,
    barcode: String?,
    remote: RemoteProduct?,
): ScannedPurchase {
    val raw = text?.text.orEmpty()
    val lines = raw.lines().asSequence().map { it.trim() }.filter { it.length > 2 }.toList()
    val label = labels.maxByOrNull { it.confidence }
    val normalizedEvidence = normalizeProductText(
        (listOf(raw, remote?.name, remote?.brand, remote?.categories) + labels.map { it.text })
            .filterNotNull().asSequence().joinToString(" ")
    )
    val catalog = peruCatalog.firstOrNull { item ->
        item.aliases.any { normalizeProductText(it) in normalizedEvidence }
    }
    val merchant = lines.firstOrNull {
        it.length in 3..34 && it.any(Char::isLetter) && !it.contains("RUC", true) &&
            !it.contains("TOTAL", true) && !it.contains("BOLETA", true)
    }
    val ignoredProductWords = listOf("ruc", "boleta", "factura", "fecha", "hora", "total", "subtotal", "igv", "cajero", "cliente", "gracias", "vuelto")
    val productFromText = lines.dropWhile { it != merchant }.drop(1).firstOrNull { line ->
        line.length in 3..45 && line.any(Char::isLetter) && ignoredProductWords.none { line.contains(it, true) }
    }
    val product = catalog?.name
        ?: remote?.name?.takeIf(String::isNotBlank)
        ?: productFromText
        ?: label?.text?.replaceFirstChar { it.uppercase() }
        ?: merchant
        ?: "Compra escaneada"
    val resolvedMerchant = catalog?.brand
        ?: remote?.brand?.takeIf(String::isNotBlank)
        ?: merchant
        ?: product
    val moneyRegex = Regex("(?:S/\\.?\\s*)?(\\d{1,5}[.,]\\d{2})")
    val totalLineAmounts = lines.filter { line ->
        line.contains("TOTAL", true) || line.contains("PAGAR", true) || line.contains("IMPORTE", true)
    }.flatMap { line -> moneyRegex.findAll(line).mapNotNull { it.groupValues[1].replace(',', '.').toDoubleOrNull() }.toList() }
    val allAmounts = moneyRegex.findAll(raw).mapNotNull { it.groupValues[1].replace(',', '.').toDoubleOrNull() }.toList()
    val visibleAmount = totalLineAmounts.lastOrNull() ?: allAmounts.maxOrNull()
    val category = when {
        catalog != null -> catalog.category
        listOf("food", "drink", "restaurant", "comida", "market", "grocery", "fruit", "beverage", "snack", "bottle", "packaged").any(normalizedEvidence::contains) -> "Comida"
        listOf("bus", "taxi", "car", "transport", "gasoline", "combustible").any(normalizedEvidence::contains) -> "Transporte"
        listOf("book", "paper", "school", "office", "curso", "librería").any(normalizedEvidence::contains) -> "Estudios"
        listOf("game", "cinema", "movie", "music", "ticket").any(normalizedEvidence::contains) -> "Entretenimiento"
        else -> "Otros"
    }
    val estimatedAmount = visibleAmount ?: catalog?.let { adjustPriceForSize(it.typicalPrice, normalizedEvidence) }
        ?: estimateLocalPrice(normalizedEvidence, category)
    val confidence = when {
        barcode != null && remote != null -> 94
        catalog != null -> 89
        remote != null -> 86
        visibleAmount != null && raw.isNotBlank() -> 83
        raw.isNotBlank() -> 70
        else -> ((label?.confidence ?: .55f) * 72).toInt()
    }
    return ScannedPurchase(
        product = product,
        merchant = resolvedMerchant,
        amount = estimatedAmount,
        category = category,
        confidence = confidence.coerceIn(35, 95),
        priceEstimated = visibleAmount == null,
        analyzedWithGemini = false,
    )
}

/**
 * Une lo que dice Gemini con lo que leyó el teléfono. Si el comprobante trae una línea de total
 * muy clara y Gemini da un monto que no aparece en el papel, se queda el monto del comprobante.
 */
private fun ScannedPurchase.mergeReliableLocalData(local: ScannedPurchase, reading: ReceiptReading?): ScannedPurchase {
    val generic = product.lowercase() in setOf("producto", "producto fotografiado", "botella", "alimento", "objeto")
    val printed = reading?.amounts.orEmpty()
    val geminiOnPaper = amount != null && printed.any { kotlin.math.abs(it - amount) < 0.011 }
    val trustLocalTotal = reading != null && reading.total != null && reading.totalConfidence >= 92 && !geminiOnPaper
    return copy(
        product = if (generic && local.product != "Compra escaneada") local.product else product,
        merchant = if (merchant.isBlank() || merchant == "Producto fotografiado") local.merchant else merchant,
        amount = if (trustLocalTotal) reading!!.total else amount ?: local.amount,
        category = if (category == "Otros" && local.category != "Otros") local.category else category,
        confidence = when {
            geminiOnPaper -> maxOf(confidence, 95)
            trustLocalTotal -> reading!!.totalConfidence
            else -> maxOf(confidence, if (local.confidence >= 86) local.confidence else confidence)
        },
        priceEstimated = priceEstimated && !geminiOnPaper && !trustLocalTotal,
        kind = local.kind.ifBlank { kind },
        date = local.date ?: date,
        method = local.method ?: method,
    )
}

private fun normalizeProductText(value: String): String = Normalizer
    .normalize(value.lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private fun adjustPriceForSize(base: Double, text: String): Double = when {
    Regex("(?:2[.,]?25|2 25|3)\\s*(?:l|litro)").containsMatchIn(text) -> maxOf(base, 13.0)
    Regex("1[.,]?5\\s*(?:l|litro)").containsMatchIn(text) -> maxOf(base, 9.5)
    Regex("1\\s*(?:l|litro)").containsMatchIn(text) -> maxOf(base, 7.0)
    else -> base
}

private fun estimateLocalPrice(text: String, category: String): Double = when {
    listOf("water", "agua").any(text::contains) -> 2.0
    listOf("soda", "drink", "gaseosa").any(text::contains) -> 4.0
    listOf("snack", "chips", "galleta").any(text::contains) -> 3.5
    listOf("fruit", "fruta").any(text::contains) -> 3.0
    listOf("burger", "hamburguesa", "pizza").any(text::contains) -> 18.0
    listOf("book", "libro", "notebook", "cuaderno").any(text::contains) -> 25.0
    listOf("headphone", "audífono", "earphone").any(text::contains) -> 45.0
    listOf("keyboard", "teclado").any(text::contains) -> 70.0
    listOf("shirt", "camisa", "polo").any(text::contains) -> 40.0
    listOf("shoe", "zapatilla", "zapato").any(text::contains) -> 95.0
    else -> when (category) {
        "Comida" -> 10.0
        "Transporte" -> 5.0
        "Estudios" -> 25.0
        "Entretenimiento" -> 30.0
        else -> 20.0
    }
}
