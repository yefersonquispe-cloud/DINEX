package com.example.dinex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════
// TÉRMINOS Y PRIVACIDAD (Perú)
// Basado en la Ley N.° 29733 de Protección de Datos Personales, su Reglamento
// (D.S. N.° 016-2024-JUS) y la Ley N.° 29571, Código de Protección y Defensa
// del Consumidor. Si cambias el texto, sube VERSION para volver a pedir permiso.
// ═══════════════════════════════════════════════════════════════════════════
internal object DinexLegal {
    const val VERSION = "2026-11-c"
    const val UPDATED = "12 de noviembre de 2026"

    /** Correo para ejercer derechos ARCO. Si está vacío, solo se muestran las opciones de la app. */
    const val CONTACT_EMAIL = ""

    private val contactLine = if (CONTACT_EMAIL.isNotBlank()) " También puedes escribir a $CONTACT_EMAIL." else ""

    val terms = listOf(
        "1. Qué es Dinex" to "Dinex es una aplicación gratuita para ordenar tus finanzas personales: registrar ingresos y gastos, crear metas de ahorro, ver reportes y recibir consejos. Funciona en Android y en la web con la misma cuenta.",
        "2. Aceptación" to "Al crear tu cuenta declaras que leíste y aceptas estos Términos y la Política de Privacidad. Si no estás de acuerdo, no uses la aplicación. Si eres menor de 14 años, necesitas el permiso de tu madre, padre o tutor.",
        "3. Tu cuenta" to "Debes usar un correo real y una contraseña segura que no compartas con nadie. Eres responsable de lo que se haga con tu cuenta. Si crees que alguien entró sin permiso, cambia tu contraseña con la opción \"¿Olvidaste tu contraseña?\".",
        "4. No es asesoría financiera" to "Los consejos, el asistente Dinex IA y los reportes son orientativos. No son asesoría financiera, legal ni tributaria, y no reemplazan a un profesional. Tú decides qué hacer con tu dinero.",
        "5. Mercado en línea" to "Los tipos de cambio y precios de criptomonedas vienen de servicios públicos de terceros. Son referenciales, pueden tener retraso y no sirven para hacer operaciones. Dinex no compra, vende ni guarda dinero o criptomonedas.",
        "6. Uso correcto" to "No uses Dinex para actividades ilegales, para dañar el servicio ni para intentar entrar a cuentas de otras personas. Podemos suspender cuentas que incumplan estas reglas.",
        "7. Disponibilidad" to "Hacemos lo posible para que Dinex funcione siempre, pero puede haber pausas por mantenimiento o fallas de internet. Te recomendamos exportar tus reportes de vez en cuando.",
        "8. Cambios" to "Si cambiamos estos Términos te lo avisaremos en la aplicación y te pediremos aceptarlos de nuevo cuando el cambio sea importante.",
        "9. Ley aplicable" to "Estos Términos se rigen por las leyes de la República del Perú, incluida la Ley N.° 29571, Código de Protección y Defensa del Consumidor. Cualquier reclamo puede presentarse ante INDECOPI.",
    )

