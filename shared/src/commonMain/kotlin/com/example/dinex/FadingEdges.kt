package com.example.dinex

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Bordes que se desvanecen poco a poco en una fila deslizable, como en iOS:
 * lo que llega al borde se va apagando en vez de cortarse de golpe.
 * Cada lado solo se desvanece si hay más contenido por ese lado, con una transición suave.
 */
@Composable
internal fun Modifier.fadingEdges(state: LazyListState, edge: Dp = 36.dp): Modifier {
    val start by animateFloatAsState(if (state.canScrollBackward) 1f else 0f, tween(280), label = "borde inicio")
    val end by animateFloatAsState(if (state.canScrollForward) 1f else 0f, tween(280), label = "borde fin")
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val w = edge.toPx().coerceAtMost(size.width / 2)
            if (start > 0f) drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 1f - start), Color.Black), startX = 0f, endX = w),
                size = Size(w, size.height),
                blendMode = BlendMode.DstIn,
            )
            if (end > 0f) drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Black, Color.Black.copy(alpha = 1f - end)), startX = size.width - w, endX = size.width),
                topLeft = Offset(size.width - w, 0f),
                size = Size(w, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
}
