package com.example.dinex

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Shared, resolution-independent artwork based on the DINEX navigation reference. */
internal object DinexNavigationIcons {
    val Home = ImageVector.Builder("DinexHome", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(1.6f, 10.2f)
            lineTo(10.8f, 2.2f)
            quadTo(12f, 1.2f, 13.2f, 2.2f)
            lineTo(22.4f, 10.2f)
            quadTo(23.3f, 11.1f, 22.1f, 11.9f)
            lineTo(20.5f, 11.9f)
            lineTo(20.5f, 20.5f)
            quadTo(20.5f, 22f, 19f, 22f)
            lineTo(14.9f, 22f)
            lineTo(14.9f, 15.6f)
            quadTo(14.9f, 14.6f, 13.9f, 14.6f)
            lineTo(10.1f, 14.6f)
            quadTo(9.1f, 14.6f, 9.1f, 15.6f)
            lineTo(9.1f, 22f)
            lineTo(5f, 22f)
            quadTo(3.5f, 22f, 3.5f, 20.5f)
            lineTo(3.5f, 11.9f)
            lineTo(1.9f, 11.9f)
            quadTo(.7f, 11.1f, 1.6f, 10.2f)
            close()
        }
    }.build()

    val Movements = outline("DinexMovements") {
        roundedBar(2.5f, 13f, 6.5f, 21f)
        roundedBar(10f, 8f, 14f, 21f)
        roundedBar(17.5f, 2f, 21.5f, 21f)
    }

    val Plan = outline("DinexPlan") {
        moveTo(14f, 2f); lineTo(5f, 2f)
        quadTo(3.5f, 2f, 3.5f, 3.5f)
        lineTo(3.5f, 20.5f)
        quadTo(3.5f, 22f, 5f, 22f)
        lineTo(19f, 22f)
        quadTo(20.5f, 22f, 20.5f, 20.5f)
        lineTo(20.5f, 8.5f); lineTo(14f, 2f); close()
        moveTo(14f, 2f); lineTo(14f, 8.5f); lineTo(20.5f, 8.5f)
        moveTo(7.5f, 12f); lineTo(16.5f, 12f)
        moveTo(7.5f, 15.5f); lineTo(16.5f, 15.5f)
        moveTo(7.5f, 19f); lineTo(12.5f, 19f)
    }

    val Analysis = outline("DinexAnalysis") {
        moveTo(10.5f, 3f); lineTo(10.5f, 12.5f); lineTo(20f, 12.5f)
        curveTo(20f, 17.8f, 15.8f, 22f, 10.5f, 22f)
        curveTo(5.2f, 22f, 1f, 17.8f, 1f, 12.5f)
        curveTo(1f, 7.2f, 5.2f, 3f, 10.5f, 3f); close()
        moveTo(13.5f, 1.5f)
        curveTo(19f, 1.5f, 22.5f, 5.8f, 22.5f, 10.5f)
        lineTo(13.5f, 10.5f); close()
    }

    val Add = outline("DinexAdd", strokeWidth = 2.4f) {
        moveTo(12f, 4f); lineTo(12f, 20f)
        moveTo(4f, 12f); lineTo(20f, 12f)
    }

    val Send = outline("DinexSend") {
        moveTo(22f, 2f); lineTo(15f, 22f); lineTo(10f, 14f)
        lineTo(2f, 9f); lineTo(22f, 2f); close()
        moveTo(10f, 14f); lineTo(22f, 2f)
    }
    val Chevron = outline("DinexChevron") {
        moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f)
    }
    val Camera = outline("DinexCamera") {
        moveTo(2f, 7f); lineTo(7f, 7f); lineTo(9f, 4f); lineTo(15f, 4f)
        lineTo(17f, 7f); lineTo(22f, 7f); lineTo(22f, 20f); lineTo(2f, 20f); close()
        moveTo(17f, 13f); curveTo(17f, 19.7f, 7f, 19.7f, 7f, 13f)
        curveTo(7f, 6.3f, 17f, 6.3f, 17f, 13f); close()
    }
    val Microphone = outline("DinexMicrophone") {
        moveTo(9f, 5f); curveTo(9f, 1f, 15f, 1f, 15f, 5f)
        lineTo(15f, 12f); curveTo(15f, 16f, 9f, 16f, 9f, 12f); close()
        moveTo(5f, 11f); curveTo(5f, 21f, 19f, 21f, 19f, 11f)
        moveTo(12f, 19f); lineTo(12f, 22f)
        moveTo(8f, 22f); lineTo(16f, 22f)
    }
    val Sparkle = outline("DinexSparkle") {
        moveTo(12f, 2f); lineTo(15f, 9f); lineTo(22f, 12f)
        lineTo(15f, 15f); lineTo(12f, 22f); lineTo(9f, 15f)
        lineTo(2f, 12f); lineTo(9f, 9f); close()
    }
    val ArrowUp = outline("DinexArrowUp") {
        moveTo(12f, 21f); lineTo(12f, 3f)
        moveTo(5f, 10f); lineTo(12f, 3f); lineTo(19f, 10f)
    }
    val ArrowDown = outline("DinexArrowDown") {
        moveTo(12f, 3f); lineTo(12f, 21f)
        moveTo(5f, 14f); lineTo(12f, 21f); lineTo(19f, 14f)
    }
    val Wallet = outline("DinexWallet") {
        moveTo(3f, 5f); lineTo(19f, 5f); quadTo(21f, 5f, 21f, 7f)
        lineTo(21f, 19f); lineTo(4f, 19f); quadTo(2f, 19f, 2f, 17f)
        lineTo(2f, 5f); quadTo(2f, 3f, 4f, 3f); lineTo(17f, 3f)
        moveTo(21f, 10f); lineTo(16f, 10f); lineTo(16f, 15f); lineTo(21f, 15f)
        moveTo(18.5f, 12.5f); lineTo(18.6f, 12.5f)
    }

    // The web canvas font has no ✓ × → glyphs, so these are drawn as vectors everywhere.
    val Check = outline("DinexCheck", strokeWidth = 2.4f) {
        moveTo(4.5f, 12.5f); lineTo(9.5f, 17.5f); lineTo(19.5f, 6.5f)
    }
    val Close = outline("DinexClose", strokeWidth = 2f) {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }

    val Back = outline("DinexBack") {
        moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
    }
    /** Rising line with an arrow head: opens the live market screen. */
    val Market = outline("DinexMarket") {
        moveTo(3f, 17f); lineTo(9f, 11f); lineTo(13f, 15f); lineTo(21f, 7f)
        moveTo(15f, 7f); lineTo(21f, 7f); lineTo(21f, 13f)
    }
    val Refresh = outline("DinexRefresh") {
        moveTo(20f, 12f); curveTo(20f, 16.4f, 16.4f, 20f, 12f, 20f)
        curveTo(7.6f, 20f, 4f, 16.4f, 4f, 12f)
        curveTo(4f, 7.6f, 7.6f, 4f, 12f, 4f)
        curveTo(14.6f, 4f, 16.9f, 5.2f, 18.4f, 7.2f)
        moveTo(19f, 3f); lineTo(19f, 7.6f); lineTo(14.4f, 7.6f)
    }

    val Moon = outline("DinexMoon") {
        moveTo(20.5f, 14.5f)
        curveTo(17.5f, 15.8f, 13.8f, 15.2f, 11.4f, 12.8f)
        curveTo(9f, 10.4f, 8.3f, 6.6f, 9.7f, 3.6f)
        curveTo(5.6f, 4.7f, 2.8f, 8.4f, 3f, 12.6f)
        curveTo(3.2f, 17.4f, 7.1f, 21.1f, 11.9f, 21f)
        curveTo(15.8f, 20.9f, 19.2f, 18.3f, 20.5f, 14.5f); close()
    }
    val Sun = outline("DinexSun") {
        moveTo(16f, 12f); curveTo(16f, 17.3f, 8f, 17.3f, 8f, 12f)
        curveTo(8f, 6.7f, 16f, 6.7f, 16f, 12f); close()
        moveTo(12f, 2f); lineTo(12f, 3.5f)
        moveTo(12f, 20.5f); lineTo(12f, 22f)
        moveTo(2f, 12f); lineTo(3.5f, 12f)
        moveTo(20.5f, 12f); lineTo(22f, 12f)
        moveTo(4.9f, 4.9f); lineTo(6f, 6f)
        moveTo(18f, 18f); lineTo(19.1f, 19.1f)
        moveTo(4.9f, 19.1f); lineTo(6f, 18f)
        moveTo(18f, 6f); lineTo(19.1f, 4.9f)
    }

    fun forCode(code: String): ImageVector? = when (code) {
        "IN" -> Home
        "MO" -> Movements
        "PL" -> Plan
        "AN" -> Analysis
        "IA" -> Sparkle
        "CF" -> Settings
        else -> null
    }

    val Settings = outline("DinexSettings") {
        moveTo(9f, 2f); lineTo(15f, 2f); lineTo(16f, 5f); lineTo(19f, 5f)
        lineTo(22f, 10f); lineTo(20f, 12f); lineTo(22f, 14f); lineTo(19f, 19f)
        lineTo(16f, 19f); lineTo(15f, 22f); lineTo(9f, 22f); lineTo(8f, 19f)
        lineTo(5f, 19f); lineTo(2f, 14f); lineTo(4f, 12f); lineTo(2f, 10f)
        lineTo(5f, 5f); lineTo(8f, 5f); close()
        moveTo(16f, 12f); curveTo(16f, 17.3f, 8f, 17.3f, 8f, 12f)
        curveTo(8f, 6.7f, 16f, 6.7f, 16f, 12f); close()
    }

    private fun outline(name: String, strokeWidth: Float = 1.7f, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    private fun PathBuilder.roundedBar(left: Float, top: Float, right: Float, bottom: Float) {
        val radius = 1.1f
        moveTo(left + radius, top); lineTo(right - radius, top)
        quadTo(right, top, right, top + radius)
        lineTo(right, bottom - radius)
        quadTo(right, bottom, right - radius, bottom)
        lineTo(left + radius, bottom)
        quadTo(left, bottom, left, bottom - radius)
        lineTo(left, top + radius)
        quadTo(left, top, left + radius, top); close()
    }
}
