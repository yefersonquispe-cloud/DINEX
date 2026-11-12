package com.example.dinex

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dinex.shared.generated.resources.Res
import dinex.shared.generated.resources.dinex_mascot_ai
import dinex.shared.generated.resources.goal_bike
import dinex.shared.generated.resources.goal_books
import dinex.shared.generated.resources.goal_camera
import dinex.shared.generated.resources.goal_car
import dinex.shared.generated.resources.goal_coin
import dinex.shared.generated.resources.goal_dog
import dinex.shared.generated.resources.goal_game
import dinex.shared.generated.resources.goal_gift
import dinex.shared.generated.resources.goal_grad
import dinex.shared.generated.resources.goal_headphones
import dinex.shared.generated.resources.goal_house
import dinex.shared.generated.resources.goal_island
import dinex.shared.generated.resources.goal_laptop
import dinex.shared.generated.resources.goal_moto
import dinex.shared.generated.resources.goal_phone
import dinex.shared.generated.resources.goal_pig
import dinex.shared.generated.resources.goal_plane
import dinex.shared.generated.resources.goal_ring
import dinex.shared.generated.resources.goal_shield
import dinex.shared.generated.resources.goal_shirt
import dinex.shared.generated.resources.goal_shoe
import dinex.shared.generated.resources.goal_sparkles
import dinex.shared.generated.resources.goal_store
import dinex.shared.generated.resources.goal_target
import dinex.shared.generated.resources.goal_ticket
import dinex.shared.generated.resources.goal_tv
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Tema visual de una meta: ilustración principal, una de apoyo y los colores del fondo. */
internal data class GoalTheme(val label: String, val main: DrawableResource, val extra: DrawableResource?, val top: Color, val bottom: Color)

private fun theme(label: String, main: DrawableResource, extra: DrawableResource?, top: Long, bottom: Long) =
    GoalTheme(label, main, extra, Color(top), Color(bottom))

private fun plain(text: String): String = text.lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')

/** Elige la ilustración según las palabras del nombre de la meta. Funciona sin internet. */
internal fun goalTheme(name: String): GoalTheme {
    val n = plain(name)
    val tokens = n.split(Regex("[^a-z0-9ñ]+")).filter { it.isNotBlank() }.toSet()
    // Palabras cortas (pc, tv, ps5) deben coincidir completas para no confundirse con otras palabras.
    fun has(vararg words: String) = words.any { if (it.length <= 3) it in tokens else n.contains(it) }
    return when {
        has("viaje", "vacacion", "playa", "cusco", "machu", "pasaje", "avion", "tour", "paseo", "mancora", "europa") ->
            theme("Viaje", Res.drawable.goal_plane, Res.drawable.goal_island, 0xFF4FB3E8, 0xFF1C5D8C)
        has("celular", "telefono", "iphone", "smartphone", "samsung", "xiaomi", "movil", "phone") ->
            theme("Celular", Res.drawable.goal_phone, Res.drawable.goal_sparkles, 0xFF6C63D9, 0xFF2B2468)
        has("laptop", "computadora", "compu", "pc", "macbook", "tablet", "ipad") ->
            theme("Tecnología", Res.drawable.goal_laptop, Res.drawable.goal_sparkles, 0xFF3F7FD6, 0xFF17345E)
        has("moto") -> theme("Moto", Res.drawable.goal_moto, null, 0xFFE8644F, 0xFF6B1F1A)
        has("carro", "auto", "coche", "camioneta", "brevete") -> theme("Auto", Res.drawable.goal_car, null, 0xFFEF7A5B, 0xFF7A2A22)
        has("bici") -> theme("Bicicleta", Res.drawable.goal_bike, null, 0xFF59C6A0, 0xFF1D5E4A)
        has("casa", "depa", "departamento", "hogar", "terreno", "cuarto", "alquiler", "mudanza") ->
            theme("Hogar", Res.drawable.goal_house, null, 0xFFF0A65A, 0xFF7A4318)
        has("estudi", "universidad", "curso", "tecsup", "matricula", "pension", "carrera", "colegio", "maestria", "diploma") ->
            theme("Estudios", Res.drawable.goal_grad, Res.drawable.goal_books, 0xFF4E6CC9, 0xFF1A2A5C)
        has("libro") -> theme("Libros", Res.drawable.goal_books, null, 0xFF7D6BD0, 0xFF2E2563)
        has("consola", "play", "ps5", "ps4", "xbox", "nintendo", "switch", "videojuego", "juego", "gamer") ->
            theme("Juegos", Res.drawable.goal_game, Res.drawable.goal_sparkles, 0xFF8B5CF6, 0xFF2E1A66)
        has("zapatilla", "zapato", "tenis", "nike", "adidas") -> theme("Zapatillas", Res.drawable.goal_shoe, null, 0xFF46A6E0, 0xFF16466B)
        has("ropa", "polo", "casaca", "vestido", "jean", "outfit") -> theme("Ropa", Res.drawable.goal_shirt, null, 0xFF5BC0D9, 0xFF1C5566)
        has("regalo", "cumple", "navidad", "aniversario", "sorpresa") -> theme("Regalo", Res.drawable.goal_gift, Res.drawable.goal_sparkles, 0xFFE8698C, 0xFF6E1F3A)
        has("emergencia", "salud", "medic", "seguro", "imprevisto", "fondo") -> theme("Fondo de emergencia", Res.drawable.goal_shield, null, 0xFF58B88A, 0xFF1A4F38)
        has("audifono", "audio", "musica", "parlante", "guitarra") -> theme("Música", Res.drawable.goal_headphones, null, 0xFF7C7FE0, 0xFF262A66)
        has("camara", "foto", "dron") -> theme("Cámara", Res.drawable.goal_camera, null, 0xFF8D9AB0, 0xFF2D3546)
        has("perro", "gato", "mascota", "veterinari") -> theme("Mascota", Res.drawable.goal_dog, null, 0xFFF2B35C, 0xFF7A4E16)
        has("boda", "anillo", "matrimonio", "compromiso") -> theme("Boda", Res.drawable.goal_ring, Res.drawable.goal_sparkles, 0xFFD98BB8, 0xFF5E2550)
        has("negocio", "emprend", "tienda", "bodega", "mercaderia", "capital") -> theme("Negocio", Res.drawable.goal_store, Res.drawable.goal_coin, 0xFF4DB37D, 0xFF184A33)
        has("concierto", "entrada", "festival", "show", "cine") -> theme("Entradas", Res.drawable.goal_ticket, Res.drawable.goal_sparkles, 0xFFEA5E6E, 0xFF5E1A28)
        has("tele", "tv", "smart tv", "monitor") -> theme("Televisor", Res.drawable.goal_tv, null, 0xFF6C8FD6, 0xFF223A66)
        has("meta", "objetivo", "reto") -> theme("Meta", Res.drawable.goal_target, null, 0xFF4CAF7A, 0xFF174A30)
        else -> theme("Ahorro", Res.drawable.goal_pig, Res.drawable.goal_coin, 0xFF3FAF73, 0xFF114B31)
    }
}

