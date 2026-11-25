package com.example.dinex

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // La página antigua de la alcancía trae aquí (?yupay=1): se abre Dinex YUPAY al entrar con la cuenta.
    if ("yupay=1" in kotlinx.browser.window.location.search) DinexQuickActions.request("YUPAY")
    ComposeViewport {
        App(webLayout = true)
    }
}