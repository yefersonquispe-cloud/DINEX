package com.example.dinex

import androidx.compose.runtime.Composable

data class ScannedPurchase(
    val product: String,
    val merchant: String,
    val amount: Double?,
    val category: String,
    val confidence: Int,
    val priceEstimated: Boolean = false,
    val analyzedWithGemini: Boolean = false,
    /** Tipo de comprobante detectado (boleta, factura, Yape…); vacío si es la foto de un producto. */
    val kind: String = "",
    val date: ReceiptDate? = null,
    /** Yape, Plin, Tarjeta, Efectivo o Transferencia, si el comprobante lo dice. */
    val method: String? = null,
)

@Composable
expect fun rememberReceiptScanner(
    onResult: (ScannedPurchase) -> Unit,
    onError: (String) -> Unit,
    onAnalyzing: () -> Unit = {},
    onCancelled: () -> Unit = {},
): () -> Unit
