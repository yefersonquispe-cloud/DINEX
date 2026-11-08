package com.example.dinex

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual suspend fun cloudRequest(method: String, url: String, body: String?, bearer: String?): CloudResponse = withContext(Dispatchers.IO) {
    require(url.startsWith("https://")) { "Solo se permiten conexiones seguras" }
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 20_000
        useCaches = false
        // HttpURLConnection no admite PATCH; las APIs de Google aceptan este encabezado.
        requestMethod = if (method == "PATCH") "POST" else method
        if (method == "PATCH") setRequestProperty("X-HTTP-Method-Override", "PATCH")
        setRequestProperty("Accept", "application/json")
        if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
        if (body != null) {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
    }
    try {
        if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        CloudResponse(status, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
    } finally {
        connection.disconnect()
    }
}
