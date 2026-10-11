package com.example.dinex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable internal actual fun rememberGoalIllustrator(): GoalIllustrator = remember {
    object : GoalIllustrator {
        override val isConfigured = false
        override suspend fun generate(purpose: String): String = error("Configura un servidor seguro para Gemini en web.")
    }
}