    val privacy = listOf(
        "1. Responsable" to "El responsable de tus datos es Dinex. Cumplimos la Ley N.° 29733, Ley de Protección de Datos Personales, y su Reglamento aprobado por el D.S. N.° 016-2024-JUS.",
        "2. Qué datos usamos" to "Tu nombre y correo; los movimientos, metas, presupuestos y recordatorios que registras; y, solo si das permiso, la ubicación de cada gasto, fotos de boletas, tu voz y avisos de pago de Yape, Plin o tu banco. Tu contraseña la protege Firebase de Google y Dinex nunca la ve ni la guarda.",
        "3. Datos sensibles" to "La ley peruana considera tus ingresos económicos como datos sensibles. Por eso pedimos tu consentimiento expreso y por escrito (la casilla al crear tu cuenta) y los protegemos con medidas de seguridad reforzadas.",
        "4. Para qué los usamos" to "Solo para darte el servicio: guardar y sincronizar tus finanzas, mostrarte reportes, el mapa de calor, alertas de presupuesto, recordatorios y consejos. No vendemos tus datos, no los usamos para publicidad y no los compartimos con bancos ni empresas de cobranza.",
        "5. Permisos del teléfono" to "Cámara: escanear boletas y poner tu foto. Micrófono: registrar gastos con la voz y \"Oye Dinex\". Ubicación: guardar dónde gastaste para el mapa de calor. Notificaciones: avisarte del presupuesto y recordarte ahorrar. Acceso a notificaciones: leer solo los avisos de pago de Yape, Plin y bancos para registrarlos por ti. Cada permiso es opcional y puedes quitarlo cuando quieras desde los ajustes del teléfono.",
        "6. Dónde se guardan" to "En tu equipo y en Firebase (Google Cloud): la cuenta en Firebase Authentication y tus datos en Cloud Firestore, en servidores de Santiago de Chile y otros países. Esto es un flujo transfronterizo de datos permitido por la ley, con proveedores que aplican estándares de seguridad internacionales.",
        "7. Otros servicios" to "El asistente usa Gemini Nano dentro de tu teléfono cuando está disponible. Algunas funciones, como leer boletas, pueden enviar solo la imagen necesaria a Google Gemini. La ilustración de cada meta se crea en tu teléfono, sin internet. El mapa usa OpenStreetMap y el mercado usa servicios públicos de precios; a ellos no les enviamos tu nombre ni tu correo.",
        "8. Seguridad" to "Toda la información viaja cifrada (HTTPS). Cada cuenta solo puede leer y cambiar sus propios datos gracias a reglas de acceso en Firebase. Las ilustraciones de metas y tu foto de perfil se quedan solo en tu equipo.",
        "9. Cuánto tiempo" to "Guardamos tus datos mientras tengas la cuenta. Si la eliminas, borramos tu cuenta y tus datos de la nube y de este equipo.",
        "10. Tus derechos (ARCO)" to "Puedes acceder a tus datos, rectificarlos, cancelarlos u oponerte a su uso. En la app puedes ver y corregir todo, exportar un reporte y eliminar tu cuenta desde Perfil → Eliminar mi cuenta.$contactLine Si no quedas conforme, puedes acudir a la Autoridad Nacional de Protección de Datos Personales del Ministerio de Justicia y Derechos Humanos.",
        "11. Consentimiento" to "Al marcar la casilla autorizas el tratamiento de tus datos para los fines descritos. Puedes retirar tu consentimiento en cualquier momento eliminando tu cuenta.",
    )
}

/** Puntos clave que se muestran antes del texto completo. No reemplazan el documento. */
private val termsSummary = listOf(
    "Dinex te ayuda a ordenar tus finanzas personales; no es asesoría financiera.",
    "Eres responsable de tu cuenta y de usar la aplicación de forma correcta.",
    "Se rige por las leyes del Perú y puedes presentar reclamos ante INDECOPI.",
)
private val privacySummary = listOf(
    "No vendemos tus datos ni los usamos para publicidad.",
    "Tu información viaja cifrada y solo tu cuenta puede verla.",
    "Puedes ver, corregir, exportar o eliminar tus datos cuando quieras.",
)

