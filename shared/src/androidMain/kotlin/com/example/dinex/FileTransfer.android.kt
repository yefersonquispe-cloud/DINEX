package com.example.dinex

import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberFileTransfer(onImport: (TransferFile?) -> Unit, onSaved: (Boolean) -> Unit, onError: (String) -> Unit): FileTransfer {
    val context = LocalContext.current
    val importCallback = rememberUpdatedState(onImport); val savedCallback = rememberUpdatedState(onSaved); val errorCallback = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<TransferDocument?>(null) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) importCallback.value(null)
        else scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: uri.lastPathSegment ?: "movimientos.csv"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) {
                            val read = input.read(buffer); if (read < 0) break
                            require(output.size() + read <= MAX_TRANSFER_BYTES) { "El archivo supera 20 MB." }
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    } ?: error("No pudimos abrir el archivo seleccionado.")
                    TransferFile(name, bytes)
                }
            }.onSuccess { importCallback.value(it) }.onFailure { errorCallback.value(it.message ?: "No pudimos leer el archivo.") }
        }
    }
    val saveContract = remember {
        object : ActivityResultContracts.CreateDocument("application/octet-stream") {
            override fun createIntent(context: Context, input: String): Intent = super.createIntent(context, input).apply { type = pending?.mimeType ?: "application/octet-stream" }
        }
    }
    val save = rememberLauncherForActivityResult(saveContract) { uri ->
        val document = pending; pending = null
        if (uri == null || document == null) savedCallback.value(false)
        else scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "w")?.use { it.write(document.bytes) }
                    ?: error("No pudimos guardar en esa ubicación.")
            } }.onSuccess { savedCallback.value(true) }.onFailure { errorCallback.value(it.message ?: "No pudimos guardar el reporte.") }
        }
    }
    return object : FileTransfer {
        override fun chooseImport() { runCatching { open.launch(arrayOf("*/*")) }.onFailure { errorCallback.value("No hay un selector de archivos disponible.") } }
        override fun save(document: TransferDocument) {
            pending = document
            runCatching { save.launch(document.name) }.onFailure { pending = null; errorCallback.value("No pudimos abrir el selector para guardar.") }
        }
    }
}