/** Nombre de la meta para mostrar: "CELULAR" o "celular" se ven como "Celular". */
internal fun displayGoalName(name: String): String {
    val clean = name.trim()
    if (clean.isEmpty()) return "Mi ahorro"
    val shouting = clean == clean.uppercase() || clean == clean.lowercase()
    return if (!shouting) clean else clean.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) }
}

/** Distintivo sobrio de la meta para listas: su inicial con el color de su tema. */
@Composable
internal fun GoalAvatar(name: String, size: Dp) {
    val theme = goalTheme(name)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * .3f))
            .background(Brush.linearGradient(listOf(theme.top, theme.bottom))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            displayGoalName(name).take(1),
            color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * .42f).sp,
        )
    }
}

/**
 * Ilustración de la meta creada en el dispositivo: no usa internet ni cuota de IA.
 * La pila de monedas y el anillo cambian según cuánto llevas ahorrado.
 */
@Composable
internal fun GoalArtwork(goal: SavingsGoal, modifier: Modifier = Modifier, height: Dp = 200.dp) {
    val t = goalTheme(goal.name)
    val progress = if (goal.target > 0) (goal.saved / goal.target).toFloat().coerceIn(0f, 1f) else 0f
    // Monedas: con meta total, según el avance; sin meta, según el monto guardado.
    val coins = when {
        goal.target > 0 -> (1 + progress * 5).toInt().coerceIn(1, 6)
        goal.saved >= 500 -> 6; goal.saved >= 200 -> 5; goal.saved >= 100 -> 4; goal.saved >= 50 -> 3; goal.saved > 0 -> 2
        else -> 1
    }
    val loop = rememberInfiniteTransition(label = "meta")
    val float by loop.animateFloat(-7f, 7f, infiniteRepeatable(tween(1900), RepeatMode.Reverse), label = "flotar")
    val sway by loop.animateFloat(-3f, 3f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "balanceo")
    val shine by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "brillo")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "rayos")
    val pulse by loop.animateFloat(.6f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "halo")
    val aurora by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(6000), RepeatMode.Reverse), label = "aurora")

    BoxWithConstraints(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(t.top, t.bottom, t.top.copy(alpha = .9f)),
                start = Offset(0f, 600f * aurora), end = Offset(900f, 600f * (1f - aurora) + 300f))),
    ) {
        val w = maxWidth
        // Fondo mágico: aurora, rayos de luz que giran detrás del objeto, halo que late y polvo brillante que sube.
        Canvas(Modifier.fillMaxSize()) {
            val focus = Offset(size.width * .72f, size.height * .45f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFB8FFE0).copy(alpha = .22f * aurora), Color.Transparent), center = Offset(size.width * .15f, size.height * .1f), radius = size.width * .6f),
                radius = size.width * .6f, center = Offset(size.width * .15f, size.height * .1f))
            rotate(spin, focus) {
                repeat(12) { i -> drawArc(Color.White.copy(alpha = .09f), i * 30f, 11f, useCenter = true,
                    topLeft = Offset(focus.x - size.height, focus.y - size.height), size = androidx.compose.ui.geometry.Size(size.height * 2, size.height * 2)) }
            }
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .5f * pulse), Color.Transparent), center = focus, radius = size.height * .55f),
                radius = size.height * .55f, center = focus)
            drawCircle(Color.White.copy(alpha = .10f), radius = size.width * .55f, center = Offset(size.width * .2f, size.height * 1.32f))
            drawCircle(Color.Black.copy(alpha = .12f), radius = size.width * .6f, center = Offset(size.width * .92f, size.height * 1.45f))
            repeat(16) { i ->
                val phase = (shine + i / 16f) % 1f
                val x = size.width * ((i * 37 % 100) / 100f) + kotlin.math.sin((phase + i) * 6f) * 8f
                val y = size.height * (1.05f - phase * 1.1f)
                val gold = i % 3 == 0
                drawCircle((if (gold) Color(0xFFFFE08A) else Color.White).copy(alpha = kotlin.math.sin(phase * 3.14f) * .85f), radius = 1.5f + 3.5f * kotlin.math.sin(phase * 3.14f), center = Offset(x, y))
            }
            // Estrella fugaz cada pocos segundos.
            val star = (shine * 1.6f) % 1.6f
            if (star < .35f) {
                val k = star / .35f
                val head = Offset(size.width * (1.05f - k * .7f), size.height * (.05f + k * .3f))
                drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .9f)), start = Offset(head.x + 60f, head.y - 26f), end = head),
                    start = Offset(head.x + 60f, head.y - 26f), end = head, strokeWidth = 3f)
            }
        }
        // Ilustración de apoyo detrás de la principal.
        t.extra?.let {
            Image(painterResource(it), null, Modifier.size(height * .4f).align(Alignment.TopEnd).offset(x = (-12).dp, y = (10 - float * .6f).dp)
                .graphicsLayer { rotationZ = -sway * 2; alpha = .95f }, contentScale = ContentScale.Fit)
        }
        // Ilustración principal flotando.
        Image(
            painterResource(t.main), "Ilustración de ${goal.name}",
            Modifier.size(height * .62f).align(Alignment.CenterEnd).offset(x = -(w * .1f), y = float.dp - 6.dp)
                .graphicsLayer { rotationZ = sway; val s = .97f + .03f * pulse; scaleX = s; scaleY = s },
            contentScale = ContentScale.Fit,
        )
        // Mascota de Dinex con su pila de monedas: crece con tu ahorro y las monedas rebotan.
        Row(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
            Image(painterResource(Res.drawable.dinex_mascot_ai), "Dinex", Modifier.size(height * .34f).offset(y = (float * .4f).dp), contentScale = ContentScale.Fit)
            Box(Modifier.size(width = 40.dp, height = height * .5f)) {
                repeat(coins) { i ->
                    val hop = kotlin.math.max(0f, kotlin.math.sin((shine * 2f - i * .12f) * 6.28f)) * 4f
                    Image(painterResource(Res.drawable.goal_coin), null,
                        Modifier.size(30.dp).align(Alignment.BottomCenter).offset(x = (if (i % 2 == 0) 0 else 3).dp, y = (-i * 11 - hop).dp),
                        contentScale = ContentScale.Fit)
                }
            }
        }
        // Nombre de la meta y avance.
        Column(Modifier.align(Alignment.TopStart).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = .28f)) {
                Text("✨ " + t.label.uppercase(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
            }
            Text(goal.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(.58f))
            Text(if (goal.target > 0) "${(progress * 100).toInt()}% · ${money(goal.saved)} de ${money(goal.target)}" else "${money(goal.saved)} ahorrados",
                color = Color.White.copy(alpha = .9f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
