package com.example.dinex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// ═══════════════════════════════════════════════════════════════════════════
// TÉRMINOS Y PRIVACIDAD (Perú)
// Basado en la Ley N.° 29733 de Protección de Datos Personales, su Reglamento
// (D.S. N.° 016-2024-JUS) y la Ley N.° 29571, Código de Protección y Defensa
// del Consumidor. Si cambias el texto, sube VERSION para volver a pedir permiso.
// ═══════════════════════════════════════════════════════════════════════════

/** Partes de un documento legal: párrafos, listas, definiciones y recuadros con subtítulo. */
internal sealed interface LegalBlock {
    data class Paragraph(val text: String) : LegalBlock
    data class Bullets(val items: List<String>) : LegalBlock
    data class Definition(val term: String, val text: String) : LegalBlock
    data class Box(val title: String, val blocks: List<LegalBlock>) : LegalBlock
}

internal data class LegalSection(val title: String, val blocks: List<LegalBlock>)

internal data class LegalDocument(val screenTitle: String, val title: String, val intro: List<String>, val sections: List<LegalSection>)

private fun p(text: String) = LegalBlock.Paragraph(text)
private fun bullets(vararg items: String) = LegalBlock.Bullets(items.toList())
private fun term(name: String, text: String) = LegalBlock.Definition(name, text)
private fun box(title: String, vararg blocks: LegalBlock) = LegalBlock.Box(title, blocks.toList())

internal object DinexLegal {
    const val VERSION = "2026-11-d"
    const val UPDATED = "13 de noviembre de 2026"

    /** Correo para ejercer derechos ARCO. Si está vacío, solo se muestran las opciones de la app. */
    const val CONTACT_EMAIL = ""

    private val contactLine = if (CONTACT_EMAIL.isNotBlank()) " También puede escribir a $CONTACT_EMAIL." else ""

