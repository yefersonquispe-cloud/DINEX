package com.example.dinex

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ImportMovementsDialog(existing: List<Movement>, onDismiss: () -> Unit, onImport: (List<Movement>) -> Unit) {
    var pasted by remember { mutableStateOf("") }
    var pasteOpen by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf("") }
    var review by remember { mutableStateOf<ImportReview?>(null) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var skipDuplicates by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val identities = remember(existing) { existing.map { it.importIdentity() }.toSet() }
    val candidates = remember(review, skipDuplicates, identities) {
        val items = review?.movements.orEmpty()
        if (skipDuplicates) items.filterNot { it.importIdentity() in identities }.distinctBy { it.importIdentity() } else items
    }
    val duplicates = review?.movements.orEmpty().size - candidates.size
    fun inspect(file: TransferFile) {
        fileName = file.name; error = ""; busy = true; review = null
        scope.launch {
            runCatching { withContext(Dispatchers.Default) { reviewImportFile(file) } }
                .onSuccess { review = it; if (it.movements.isEmpty()) error = "No encontramos movimientos válidos. El archivo debe tener fecha, descripción, monto y tipo (Ingreso o Egreso)." }
                .onFailure { error = it.message ?: "No pudimos analizar el archivo." }
            busy = false
        }
    }
    val files = rememberFileTransfer(
        onImport = { if (it == null) busy = false else inspect(it) },
        onSaved = {},
        onError = { busy = false; error = it },
    )
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() }, containerColor = Card, shape = RoundedCornerShape(RadiusLg),
        title = { Text("Importar movimientos", fontWeight = FontWeight.Bold, color = Night) },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Abre tu Excel o CSV de Yape, Plin o tu banco. Revisa la vista previa y confirma qué vas a registrar.", color = Gray, fontSize = 14.sp)
            Button(onClick = { busy = true; error = ""; files.chooseImport() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Seleccionar Excel o CSV") }
            if (fileName.isNotBlank()) Text(fileName, color = Night, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { pasteOpen = !pasteOpen }, enabled = !busy) { Text(if (pasteOpen) "Ocultar texto CSV" else "O pegar texto CSV") }
            if (pasteOpen) {
                OutlinedTextField(pasted, { pasted = it }, label = { Text("Contenido CSV") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), enabled = !busy)
                OutlinedButton(onClick = { inspect(TransferFile("movimientos.csv", pasted.encodeToByteArray())) }, enabled = !busy && pasted.isNotBlank()) { Text("Revisar texto") }
            }
            if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Preparando la vista previa…", color = Gray) }
            review?.let { result ->
                Text("${candidates.size} movimientos para importar", color = Green, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Ingresos ${money(candidates.filter { it.income }.sumOf { it.amount })}\nEgresos ${money(candidates.filterNot { it.income }.sumOf { it.amount })}", color = Night)
                Row { Checkbox(skipDuplicates, { skipDuplicates = it }); Text("Omitir movimientos repetidos", modifier = Modifier.padding(top = 12.dp), color = Night) }
                if (duplicates > 0) Text("$duplicates movimientos repetidos se omitirán.", color = Gray)
                result.warnings.forEach { Text(it, color = Orange, fontSize = 13.sp) }
                candidates.take(6).forEach { item ->
                    HorizontalDivider(color = Border)
                    Text(item.title, color = Night, fontWeight = FontWeight.SemiBold)
                    Text("${item.dateLabel()} · ${if (item.income) "Ingreso" else "Egreso"} ${money(item.amount)}\n${item.category} · ${item.method}", color = Gray, fontSize = 13.sp)
                }
                if (candidates.size > 6) Text("Y ${candidates.size - 6} movimientos más. Se conservarán sus fechas.", color = Gray, fontSize = 13.sp)
            }
            if (error.isNotBlank()) Text(error, color = Red)
            Text("Nada se guarda sin tu confirmación. Formatos: .xlsx, .csv y .tsv; hasta 20 MB.", color = Gray, fontSize = 12.sp)
        } },
        confirmButton = { Button(onClick = { onImport(candidates) }, enabled = !busy && candidates.isNotEmpty()) { Text("Importar ${candidates.size}") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}

@Composable
internal fun ExportReportDialog(movements: List<Movement>, owner: String, onDismiss: () -> Unit) {
    var format by remember { mutableStateOf(ReportFormat.XLSX) }
    var period by remember { mutableStateOf(ReportPeriod.ALL) }
    var from by remember { mutableStateOf(movements.minOfOrNull { it.dateIso() }.orEmpty()) }
    var to by remember { mutableStateOf(movements.maxOfOrNull { it.dateIso() }.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val validRange = period != ReportPeriod.CUSTOM || (from.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && to.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && parseImportDate(from) != null && parseImportDate(to) != null && from <= to)
    val selected = remember(movements, period, from, to) { reportMovements(movements, period, from, to) }
    val files = rememberFileTransfer(onImport = {}, onSaved = { success ->
        busy = false
        message = if (success) "Reporte listo. Revisa la ubicación elegida o tus descargas." else "Guardado cancelado. Puedes intentarlo de nuevo."
    }, onError = { busy = false; error = it })
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() }, containerColor = Card, shape = RoundedCornerShape(RadiusLg),
        title = { Text("Exportar reporte", fontWeight = FontWeight.Bold, color = Night) },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Elige el periodo y el formato. Guardarás un archivo real con tus movimientos.", color = Gray, fontSize = 14.sp)
            Text("Formato", color = Night, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportFormat.entries.forEach { item -> FilterChip(format == item, { format = item }, label = { Text(item.label) }, enabled = !busy) }
            }
            Text("Periodo", color = Night, fontWeight = FontWeight.Bold)
            ReportPeriod.entries.forEach { item ->
                FilterChip(period == item, { period = item; error = ""; message = "" }, label = { Text(item.label) }, enabled = !busy)
            }
            if (period == ReportPeriod.CUSTOM) {
                OutlinedTextField(from, { from = it; error = "" }, label = { Text("Desde (AAAA-MM-DD)") }, singleLine = true, enabled = !busy)
                OutlinedTextField(to, { to = it; error = "" }, label = { Text("Hasta (AAAA-MM-DD)") }, singleLine = true, enabled = !busy)
                if (!validRange) Text("Ingresa fechas válidas y coloca la fecha inicial antes de la final.", color = Red)
            }
            Text("${selected.size} movimientos", color = Night, fontWeight = FontWeight.Bold)
            Text("Ingresos: ${money(selected.filter { it.income }.sumOf { it.amount })}\nEgresos: ${money(selected.filterNot { it.income }.sumOf { it.amount })}", color = Night)
            if (selected.isEmpty()) Text("No hay movimientos en este periodo. Elige otro o registra uno primero.", color = Gray)
            if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Preparando y guardando tu reporte…", color = Gray) }
            if (message.isNotBlank()) Text(message, color = Green)
            if (error.isNotBlank()) Text(error, color = Red)
        } },
        confirmButton = { Button(enabled = !busy && validRange && selected.isNotEmpty(), onClick = {
            busy = true; error = ""; message = ""
            scope.launch {
                val label = if (period == ReportPeriod.CUSTOM) "$from al $to" else period.label
                runCatching { withContext(Dispatchers.Default) { createMovementReport(selected, format, label, owner) } }
                    .onSuccess(files::save).onFailure { busy = false; error = it.message ?: "No pudimos preparar el reporte." }
            }
        }) { Text("Guardar ${format.extension.uppercase()}") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cerrar") } },
    )
}
