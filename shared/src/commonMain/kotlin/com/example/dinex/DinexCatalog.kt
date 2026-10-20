package com.example.dinex

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme

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

internal fun seedMovements() = listOf(
    Movement(id = 1, title = "Propina mensual", category = "Ingreso", amount = 850.0, income = true, day = 1, method = "Yape"),
    Movement(2, "Trabajo freelance", "Ingreso", 420.0, true, 4, "Transferencia"),
    Movement(3, "Almuerzo en Tecsup", "Comida", 14.5, false, 12, "Yape"),
    Movement(4, "Pasajes de la semana", "Transporte", 36.0, false, 11, "Efectivo"),
    Movement(5, "Materiales del curso", "Estudios", 58.9, false, 10, "Tarjeta"),
    Movement(6, "Salida con amigos", "Entretenimiento", 72.0, false, 8, "Yape"),
    Movement(7, "Desayuno", "Comida", 9.5, false, 7, "Efectivo"),
    Movement(8, "Videojuego en oferta", "Entretenimiento", 29.9, false, 5, "Tarjeta"),
    Movement(9, "Venta de audífonos", "Ingreso", 110.0, true, 3, "Plin"),
)

internal fun seedReminders() = listOf(
    PaymentReminder(1, "Internet móvil", "HOY", 39.9),
    PaymentReminder(2, "Spotify", "18 SEP", 20.9),
    PaymentReminder(3, "Pensión", "25 SEP", 420.0),
)

internal val colors get() = if (DinexThemeState.dark) darkColorScheme(
    primary = GreenDark,
    onPrimary = Color(0xFF103C27),
    primaryContainer = Mint,
    onPrimaryContainer = GreenDark,
    secondary = Indigo,
    onSecondary = Color(0xFF2A2044),
    secondaryContainer = CardRaised,
    onSecondaryContainer = Indigo,
    tertiary = Orange,
    onTertiary = Color(0xFF4A3019),
    tertiaryContainer = CardRaised,
    onTertiaryContainer = Orange,
    background = AppBg,
    onBackground = Night,
    surface = Card,
    onSurface = Night,
    surfaceVariant = CardRaised,
    onSurfaceVariant = Gray,
    outline = Border,
    error = Red,
    onError = Color(0xFF3E1821),
) else lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = GreenDark,
    secondary = Indigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9F7),
    onSecondaryContainer = Color(0xFF3D316E),
    tertiary = Orange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBD4),
    onTertiaryContainer = Color(0xFF704111),
    background = AppBg,
    onBackground = Night,
    surface = Card,
    onSurface = Night,
    surfaceVariant = CardRaised,
    onSurfaceVariant = Gray,
    outline = Border,
    error = Red,
    onError = Color.White,
)