/** Pantalla completa con los Términos y la Política de Privacidad, con formato de documento. */
@Composable
internal fun LegalDialog(initialTab: Int = 0, onDismiss: () -> Unit) {
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(tab) { listState.scrollToItem(0) }
    val sections = if (tab == 0) DinexLegal.terms else DinexLegal.privacy
    Dialog(onDismissRequest = onDismiss, properties = fullScreenDialogProperties()) {
        Surface(Modifier.fillMaxSize(), color = AppBg) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // Barra superior
                Row(Modifier.fillMaxWidth().background(Card).padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(DinexNavigationIcons.Back, "Volver", tint = Night) }
                    Column(Modifier.weight(1f)) {
                        Text("Información legal", color = Night, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text("DINEX · República del Perú", color = Gray, fontSize = 11.sp)
                    }
                }
                LegalTabs(tab) { tab = it }
                HorizontalDivider(color = Border)

                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().widthIn(max = 720.dp).align(Alignment.CenterHorizontally),
                    state = listState,
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 32.dp),
                ) {
                    item(key = "header-$tab") {
                        Text(
                            if (tab == 0) "TÉRMINOS Y CONDICIONES" else "POLÍTICA DE PRIVACIDAD",
                            color = GreenDark, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (tab == 0) "Términos y condiciones de uso" else "Política de privacidad y protección de datos personales",
                            color = Night, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 31.sp,
                        )
                        Spacer(Modifier.height(18.dp))
                        LegalFacts(
                            listOf(
                                "Versión" to DinexLegal.VERSION,
                                "Vigente desde" to DinexLegal.UPDATED,
                                "Normativa" to if (tab == 0) "Ley N.° 29571" else "Ley N.° 29733 y D.S. N.° 016-2024-JUS",
                            ),
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                    item(key = "summary-$tab") {
                        LegalSummary(if (tab == 0) termsSummary else privacySummary)
                        Spacer(Modifier.height(28.dp))
                    }
                    item(key = "index-$tab") {
                        Text("CONTENIDO", color = Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
                        Spacer(Modifier.height(6.dp))
                        sections.forEachIndexed { index, (title, _) ->
                            val (number, name) = legalHeading(title, index)
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(onClickLabel = "Ir a $name") { scope.launch { listState.animateScrollToItem(3 + index) } }
                                    .padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(number, color = Gray, fontSize = 13.sp, modifier = Modifier.width(34.dp))
                                Text(name, color = NightSoft, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        HorizontalDivider(color = Border)
                    }
                    itemsIndexed(sections, key = { index, _ -> "section-$tab-$index" }) { index, (title, body) ->
                        val (number, name) = legalHeading(title, index)
                        Column(Modifier.fillMaxWidth().padding(vertical = 22.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(number, color = GreenDark, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(34.dp).padding(top = 1.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, color = Night, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text(body, color = NightSoft, fontSize = 14.sp, lineHeight = 22.sp)
                                }
                            }
                        }
                        if (index < sections.lastIndex) HorizontalDivider(color = Border, modifier = Modifier.padding(start = 34.dp))
                    }
                    item(key = "footer-$tab") {
                        HorizontalDivider(color = Border)
                        Spacer(Modifier.height(18.dp))
                        Text(
                            "Este documento se elaboró conforme a la legislación peruana vigente. Ante cualquier diferencia, prevalece el texto completo sobre el resumen.",
                            color = Gray, fontSize = 12.sp, lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("Versión ${DinexLegal.VERSION} · Actualizado el ${DinexLegal.UPDATED}", color = Gray, fontSize = 12.sp)
                    }
                }

                // Barra inferior
                HorizontalDivider(color = Border)
                Box(Modifier.fillMaxWidth().background(Card).padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
                    ) { Text("Entendido", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
                }
            }
        }
    }
}

/** "1. Qué es Dinex" → ("01", "Qué es Dinex"). */
private fun legalHeading(title: String, index: Int): Pair<String, String> {
    val match = Regex("^(\\d+)\\.\\s*(.*)$").find(title)
    val number = match?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)
    return number.toString().padStart(2, '0') to (match?.groupValues?.get(2) ?: title)
}

/** Pestañas sobrias con línea inferior, como las de un documento. */
@Composable
private fun LegalTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Card).padding(horizontal = 12.dp)) {
        listOf("Términos de uso", "Privacidad").forEachIndexed { index, label ->
            val active = selected == index
            Column(
                Modifier.weight(1f).clickable(onClickLabel = "Ver $label") { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label, color = if (active) Night else Gray, fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
                )
                Box(Modifier.fillMaxWidth(0.6f).height(2.dp).background(if (active) GreenDark else Color.Transparent, RoundedCornerShape(1.dp)))
            }
        }
    }
}

/** Ficha con versión, vigencia y normativa. */
@Composable
private fun LegalFacts(facts: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth().border(1.dp, Border, RoundedCornerShape(12.dp)).background(Card, RoundedCornerShape(12.dp))) {
        facts.forEachIndexed { index, (label, value) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Gray, fontSize = 13.sp, modifier = Modifier.width(110.dp))
                Text(value, color = Night, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            }
            if (index < facts.lastIndex) HorizontalDivider(color = Border, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

/** Resumen con una línea vertical a la izquierda, sin colores llamativos. */
@Composable
private fun LegalSummary(points: List<String>) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(CardRaised, RoundedCornerShape(12.dp))) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(GreenDark, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)))
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("En resumen", color = Night, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            points.forEach { point ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 8.dp).size(4.dp).background(GreenDark, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(point, color = NightSoft, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

/** Enlace de texto pequeño, usado en el registro y en la guía. */
@Composable
internal fun LegalLink(label: String, onClick: () -> Unit) {
    Text(
        label, color = GreenDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(onClickLabel = "Abrir $label", onClick = onClick).padding(vertical = 4.dp),
    )
}
