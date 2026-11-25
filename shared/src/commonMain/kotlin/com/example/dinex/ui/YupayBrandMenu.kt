package com.example.dinex

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun YupayBrandMenu(expanded: Boolean, onDismiss: () -> Unit, onOpen: () -> Unit) {
    val reduced = LocalDinexReducedMotion.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else if (hovered) 1.02f else 1f,
        animationSpec = tween(if (reduced) 0 else 120), label = "menú YUPAY")
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, offset = DpOffset(0.dp, 8.dp),
        containerColor = Color.Transparent, shape = RoundedCornerShape(20.dp), shadowElevation = 0.dp, tonalElevation = 0.dp,
        modifier = Modifier.width(290.dp)) {
        AnimatedVisibility(visible = expanded, enter = fadeIn(tween(if (reduced) 0 else 140)) + scaleIn(tween(if (reduced) 0 else 140), initialScale = .94f)) {
            Row(
                Modifier.padding(3.dp).fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF1B7A4D), Color(0xFF0B3322))))
                    .border(1.dp, Color(0xFFC6F06A).copy(alpha = .45f), RoundedCornerShape(20.dp))
                    .hoverable(interaction).clickable(interaction, null, role = Role.Button, onClickLabel = "Abrir Dinex YUPAY", onClick = onOpen)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Alcancía con una moneda entrando.
                Canvas(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFC6F06A))) {
                    val ink = Color(0xFF0E3322)
                    val w = size.width
                    drawCircle(Color(0xFFE0B64A), w * .12f, Offset(w * .5f, w * .24f))
                    drawCircle(ink, w * .12f, Offset(w * .5f, w * .24f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * .04f))
                    drawRoundRect(ink, Offset(w * .28f, w * .40f), androidx.compose.ui.geometry.Size(w * .44f, w * .09f), androidx.compose.ui.geometry.CornerRadius(w * .04f))
                    drawRoundRect(ink, Offset(w * .25f, w * .52f), androidx.compose.ui.geometry.Size(w * .50f, w * .29f), androidx.compose.ui.geometry.CornerRadius(w * .11f))
                    drawRoundRect(Color(0xFFC6F06A), Offset(w * .31f, w * .67f), androidx.compose.ui.geometry.Size(w * .38f, w * .08f), androidx.compose.ui.geometry.CornerRadius(w * .04f))
                }
                Column(Modifier.weight(1f)) {
                    Text("Dinex YUPAY", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text("Tu alcancía inteligente", color = Color.White.copy(alpha = .78f), fontSize = 11.sp)
                }
                Icon(DinexNavigationIcons.Chevron, null, tint = Color(0xFFC6F06A), modifier = Modifier.size(17.dp))
            }
        }
    }
}