    val terms = LegalDocument(
        screenTitle = "Términos y condiciones",
        title = "Términos y Condiciones de Uso de la aplicación DINEX",
        intro = listOf(
            "Los presentes Términos y Condiciones de Uso (en adelante, los “Términos”) regulan el acceso y uso de la aplicación DINEX (en adelante, la “Aplicación”), así como los derechos y obligaciones de las personas que la utilizan.",
            "El Usuario declara haber leído y entendido todas las condiciones establecidas en los presentes Términos. Cualquier persona que no acepte o se encuentre en desacuerdo con estos Términos no podrá utilizar la Aplicación.",
        ),
        sections = listOf(
            LegalSection("1. Definiciones", listOf(
                p("Los términos en mayúsculas que se encuentren aquí definidos tendrán los significados que se les atribuyen a continuación."),
                term("Aplicación", "la aplicación DINEX, disponible para Android y en la web, que permite registrar ingresos y gastos, crear metas de ahorro, consultar reportes y recibir recomendaciones financieras."),
                term("Cuenta", "el registro personal del Usuario en la Aplicación, creado con un correo electrónico y una contraseña, o mediante una cuenta de Google."),
                term("Datos Financieros", "los movimientos, metas de ahorro, presupuestos y recordatorios que el Usuario registra en la Aplicación."),
                term("Dinex IA", "el asistente de la Aplicación que responde consultas sobre las finanzas del Usuario mediante modelos de inteligencia artificial de Google (Gemini)."),
                term("Usuario", "toda persona natural que crea una Cuenta y utiliza la Aplicación."),
            )),
            LegalSection("2. Objeto", listOf(
                p("DINEX es una aplicación gratuita de finanzas personales que permite al Usuario ordenar sus ingresos y gastos, planificar metas de ahorro, consultar reportes y recibir consejos. La Aplicación funciona en Android y en la web con la misma Cuenta."),
            )),
            LegalSection("3. Aceptación y capacidad", listOf(
                p("Al crear una Cuenta, el Usuario declara que leyó y acepta estos Términos y la Política de Privacidad. Las personas menores de catorce (14) años solo podrán utilizar la Aplicación con la autorización de su madre, padre o tutor."),
            )),
            LegalSection("4. Registro y seguridad de la Cuenta", listOf(
                p("El Usuario se obliga a:"),
                bullets(
                    "Registrarse con un correo electrónico real y vigente.",
                    "Utilizar una contraseña segura y no compartirla con terceros.",
                    "Cambiar su contraseña mediante la opción “¿Olvidaste tu contraseña?” si sospecha que un tercero accedió a su Cuenta.",
                ),
                p("El Usuario es responsable de toda actividad realizada desde su Cuenta."),
            )),
            LegalSection("5. Naturaleza de la información", listOf(
                p("Los consejos, los reportes y las respuestas de Dinex IA tienen carácter orientativo. No constituyen asesoría financiera, legal ni tributaria, y no reemplazan la opinión de un profesional. Las decisiones sobre el dinero del Usuario son de su exclusiva responsabilidad."),
                p("El uso de Dinex IA está limitado a tres (3) consultas por Usuario al día. El límite se renueva cada día."),
            )),
            LegalSection("6. Información de mercado", listOf(
                p("Los tipos de cambio y los precios de criptomonedas que muestra la Aplicación provienen de servicios públicos de terceros. Son referenciales, pueden presentar retrasos y no deben utilizarse para realizar operaciones. DINEX no compra, vende ni custodia dinero ni criptomonedas."),
            )),
            LegalSection("7. Uso permitido", listOf(
                p("El Usuario se compromete a no utilizar la Aplicación para:"),
                bullets(
                    "Realizar actividades ilícitas.",
                    "Afectar el funcionamiento o la seguridad del servicio.",
                    "Intentar acceder a Cuentas de otras personas.",
                    "Eludir los límites de uso de la Aplicación, incluido el de Dinex IA.",
                ),
                p("DINEX podrá suspender las Cuentas que incumplan estas reglas."),
            )),
            LegalSection("8. Disponibilidad del servicio", listOf(
                p("DINEX procura que la Aplicación funcione de manera continua; sin embargo, el servicio puede interrumpirse por mantenimiento o por fallas de conexión. Se recomienda al Usuario exportar sus reportes de forma periódica."),
            )),
            LegalSection("9. Modificaciones", listOf(
                p("DINEX podrá modificar estos Términos. Los cambios serán informados en la Aplicación y, cuando sean relevantes, se solicitará al Usuario que los acepte nuevamente."),
            )),
            LegalSection("10. Ley aplicable y solución de controversias", listOf(
                p("Los presentes Términos se rigen por las leyes de la República del Perú, incluida la Ley N.° 29571, Código de Protección y Defensa del Consumidor. Lo anterior no impide que el Usuario acuda al INDECOPI o a las demás entidades competentes para exigir el cumplimiento de sus derechos."),
            )),
        ),
    )

