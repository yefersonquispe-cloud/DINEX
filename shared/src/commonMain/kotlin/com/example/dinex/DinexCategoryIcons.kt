package com.example.dinex

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dinex.shared.generated.resources.Res
import dinex.shared.generated.resources.yape_logo
import org.jetbrains.compose.resources.painterResource

// ═══════════════════════════════════════════════════════════════════════════
// CATEGORÍA Y MÉTODO DE PAGO
// Iconos dibujados con trazos (outline), igual que DinexNavigationIcons,
// para que se vean nítidos en cualquier tamaño en lugar de manchones sólidos.
// ═══════════════════════════════════════════════════════════════════════════

/** Acumula las sub-rutas de un icono: contornos trazados y rellenos sólidos. */
private class IconPaths {
    val outlines = mutableListOf<PathBuilder.() -> Unit>()
    val fills = mutableListOf<PathBuilder.() -> Unit>()

    /** Contorno trazado (el color lo define el tint del Icon). */
    fun stroke(block: PathBuilder.() -> Unit) {
        outlines += block
    }

    /** Relleno sólido (el color lo define el tint del Icon). */
    fun fill(block: PathBuilder.() -> Unit) {
        fills += block
    }
}

/** Construye un icono 24×24 con contornos redondeados y rellenos opcionales. */
private fun buildIcon(strokeWidth: Float = 1.8f, block: IconPaths.() -> Unit): ImageVector {
    val paths = IconPaths().apply(block)
    return ImageVector.Builder("DinexIcon", 24.dp, 24.dp, 24f, 24f).apply {
        paths.outlines.forEach { builder ->
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = builder,
            )
        }
        paths.fills.forEach { builder -> path(fill = SolidColor(Color.Black), pathBuilder = builder) }
    }.build()
}

/** Círculo cerrado como sub-ruta (ruedas, monedas, círculos, etc.). */
private fun PathBuilder.circle(centerX: Float, centerY: Float, radius: Float) {
    val k = radius * 0.5523f
    moveTo(centerX - radius, centerY)
    curveTo(centerX - radius, centerY - k, centerX - k, centerY - radius, centerX, centerY - radius)
    curveTo(centerX + k, centerY - radius, centerX + radius, centerY - k, centerX + radius, centerY)
    curveTo(centerX + radius, centerY + k, centerX + k, centerY + radius, centerX, centerY + radius)
    curveTo(centerX - k, centerY + radius, centerX - radius, centerY + k, centerX - radius, centerY)
    close()
}

/** Iconos vectoriales por categoría, usados en el formulario de registro. */
internal object DinexCategoryIcons {

    /** Tenedor y cuchillo (Comida). */
    val Food = buildIcon {
        // Tenedor: tres púas que se unen en el mango.
        stroke { moveTo(8f, 3f); lineTo(8f, 21f) }
        stroke {
            moveTo(6f, 3f)
            lineTo(6f, 7.6f)
            curveTo(6f, 10.4f, 7.3f, 10.9f, 8f, 12.6f)
        }
        stroke {
            moveTo(10f, 3f)
            lineTo(10f, 7.6f)
            curveTo(10f, 10.4f, 8.7f, 10.9f, 8f, 12.6f)
        }
        // Cuchillo: mango recto con hoja curva.
        stroke { moveTo(15f, 21f); lineTo(15f, 12.8f) }
        stroke {
            moveTo(15f, 12.8f)
            lineTo(15f, 4.8f)
            curveTo(15f, 3.7f, 15.9f, 3f, 16.8f, 3.5f)
            curveTo(18.2f, 4.4f, 19f, 6.4f, 19f, 8.7f)
            curveTo(19f, 11f, 17.4f, 12.4f, 15f, 12.8f)
        }
    }

