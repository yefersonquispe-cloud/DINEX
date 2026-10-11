@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.example.dinex

import androidx.compose.runtime.*
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject

@Composable
internal actual fun rememberFileTransfer(onImport: (TransferFile?) -> Unit, onSaved: (Boolean) -> Unit, onError: (String) -> Unit): FileTransfer {
    val importCallback = rememberUpdatedState(onImport); val savedCallback = rememberUpdatedState(onSaved); val errorCallback = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    val delegate = remember { DinexDocumentDelegate() }
    fun presenter(): UIViewController? {
        var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (controller?.presentedViewController != null) controller = controller.presentedViewController
        return controller
    }
    return object : FileTransfer {
        override fun chooseImport() {
            val controller = presenter() ?: return errorCallback.value("No pudimos abrir Archivos.")
            delegate.exportUrl = null
            delegate.onCancel = { importCallback.value(null) }
            delegate.onPick = { url ->
                scope.launch {
                    runCatching { withContext(Dispatchers.Default) {
                        val allowed = url.startAccessingSecurityScopedResource()
                        try {
                            val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(url.path.orEmpty(), null)
                            val size = (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0
                            require(size <= MAX_TRANSFER_BYTES) { "El archivo supera 20 MB." }
                            val data = NSData.dataWithContentsOfURL(url) ?: error("No pudimos leer el archivo.")
                            require(data.length <= MAX_TRANSFER_BYTES.toULong()) { "El archivo supera 20 MB." }
                            TransferFile(url.lastPathComponent ?: "movimientos.csv", data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt()) ?: ByteArray(0))
                        } finally { if (allowed) url.stopAccessingSecurityScopedResource() }
                    } }.onSuccess { importCallback.value(it) }.onFailure { errorCallback.value(it.message ?: "No pudimos leer el archivo.") }
                }
            }
            val picker = UIDocumentPickerViewController(documentTypes = listOf("public.data"), inMode = UIDocumentPickerMode.UIDocumentPickerModeImport)
            picker.delegate = delegate; picker.allowsMultipleSelection = false
            controller.presentViewController(picker, true, null)
        }
        override fun save(document: TransferDocument) {
            val controller = presenter() ?: return errorCallback.value("No pudimos abrir Archivos para guardar.")
            runCatching {
                val destination = NSTemporaryDirectory() + "dinex_${NSDate().timeIntervalSince1970.toLong()}_${document.name}"
                val data = document.bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = document.bytes.size.toULong()) }
                check(data.writeToFile(destination, true)) { "No pudimos preparar el reporte." }
                val url = NSURL.fileURLWithPath(destination)
                delegate.exportUrl = url
                delegate.onCancel = { savedCallback.value(false) }
                delegate.onPick = { savedCallback.value(true) }
                val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
                picker.delegate = delegate
                controller.presentViewController(picker, true, null)
            }.onFailure { errorCallback.value(it.message ?: "No pudimos guardar el reporte.") }
        }
    }
}

private class DinexDocumentDelegate : NSObject(), UIDocumentPickerDelegateProtocol {
    var exportUrl: NSURL? = null
    var onPick: (NSURL) -> Unit = {}
    var onCancel: () -> Unit = {}
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(onPick) ?: onCancel()
        cleanup()
    }
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { onCancel(); cleanup() }
    private fun cleanup() { exportUrl?.let { NSFileManager.defaultManager.removeItemAtURL(it, null) }; exportUrl = null }
}
