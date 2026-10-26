package com.example.dinex

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun SpendingHeatmap(places: List<SpendingPlace>, modifier: Modifier) {
    SpendingHeatmapFallback(
        places = places,
        modifier = modifier,
        message = "El mapa interactivo está disponible en Android. En web puedes revisar tus lugares y montos.",
    )
}