    /** Bus visto de frente (Transporte): carrocería, parabrisas, espejos, farolas y ruedas. */
    val Transport = buildIcon(strokeWidth = 1.6f) {
        // Carrocería frontal
        stroke {
            moveTo(6.6f, 3.4f)
            lineTo(17.4f, 3.4f)
            curveTo(18.7f, 3.4f, 19.8f, 4.5f, 19.8f, 5.8f)
            lineTo(19.8f, 18.6f)
            curveTo(19.8f, 19.5f, 19.1f, 20.2f, 18.2f, 20.2f)
            lineTo(5.8f, 20.2f)
            curveTo(4.9f, 20.2f, 4.2f, 19.5f, 4.2f, 18.6f)
            lineTo(4.2f, 5.8f)
            curveTo(4.2f, 4.5f, 5.3f, 3.4f, 6.6f, 3.4f)
            close()
        }
        // Parabrisas
        stroke {
            moveTo(8.8f, 7.6f)
            lineTo(15.2f, 7.6f)
            curveTo(16f, 7.6f, 16.6f, 8.2f, 16.6f, 9f)
            lineTo(16.6f, 12.4f)
            curveTo(16.6f, 13.2f, 16f, 13.8f, 15.2f, 13.8f)
            lineTo(8.8f, 13.8f)
            curveTo(8f, 13.8f, 7.4f, 13.2f, 7.4f, 12.4f)
            lineTo(7.4f, 9f)
            curveTo(7.4f, 8.2f, 8f, 7.6f, 8.8f, 7.6f)
            close()
        }
        // Espejos laterales
        stroke {
            moveTo(4.2f, 9.4f)
            lineTo(2.4f, 9.4f)
            lineTo(2.4f, 12.6f)
            lineTo(4.2f, 12.6f)
            moveTo(19.8f, 9.4f)
            lineTo(21.6f, 9.4f)
            lineTo(21.6f, 12.6f)
            lineTo(19.8f, 12.6f)
        }
        // Faroles
        stroke {
            moveTo(7.6f, 16.8f)
            lineTo(10.6f, 16.8f)
            moveTo(13.4f, 16.8f)
            lineTo(16.4f, 16.8f)
        }
        // Ruedas
        stroke {
            moveTo(7f, 20.2f)
            lineTo(7f, 21.6f)
            lineTo(9.4f, 21.6f)
            lineTo(9.4f, 20.2f)
            moveTo(14.6f, 20.2f)
            lineTo(14.6f, 21.6f)
            lineTo(17f, 21.6f)
            lineTo(17f, 20.2f)
        }
    }

    /** Libro abierto (Estudios). */
    val Studies = buildIcon {
        stroke {
            moveTo(12f, 7.2f)
            curveTo(10.4f, 6f, 8.4f, 5.4f, 6.3f, 5.4f)
            curveTo(5.6f, 5.4f, 5f, 6f, 5f, 6.7f)
            lineTo(5f, 17.3f)
            curveTo(5f, 18f, 5.6f, 18.6f, 6.3f, 18.6f)
            curveTo(8.4f, 18.6f, 10.4f, 19.2f, 12f, 20.4f)
            curveTo(13.6f, 19.2f, 15.6f, 18.6f, 17.7f, 18.6f)
            curveTo(18.4f, 18.6f, 19f, 18f, 19f, 17.3f)
            lineTo(19f, 6.7f)
            curveTo(19f, 6f, 18.4f, 5.4f, 17.7f, 5.4f)
            curveTo(15.6f, 5.4f, 13.6f, 6f, 12f, 7.2f)
            close()
            // Lomo central
            moveTo(12f, 7.2f)
            lineTo(12f, 20.4f)
        }
    }

    /** Círculo con triángulo Play (Entretenimiento). */
    val Entertainment = buildIcon {
        stroke {
            moveTo(12f, 4.2f)
            curveTo(7.7f, 4.2f, 4.2f, 7.7f, 4.2f, 12f)
            curveTo(4.2f, 16.3f, 7.7f, 19.8f, 12f, 19.8f)
            curveTo(16.3f, 19.8f, 19.8f, 16.3f, 19.8f, 12f)
            curveTo(19.8f, 7.7f, 16.3f, 4.2f, 12f, 4.2f)
            close()
        }
        fill {
            moveTo(10f, 8.9f)
            lineTo(15.4f, 12f)
            lineTo(10f, 15.1f)
            close()
        }
    }

    /** Paquete / caja (Otros). */
    val Others = buildIcon {
        stroke {
            moveTo(12f, 3.4f)
            lineTo(20.2f, 7.7f)
            lineTo(20.2f, 16.3f)
            lineTo(12f, 20.6f)
            lineTo(3.8f, 16.3f)
            lineTo(3.8f, 7.7f)
            close()
            // Tapa y costura
            moveTo(3.8f, 7.7f)
            lineTo(12f, 12f)
            lineTo(20.2f, 7.7f)
            moveTo(12f, 12f)
            lineTo(12f, 20.6f)
        }
    }

    /** Flecha hacia arriba en círculo (Ingreso). */
    val Income = buildIcon {
        stroke {
            moveTo(12f, 4.2f)
            curveTo(7.7f, 4.2f, 4.2f, 7.7f, 4.2f, 12f)
            curveTo(4.2f, 16.3f, 7.7f, 19.8f, 12f, 19.8f)
            curveTo(16.3f, 19.8f, 19.8f, 16.3f, 19.8f, 12f)
            curveTo(19.8f, 7.7f, 16.3f, 4.2f, 12f, 4.2f)
            close()
            moveTo(12f, 15.8f)
            lineTo(12f, 8.2f)
            moveTo(8.9f, 11.3f)
            lineTo(12f, 8.2f)
            lineTo(15.1f, 11.3f)
        }
    }

