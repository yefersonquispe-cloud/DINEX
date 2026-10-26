package com.example.dinex

import androidx.compose.ui.graphics.Color

internal data class Movement(
    val id: Int,
    val title: String,
    val category: String,
    val amount: Double,
    val income: Boolean,
    val day: Int,
    val method: String,
    val location: String = "",
    val month: Int = civilDateFromEpochDays().month,
    val year: Int = civilDateFromEpochDays().year,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

internal data class PaymentReminder(
    val id: Int,
    val title: String,
    val date: String,
    val amount: Double,
)

internal data class CategoryInfo(val name: String, val code: String, val color: Color, val limit: Double)

internal data class ChatMessage(
    val text: String,
    val user: Boolean,
    val error: Boolean = false,
    val engine: GeminiEngine? = null,
)

internal data class SavingEntry(val amount: Double, val purpose: String, val target: Double, val goalId: Int? = null)

internal data class UserAccount(val name: String, val email: String, val passwordDigest: String)

internal data class StreakLevel(val name: String, val startsAt: Int, val color: Color)
