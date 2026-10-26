package com.example.dinex

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun SpendingHeatmap(places: List<SpendingPlace>, modifier: Modifier) {
    SpendingHeatmapFallback(
        places = places,
        modifier = modifier,
        message = "El mapa interactivo está disponible en la app para Android. Aquí puedes ver tus lugares y montos.",
    )
}
