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
)

@Composable
expect fun rememberReceiptScanner(
    onResult: (ScannedPurchase) -> Unit,
    onError: (String) -> Unit,
): () -> Unit
