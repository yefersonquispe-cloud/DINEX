package com.example.dinex

import androidx.compose.ui.graphics.Color

internal val streakLevels = listOf(
    StreakLevel("Cobre", 1, Color(0xFFC97843)),
    StreakLevel("Plata", 8, Color(0xFFB9C5D2)),
    StreakLevel("Oro", 15, Color(0xFFFFC83D)),
    StreakLevel("Platino", 30, Color(0xFF78D9FF)),
    StreakLevel("Diamante", 45, Color(0xFF73B8FF)),
    StreakLevel("Esmeralda", 60, Color(0xFF38D98A)),
    StreakLevel("Rubí", 90, Color(0xFFFF405F)),
    StreakLevel("Amatista", 120, Color(0xFFB75CFF)),
    StreakLevel("Obsidiana", 150, Color(0xFFFF6B36)),
    StreakLevel("Perla", 180, Color(0xFFF2E9E4)),
    StreakLevel("Madera", 210, Color(0xFFC68A52)),
    StreakLevel("Piedra", 240, Color(0xFF949EAA)),
    StreakLevel("Acero", 270, Color(0xFFCBD5E1)),
    StreakLevel("Cristal", 330, Color(0xFF2DE2FF)),
    StreakLevel("Leyenda", 365, Color(0xFFFFD34E)),
)

internal fun streakLevel(days: Int): StreakLevel = streakLevels.lastOrNull { days >= it.startsAt } ?: streakLevels.first()

internal fun nextStreakLevel(days: Int): StreakLevel? = streakLevels.firstOrNull { it.startsAt > days }

internal val categories = listOf(
    CategoryInfo("Comida", "CO", Color(0xFFE66678), 450.0),
    CategoryInfo("Transporte", "TR", Color(0xFF8A76D4), 220.0),
    CategoryInfo("Estudios", "ES", Color(0xFF8E65C5), 180.0),
    CategoryInfo("Entretenimiento", "OC", Color(0xFFD89945), 160.0),
    CategoryInfo("Otros", "OT", Color(0xFF71827A), 200.0),
)