    /** Flecha hacia abajo en círculo (Gasto). */
    val Expense = buildIcon {
        stroke {
            moveTo(12f, 4.2f)
            curveTo(7.7f, 4.2f, 4.2f, 7.7f, 4.2f, 12f)
            curveTo(4.2f, 16.3f, 7.7f, 19.8f, 12f, 19.8f)
            curveTo(16.3f, 19.8f, 19.8f, 16.3f, 19.8f, 12f)
            curveTo(19.8f, 7.7f, 16.3f, 4.2f, 12f, 4.2f)
            close()
            moveTo(12f, 8.2f)
            lineTo(12f, 15.8f)
            moveTo(8.9f, 12.7f)
            lineTo(12f, 15.8f)
            lineTo(15.1f, 12.7f)
        }
    }

    /** Devuelve el icono correspondiente al nombre de categoría. */
    fun forCategory(category: String): ImageVector = when (category) {
        "Comida" -> Food
        "Transporte" -> Transport
        "Estudios" -> Studies
        "Entretenimiento" -> Entertainment
        "Otros" -> Others
        "Ingreso" -> Income
        else -> Others
    }

    /** Devuelve el color correspondiente al nombre de categoría (coincide con DinexCatalog). */
    fun colorForCategory(category: String): Color = when (category) {
        "Comida" -> Color(0xFFE66678)
        "Transporte" -> Color(0xFF8A76D4)
        "Estudios" -> Color(0xFF8E65C5)
        "Entretenimiento" -> Color(0xFFD89945)
        "Otros" -> Color(0xFF71827A)
        "Ingreso" -> Color(0xFF2ECC71)
        else -> Color(0xFF71827A)
    }
}

/** Iconos vectoriales por método de pago, usados en el formulario de registro. */
internal object DinexPaymentIcons {

    /**
     * Billete (Efectivo), al estilo del logo de referencia:
     * billete de atrás asomando arriba/derecha, billete frontal con círculo
     * central que contiene "S/" y dos puntos laterales.
     */
    val Cash = buildIcon(strokeWidth = 1.4f) {
        // Billete de atrás: solo los trazos que quedan fuera del billete frontal
        stroke {
            moveTo(3.7f, 7.5f)
            lineTo(3.7f, 5.5f)
            curveTo(3.7f, 4.62f, 4.42f, 3.9f, 5.3f, 3.9f)
            lineTo(21.1f, 3.9f)
            curveTo(21.98f, 3.9f, 22.7f, 4.62f, 22.7f, 5.5f)
            lineTo(22.7f, 14.3f)
            curveTo(22.7f, 15.18f, 22.15f, 15.75f, 21.5f, 15.83f)
        }
        // Billete frontal (esquinas redondeadas)
        stroke {
            moveTo(4.1f, 7.5f)
            lineTo(19.9f, 7.5f)
            curveTo(20.78f, 7.5f, 21.5f, 8.22f, 21.5f, 9.1f)
            lineTo(21.5f, 17.9f)
            curveTo(21.5f, 18.78f, 20.78f, 19.5f, 19.9f, 19.5f)
            lineTo(4.1f, 19.5f)
            curveTo(3.22f, 19.5f, 2.5f, 18.78f, 2.5f, 17.9f)
            lineTo(2.5f, 9.1f)
            curveTo(2.5f, 8.22f, 3.22f, 7.5f, 4.1f, 7.5f)
            close()
            // Círculo central (tangente arriba y abajo al billete)
            circle(12f, 13.5f, 4.4f)
        }
        // "S/" dentro del círculo
        stroke {
            // S
            moveTo(11.8f, 12f)
            curveTo(11.45f, 11.3f, 10.4f, 11.2f, 10f, 11.85f)
            curveTo(9.65f, 12.45f, 10.2f, 13.05f, 11f, 13.45f)
            curveTo(11.8f, 13.85f, 12.2f, 14.5f, 11.75f, 15.1f)
            curveTo(11.3f, 15.7f, 10.4f, 15.8f, 10f, 15.25f)
            // /
            moveTo(12.5f, 15.5f)
            lineTo(14f, 11.7f)
        }
        // Dos puntos laterales (rellenos, para que se noten en tamaño pequeño)
        fill {
            circle(5.4f, 13.5f, 0.85f)
            circle(18.6f, 13.5f, 0.85f)
        }
    }

