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
): () -> Unit {
    val context = LocalContext.current
    val currentResult by rememberUpdatedState(onResult)
    val currentError by rememberUpdatedState(onError)
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
            val localResult = parsePurchase(text, labels, barcode, openFoodProduct)
            val geminiResult = readGeminiApiKey(context).takeIf { it.isNotBlank() }?.let { apiKey ->
                runCatching { analyzePurchaseWithGemini(context, uri, apiKey, localResult, barcode) }.getOrNull()
            }
            currentResult(geminiResult?.mergeReliableLocalData(localResult) ?: localResult)
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = pendingUri
        if (!captured || (uri == null)) return@rememberLauncherForActivityResult
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
): ScannedPurchase {
    val imageBytes = withContext(Dispatchers.IO) {
        val original = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("No se pudo leer la imagen")
        optimizeImage(original)
    }
    val prompt = """
        Analiza esta foto como un registro de gasto personal en Perú. Puede ser un producto real, su empaque, una etiqueta de precio o un recibo.
        Examina logotipo, colores del empaque, texto pequeño, volumen/peso y código de barras. Identifica el nombre comercial más
        específico, marca, variante/tamaño, rubro y precio en soles. No respondas con términos genéricos como "botella" si el empaque
        permite reconocer una marca. El análisis local encontró "${local.product}" de "${local.merchant}"${barcode?.let { " y código $it" }.orEmpty()}.
        Si el precio está visible, léelo con cuidado. Si no aparece, estima un precio minorista razonable en Perú y marca priceEstimated=true.
        La categoría debe ser exactamente una de: Comida, Transporte, Estudios, Entretenimiento, Otros.
        Devuelve exclusivamente JSON válido, sin Markdown, con esta forma:
        {"product":"nombre", "merchant":"marca o comercio", "amount":12.90, "category":"Comida", "confidence":85, "priceEstimated":false}
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
        maxOutputTokens = 300,
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
    return ScannedPurchase(
        product = product,
        merchant = merchant,
        amount = amount,
        category = category,
        confidence = json.optInt("confidence", 75).coerceIn(0, 100),
        priceEstimated = json.optBoolean("priceEstimated", false),
        analyzedWithGemini = true,
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

private fun ScannedPurchase.mergeReliableLocalData(local: ScannedPurchase): ScannedPurchase {
    val generic = product.lowercase() in setOf("producto", "producto fotografiado", "botella", "alimento", "objeto")
    return copy(
        product = if (generic && local.product != "Compra escaneada") local.product else product,
        merchant = if (merchant.isBlank() || merchant == "Producto fotografiado") local.merchant else merchant,
        amount = amount ?: local.amount,
        category = if (category == "Otros" && local.category != "Otros") local.category else category,
        confidence = maxOf(confidence, if (local.confidence >= 86) local.confidence else confidence),
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