    val privacy = LegalDocument(
        screenTitle = "Política de privacidad",
        title = "Política de Privacidad y Protección de Datos Personales de DINEX",
        intro = listOf(
            "DINEX valora a las personas que utilizan la aplicación DINEX (en adelante, la “Aplicación”) y está comprometido con la protección de su privacidad. Como parte de dicho compromiso, ha elaborado la presente Política de Privacidad (en adelante, la “Política”), que describe la manera en que trata la información del Usuario que califica como datos personales.",
            "Esta Política se rige por la Ley N.° 29733, Ley de Protección de Datos Personales, y su Reglamento, aprobado por el Decreto Supremo N.° 016-2024-JUS.",
        ),
        sections = listOf(
            LegalSection("1. Tratamiento de datos personales", listOf(
                box("1.1. Responsable del tratamiento",
                    p("El responsable del tratamiento de los datos personales del Usuario es DINEX."),
                ),
                box("1.2. Información que recolectamos",
                    p("DINEX recolecta la información necesaria para brindar el servicio:"),
                    bullets(
                        "Nombres y apellidos.",
                        "Correo electrónico.",
                        "Movimientos, metas de ahorro, presupuestos y recordatorios registrados por el Usuario.",
                        "Cantidad de consultas diarias realizadas a Dinex IA, para aplicar el límite de uso.",
                    ),
                    p("Solo con la autorización del Usuario, DINEX también trata:"),
                    bullets(
                        "La ubicación de cada gasto.",
                        "Fotografías de comprobantes de pago.",
                        "La voz, para registrar gastos por comando de voz.",
                        "Los avisos de pago de Yape, Plin y bancos recibidos en el teléfono.",
                    ),
                    p("La contraseña es protegida por Firebase Authentication (Google). DINEX no tiene acceso a ella ni la almacena."),
                ),
                box("1.3. Datos sensibles",
                    p("La legislación peruana considera los ingresos económicos como datos sensibles. Por ello, DINEX solicita el consentimiento expreso y por escrito del Usuario, mediante la casilla de aceptación al crear la Cuenta, y los protege con medidas de seguridad reforzadas."),
                ),
                box("1.4. Finalidades del tratamiento",
                    p("Los datos personales se utilizan únicamente para:"),
                    bullets(
                        "Guardar y sincronizar las finanzas del Usuario entre sus dispositivos.",
                        "Mostrar reportes, el mapa de calor y las alertas de presupuesto.",
                        "Enviar recordatorios y consejos de ahorro.",
                        "Responder las consultas realizadas a Dinex IA.",
                    ),
                    p("DINEX no vende los datos del Usuario, no los utiliza con fines publicitarios y no los comparte con bancos ni con empresas de cobranza."),
                ),
                box("1.5. Permisos del dispositivo",
                    bullets(
                        "Cámara: escanear comprobantes y tomar la foto de perfil.",
                        "Micrófono: registrar gastos por voz y activar “Oye Dinex”.",
                        "Ubicación: registrar el lugar de cada gasto para el mapa de calor.",
                        "Notificaciones: enviar alertas de presupuesto y recordatorios de ahorro.",
                        "Acceso a notificaciones: leer únicamente los avisos de pago de Yape, Plin y bancos para registrarlos automáticamente.",
                    ),
                    p("Cada permiso es opcional y puede retirarse en cualquier momento desde los ajustes del teléfono."),
                ),
            )),
            LegalSection("2. Almacenamiento y seguridad", listOf(
                box("2.1. Lugar de almacenamiento",
                    p("La información se guarda en el dispositivo del Usuario y en Firebase (Google Cloud): la Cuenta en Firebase Authentication y los datos en Cloud Firestore, en servidores ubicados en Santiago de Chile y otros países. Ello constituye un flujo transfronterizo de datos permitido por la ley, con proveedores que aplican estándares internacionales de seguridad."),
                ),
                box("2.2. Servicios de terceros",
                    p("Dinex IA utiliza Gemini Nano dentro del teléfono cuando está disponible. Algunas funciones, como la lectura de comprobantes, pueden enviar a Google Gemini únicamente la imagen necesaria. La ilustración de cada meta se genera en el teléfono, sin conexión. El mapa utiliza OpenStreetMap y la información de mercado proviene de servicios públicos de precios; a ninguno de ellos se envía el nombre ni el correo del Usuario."),
                ),
                box("2.3. Medidas de seguridad",
                    p("Toda la información viaja cifrada (HTTPS). Las reglas de acceso de Firebase garantizan que cada Cuenta solo pueda leer y modificar sus propios datos. Las ilustraciones de las metas y la foto de perfil se conservan únicamente en el dispositivo del Usuario."),
                ),
                box("2.4. Plazo de conservación",
                    p("Los datos se conservan mientras la Cuenta permanezca activa. Si el Usuario elimina su Cuenta, DINEX borra la Cuenta y sus datos de la nube y del dispositivo."),
                ),
            )),
            LegalSection("3. Derechos del Usuario", listOf(
                box("3.1. Derechos ARCO",
                    p("El Usuario puede ejercer en cualquier momento los derechos de:"),
                    bullets(
                        "Acceso: conocer los datos que DINEX trata.",
                        "Rectificación: corregir datos inexactos o incompletos.",
                        "Cancelación: solicitar la eliminación de sus datos.",
                        "Oposición: oponerse al tratamiento de sus datos.",
                    ),
                    p("Desde la Aplicación puede ver y corregir su información, exportar un reporte y eliminar su Cuenta en Perfil → Eliminar mi cuenta.$contactLine Si no queda conforme, puede acudir a la Autoridad Nacional de Protección de Datos Personales del Ministerio de Justicia y Derechos Humanos."),
                ),
                box("3.2. Consentimiento",
                    p("Al marcar la casilla de aceptación, el Usuario autoriza el tratamiento de sus datos para las finalidades descritas. Puede revocar su consentimiento en cualquier momento eliminando su Cuenta."),
                ),
            )),
            LegalSection("4. Modificaciones", listOf(
                p("DINEX podrá actualizar esta Política. Los cambios serán informados en la Aplicación y, cuando sean relevantes, se solicitará al Usuario que la acepte nuevamente."),
            )),
        ),
    )
}

