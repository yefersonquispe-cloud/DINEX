package com.example.dinex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberReceiptScanner(
    onResult: (ScannedPurchase) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val currentResult = rememberUpdatedState(onResult)
    val currentError = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    val delegate = remember {
        DinexImagePickerDelegate(
            onImage = { image ->
                scope.launch {
                    runCatching { analyzeIosPurchase(image) }
                        .onSuccess { currentResult.value(it) }
                        .onFailure { currentError.value(it.message ?: "No pudimos analizar la foto.") }
                }
            }
        ) { currentError.value("Captura cancelada.") }
    }
    return scanner@{
        if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
            currentError.value("La cámara no está disponible en este dispositivo.")
            return@scanner
        }
        val presenter = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (presenter == null) {
            currentError.value("No se pudo abrir la cámara.")
            return@scanner
        }
        val picker = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            cameraCaptureMode = UIImagePickerControllerCameraCaptureMode.UIImagePickerControllerCameraCaptureModePhoto
            allowsEditing = false
            this.delegate = delegate
        }
        presenter.presentViewController(picker, animated = true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class DinexImagePickerDelegate(
    private val onImage: (UIImage) -> Unit,
    private val onCancel: () -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(flag = true, completion = null)
        if (image != null) onImage(image) else onCancel()
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(flag = true, completion = null)
        onCancel()
    }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun analyzeIosPurchase(image: UIImage): ScannedPurchase {
    val apiKey = readIosGeminiApiKey()
    if (apiKey.isBlank()) throw IllegalStateException(
        "Configura GEMINI_API_KEY en iOS para reconocer producto, marca y precio con IA.",
    )
    val imageData: NSData = UIImageJPEGRepresentation(image, 0.82)
        ?: throw IllegalStateException("No se pudo preparar la fotografía.")
    val base64 = imageData.base64EncodedStringWithOptions(options = 0uL)
    val response = requestIosGemini(
        apiKey = apiKey,
        imageBase64 = base64,
        maxOutputTokens = 320,
        prompt = """
            Analiza esta foto como un gasto personal en Perú. Puede mostrar un producto real, su empaque, una etiqueta o un comprobante.
            Examina logotipo, colores del empaque, texto pequeño y volumen/peso. Identifica el nombre comercial más específico,
            marca o comercio, categoría y precio en soles; evita nombres genéricos como "botella" si reconoces el empaque.
            Considera marcas y presentaciones comunes del mercado peruano (por ejemplo Inca Kola, Gloria, Field, Sublime,
            Costeño, Primor, Laive, Frugos), pero solo elige una cuando exista evidencia visual real en la foto.
            Si el precio no está visible, estima un precio minorista razonable para el mercado peruano y marca priceEstimated=true. Devuelve solo JSON válido:
            {"product":"nombre","merchant":"marca o comercio","amount":12.90,"category":"Comida","confidence":85,"priceEstimated":false}
            category debe ser exactamente Comida, Transporte, Estudios, Entretenimiento u Otros.
        """.trimIndent(),
    )
    val json = response.substringAfter('{', response).substringBeforeLast('}', response)
    fun number(field: String): Double? = Regex("\"$field\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)")
        .find(json)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    val allowed = setOf("Comida", "Transporte", "Estudios", "Entretenimiento", "Otros")
    val product = json.extractJsonString("product").orEmpty().ifBlank { "Producto fotografiado" }
    val merchant = json.extractJsonString("merchant").orEmpty().ifBlank { product }
    val amount = number("amount")?.takeIf { it > 0 }
        ?: throw IllegalStateException("Gemini no detectó un precio válido.")
    return ScannedPurchase(
        product = product,
        merchant = merchant,
        amount = amount,
        category = json.extractJsonString("category")?.takeIf(allowed::contains) ?: "Otros",
        confidence = (number("confidence")?.toInt() ?: 75).coerceIn(0, 100),
        priceEstimated = Regex("\"priceEstimated\"\\s*:\\s*true", RegexOption.IGNORE_CASE).containsMatchIn(json),
        analyzedWithGemini = true,
    )
}
