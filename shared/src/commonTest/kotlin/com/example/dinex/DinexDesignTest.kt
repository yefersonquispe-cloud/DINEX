package com.example.dinex

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DinexDesignTest {
    @Test fun bodyTextRemainsReadableOnTintedCards() {
        val previous = DinexThemeState.dark
        try {
            for (dark in listOf(false, true)) {
                DinexThemeState.dark = dark
                val backgrounds = listOf(AppBg, Card, CardRaised, SurfaceHover) +
                    listOf(ElectricBlue, Indigo, Orange, Green).map { lerp(CardRaised, it, .06f) }
                backgrounds.forEach { assertTrue(contrast(Gray, it) >= 4.5, "Secondary text must remain legible on $it in dark=$dark") }
            }
        } finally { DinexThemeState.dark = previous }
    }

    @Test fun chatAndPrimaryControlsHaveContrastingText() {
        val previous = DinexThemeState.dark
        try {
            for (dark in listOf(false, true)) {
                DinexThemeState.dark = dark
                assertTrue(contrast(colors.onPrimaryContainer, Mint) >= 4.5)
                assertTrue(contrast(colors.onPrimary, colors.primary) >= 4.5)
                assertTrue(contrast(colors.onSecondary, colors.secondary) >= 4.5)
            }
        } finally { DinexThemeState.dark = previous }
    }

    @Test fun navigationIsVectorArtworkWithSquareViewports() {
        listOf(DinexNavigationIcons.Home, DinexNavigationIcons.Movements, DinexNavigationIcons.Plan,
            DinexNavigationIcons.Analysis, DinexNavigationIcons.Add, DinexNavigationIcons.Send).forEach {
            assertEquals(24f, it.viewportWidth)
            assertEquals(24f, it.viewportHeight)
            assertEquals(it.defaultWidth, it.defaultHeight)
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        fun luminance(color: Color): Double {
            fun linear(value: Float): Double = value.toDouble().let { if (it <= .04045) it / 12.92 else ((it + .055) / 1.055).pow(2.4) }
            return .2126 * linear(color.red) + .7152 * linear(color.green) + .0722 * linear(color.blue)
        }
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
}