    /** Teléfono (Yape). */
    val Yape = buildIcon {
        stroke {
            moveTo(7.5f, 2.8f)
            lineTo(16.5f, 2.8f)
            curveTo(17.6f, 2.8f, 18.5f, 3.7f, 18.5f, 4.8f)
            lineTo(18.5f, 19.2f)
            curveTo(18.5f, 20.3f, 17.6f, 21.2f, 16.5f, 21.2f)
            lineTo(7.5f, 21.2f)
            curveTo(6.4f, 21.2f, 5.5f, 20.3f, 5.5f, 19.2f)
            lineTo(5.5f, 4.8f)
            curveTo(5.5f, 3.7f, 6.4f, 2.8f, 7.5f, 2.8f)
            close()
            // Barra inferior
            moveTo(10.2f, 18.6f)
            lineTo(13.8f, 18.6f)
        }
    }

    /** Teléfono (Plin). */
    val Plin = buildIcon {
        stroke {
            moveTo(7.5f, 2.8f)
            lineTo(16.5f, 2.8f)
            curveTo(17.6f, 2.8f, 18.5f, 3.7f, 18.5f, 4.8f)
            lineTo(18.5f, 19.2f)
            curveTo(18.5f, 20.3f, 17.6f, 21.2f, 16.5f, 21.2f)
            lineTo(7.5f, 21.2f)
            curveTo(6.4f, 21.2f, 5.5f, 20.3f, 5.5f, 19.2f)
            lineTo(5.5f, 4.8f)
            curveTo(5.5f, 3.7f, 6.4f, 2.8f, 7.5f, 2.8f)
            close()
            // Barra inferior
            moveTo(10.2f, 18.6f)
            lineTo(13.8f, 18.6f)
        }
    }

    /** Tarjeta con chip (Tarjeta). */
    val Card = buildIcon {
        stroke {
            moveTo(4.8f, 6f)
            lineTo(19.2f, 6f)
            curveTo(20.2f, 6f, 21f, 6.8f, 21f, 7.8f)
            lineTo(21f, 16.2f)
            curveTo(21f, 17.2f, 20.2f, 18f, 19.2f, 18f)
            lineTo(4.8f, 18f)
            curveTo(3.8f, 18f, 3f, 17.2f, 3f, 16.2f)
            lineTo(3f, 7.8f)
            curveTo(3f, 6.8f, 3.8f, 6f, 4.8f, 6f)
            close()
            // Banda magnética y línea de datos
            moveTo(3f, 10.2f)
            lineTo(21f, 10.2f)
            moveTo(6.5f, 14.6f)
            lineTo(10.5f, 14.6f)
        }
    }

    /** Flechas opuestas (Transferencia). */
    val Transfer = buildIcon {
        stroke {
            moveTo(4f, 8.5f)
            lineTo(19.5f, 8.5f)
            moveTo(16f, 5f)
            lineTo(19.5f, 8.5f)
            lineTo(16f, 12f)
            moveTo(20f, 15.5f)
            lineTo(4.5f, 15.5f)
            moveTo(8f, 12f)
            lineTo(4.5f, 15.5f)
            lineTo(8f, 19f)
        }
    }

    /** Devuelve el ícono correspondiente al método de pago. */
    fun forMethod(method: String): ImageVector = when (method) {
        "Efectivo" -> Cash
        "Yape" -> Yape
        "Plin" -> Plin
        "Tarjeta" -> Card
        "Transferencia" -> Transfer
        else -> Cash
    }

    /** Color de acento por método de pago. */
    fun tintForMethod(method: String): Color = when (method) {
        "Efectivo" -> Color(0xFF1B5E20)
        "Yape" -> Color(0xFF8E44AD)
        "Plin" -> Color(0xFF0066CC)
        "Tarjeta" -> Color(0xFF3F51B5)
        "Transferencia" -> Color(0xFF0288D1)
        else -> Color(0xFF71827A)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// LOGOS DE MARCA (Yape / Plin)
// ═══════════════════════════════════════════════════════════════════════════

/** true cuando el método se muestra con su logo de marca (colores propios) y no con un ícono vectorial. */
internal fun isBrandPayment(method: String): Boolean = method == "Yape" || method == "Plin"

/**
 * Logo de la marca, en lugar de un ícono tintado de un solo color:
 * Yape = imagen oficial del logotipo (recurso `yape_logo.png`);
 * Plin = círculo celeste→azul con "plin".
 */
@Composable
internal fun PaymentBrandBadge(method: String, size: Dp) {
    val fontSize = (size.value * .32f).sp
    when (method) {
        // Logo oficial (imagen real) con esquinas ya transparentes en el PNG
        "Yape" -> Image(
            painter = painterResource(Res.drawable.yape_logo),
            contentDescription = "Yape",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size),
        )

        "Plin" -> Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF2FD9C9), Color(0xFF1C74E8)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "plin",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = fontSize,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
