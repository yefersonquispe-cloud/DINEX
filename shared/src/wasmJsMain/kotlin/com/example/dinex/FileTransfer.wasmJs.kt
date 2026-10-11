@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class, kotlin.js.ExperimentalWasmJsInterop::class)

package com.example.dinex

import androidx.compose.runtime.*
import kotlin.io.encoding.Base64

@JsFun("(onPicked, onError) => globalThis.dinexFiles.pick(onPicked, onError)")
private external fun browserPick(onPicked: (String, String) -> Unit, onError: (String) -> Unit)
@JsFun("(name, mime, base64) => globalThis.dinexFiles.save(name, mime, base64)")
private external fun browserSave(name: String, mime: String, base64: String)

@Composable
internal actual fun rememberFileTransfer(onImport: (TransferFile?) -> Unit, onSaved: (Boolean) -> Unit, onError: (String) -> Unit): FileTransfer {
    val imported = rememberUpdatedState(onImport); val saved = rememberUpdatedState(onSaved); val error = rememberUpdatedState(onError)
    return remember { object : FileTransfer {
        override fun chooseImport() {
            runCatching { browserPick({ name, data ->
                runCatching { if (name.isBlank()) null else TransferFile(name, Base64.decode(data)) }
                    .onSuccess { imported.value(it) }.onFailure { error.value("No pudimos leer el archivo.") }
            }, { error.value(it) }) }.onFailure { error.value("El selector de archivos no está disponible. Recarga la página.") }
        }
        override fun save(document: TransferDocument) {
            runCatching { browserSave(document.name, document.mimeType, Base64.encode(document.bytes)) }
                .onSuccess { saved.value(true) }.onFailure { error.value("No pudimos descargar el reporte.") }
        }
    } }
}