// Verde fijo de DINEX para la barra: no cambia con el modo oscuro, así el texto blanco siempre se lee.
private val LegalBar = Color(0xFF115B38)

/** Pantalla completa con los Términos o la Política de Privacidad, con formato de documento formal. */
@Composable
internal fun LegalDialog(initialTab: Int = 0, onDismiss: () -> Unit) {
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    val document = if (tab == 0) DinexLegal.terms else DinexLegal.privacy
    val listState = rememberLazyListState()
    LaunchedEffect(tab) { listState.scrollToItem(0) }
    Dialog(onDismissRequest = onDismiss, properties = fullScreenDialogProperties()) {
        Surface(Modifier.fillMaxSize(), color = Card) {
            Column(Modifier.fillMaxSize()) {
                // Barra superior verde con título centrado.
                Box(Modifier.fillMaxWidth().background(LegalBar).statusBarsPadding().height(56.dp), contentAlignment = Alignment.Center) {
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
                        Icon(DinexNavigationIcons.Back, "Volver", tint = Color.White)
                    }
                    Text(document.screenTitle, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().widthIn(max = 720.dp).align(Alignment.CenterHorizontally).navigationBarsPadding(),
                    state = listState,
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 36.dp),
                ) {
                    item(key = "title-$tab") {
                        Text(document.title, color = Night, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Última actualización: ${DinexLegal.UPDATED}", color = Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(18.dp))
                        document.intro.forEach { LegalParagraph(it); Spacer(Modifier.height(14.dp)) }
                    }
                    items(document.sections, key = { "$tab-${it.title}" }) { section ->
                        Text(section.title.uppercase(), color = Night, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp,
                            modifier = Modifier.padding(top = 14.dp, bottom = 12.dp))
                        LegalBlocks(section.blocks)
                    }
                    item(key = "footer-$tab") {
                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider(color = Border)
                        Spacer(Modifier.height(16.dp))
                        Text("Versión ${DinexLegal.VERSION} · Vigente desde el ${DinexLegal.UPDATED}", color = Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (tab == 0) "Ver la Política de privacidad" else "Ver los Términos y condiciones",
                            color = GreenDark, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { tab = 1 - tab }.padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegalBlocks(blocks: List<LegalBlock>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        blocks.forEach { block ->
            when (block) {
                is LegalBlock.Paragraph -> LegalParagraph(block.text)
                is LegalBlock.Bullets -> LegalBulletList(block.items)
                is LegalBlock.Definition -> Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Night)) { append(block.term) }
                        append(": ")
                        append(legalText(block.text))
                    },
                    color = NightSoft, fontSize = 15.sp, lineHeight = 23.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Justify,
                )
                is LegalBlock.Box -> Column(Modifier.fillMaxWidth().border(1.dp, Border, RoundedCornerShape(14.dp))) {
                    Text(block.title, color = Night, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp))
                    HorizontalDivider(color = Border)
                    Box(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) { LegalBlocks(block.blocks) }
                }
            }
        }
    }
}

/** Evita que "N.° 29733" quede partido en dos líneas. */
private fun legalText(text: String) = text.replace("N.° ", "N.\u2060°\u00A0")

@Composable
private fun LegalParagraph(text: String) {
    Text(legalText(text), color = NightSoft, fontSize = 15.sp, lineHeight = 23.sp, textAlign = TextAlign.Justify)
}

@Composable
private fun LegalBulletList(items: List<String>) {
    Column(Modifier.padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row {
                Text("•", color = Night, fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.width(20.dp))
                Text(legalText(item), color = NightSoft, fontSize = 15.sp, lineHeight = 23.sp)
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
