package com.example.dinex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
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
                Text("${countLabel(candidates.size, "movimiento", "movimientos")} para importar", color = Green, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
internal fun ExportReportDialog(movements: List<Movement>, owner: String, onDismiss: () -> Unit, context: ReportContext = ReportContext()) {
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
        title = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Exportar reporte", fontWeight = FontWeight.Bold, color = Night, textAlign = TextAlign.Center)
                Text(if (format == ReportFormat.CSV) "Datos simples para abrir en otras apps" else "Incluye tu plan de ahorro personalizado", color = Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        },
        text = { Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Formato: control segmentado de tres partes iguales.
            ExportLabel("Formato")
            Row(Modifier.fillMaxWidth().clip(CircleShape).background(CardRaised).border(1.dp, Border, CircleShape).padding(4.dp)) {
                ReportFormat.entries.forEach { item ->
                    val chosen = format == item
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(CircleShape).background(if (chosen) Green else Color.Transparent)
                            .clickable(enabled = !busy) { format = item },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when (item) { ReportFormat.XLSX -> "Excel"; ReportFormat.CSV -> "CSV"; ReportFormat.PDF -> "PDF" },
                            color = if (chosen) Color.White else NightSoft, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        )
                    }
                }
            }
            // Periodo: cuadrícula de dos columnas, ordenada y compacta.
            ExportLabel("Periodo")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportPeriod.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { item ->
                            val chosen = period == item
                            Box(
                                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp))
                                    .background(if (chosen) Green.copy(alpha = .12f) else Card)
                                    .border(if (chosen) 1.5.dp else 1.dp, if (chosen) Green else Border, RoundedCornerShape(14.dp))
                                    .clickable(enabled = !busy) { period = item; error = ""; message = "" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(item.label, color = if (chosen) GreenDark else NightSoft, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (period == ReportPeriod.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(from, { from = it; error = "" }, label = { Text("Desde") }, placeholder = { Text("AAAA-MM-DD") }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
                    OutlinedTextField(to, { to = it; error = "" }, label = { Text("Hasta") }, placeholder = { Text("AAAA-MM-DD") }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
                }
                if (!validRange) Text("Ingresa fechas válidas y coloca la fecha inicial antes de la final.", color = Red, fontSize = 12.sp)
            }
            // Resumen de lo que se exportará, en tres cifras centradas.
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Mint).padding(vertical = 12.dp)) {
                listOf(
                    Triple("Movimientos", "${selected.size}", Night),
                    Triple("Ingresos", money(selected.filter { it.income }.sumOf { it.amount }), GreenDark),
                    Triple("Egresos", money(selected.filterNot { it.income }.sumOf { it.amount }), Red),
                ).forEach { (label, value, tint) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, color = Gray, fontSize = 11.sp)
                        Text(value, color = tint, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    }
                }
            }
            if (selected.isEmpty()) Text("No hay movimientos en este periodo. Elige otro o registra uno primero.", color = Gray, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green); Text("Preparando y guardando tu reporte…", color = Gray, fontSize = 12.sp) }
            if (message.isNotBlank()) Text(message, color = Green, fontSize = 13.sp)
            if (error.isNotBlank()) Text(error, color = Red, fontSize = 13.sp)
        } },
        confirmButton = { Button(enabled = !busy && validRange && selected.isNotEmpty(), onClick = {
            busy = true; error = ""; message = ""
            scope.launch {
                val label = if (period == ReportPeriod.CUSTOM) "$from al $to" else period.label
                runCatching { withContext(Dispatchers.Default) { createMovementReport(selected, format, label, owner, context) } }
                    .onSuccess(files::save).onFailure { busy = false; error = it.message ?: "No pudimos preparar el reporte." }
            }
        }, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Guardar ${format.extension.uppercase()}", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cerrar", color = GreenDark) } },
    )
}

@Composable
private fun ExportLabel(text: String) {
    Text(text.uppercase(), color = Gray, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}
