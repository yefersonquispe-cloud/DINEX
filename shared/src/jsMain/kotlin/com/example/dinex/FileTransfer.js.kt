@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
package com.example.dinex

import androidx.compose.runtime.*
import kotlin.io.encoding.Base64

@JsName("dinexFiles")
private external object BrowserFiles {
    fun pick(onPicked: (String, String) -> Unit, onError: (String) -> Unit)
    fun save(name: String, mime: String, base64: String)
}

@Composable
internal actual fun rememberFileTransfer(onImport: (TransferFile?) -> Unit, onSaved: (Boolean) -> Unit, onError: (String) -> Unit): FileTransfer {
    val imported = rememberUpdatedState(onImport); val saved = rememberUpdatedState(onSaved); val error = rememberUpdatedState(onError)
    return remember { object : FileTransfer {
        override fun chooseImport() {
            runCatching { BrowserFiles.pick({ name, data ->
                runCatching { if (name.isBlank()) null else TransferFile(name, Base64.decode(data)) }
                    .onSuccess { imported.value(it) }.onFailure { error.value("No pudimos leer el archivo.") }
            }, { error.value(it) }) }.onFailure { error.value("El selector de archivos no está disponible. Recarga la página.") }
        }
        override fun save(document: TransferDocument) {
            runCatching { BrowserFiles.save(document.name, document.mimeType, Base64.encode(document.bytes)) }
                .onSuccess { saved.value(true) }.onFailure { error.value("No pudimos descargar el reporte.") }
        }
    } }
}
