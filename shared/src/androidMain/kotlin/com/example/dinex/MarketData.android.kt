package com.example.dinex

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual suspend fun fetchMarketText(url: String): String = withContext(Dispatchers.IO) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 15_000
        setRequestProperty("Accept", "application/json")
    }
    try {
        val status = connection.responseCode
        if (status !in 200..299) error("El mercado respondió $status")
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

internal actual fun fullScreenDialogProperties() =
    androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
