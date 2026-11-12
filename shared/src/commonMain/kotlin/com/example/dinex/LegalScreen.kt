package com.example.dinex

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

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

/** Pantalla completa con los Términos y la Política de Privacidad. */
@Composable
internal fun LegalDialog(initialTab: Int = 0, onDismiss: () -> Unit) {
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    Dialog(onDismissRequest = onDismiss, properties = fullScreenDialogProperties()) {
        Surface(Modifier.fillMaxSize(), color = AppBg) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(DinexNavigationIcons.Close, "Cerrar", tint = Night) }
                    Text("Términos y privacidad", color = Night, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Términos", "Privacidad").forEachIndexed { index, label ->
                        FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(label, fontWeight = FontWeight.SemiBold) })
                    }
                }
                val sections = if (tab == 0) DinexLegal.terms else DinexLegal.privacy
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().widthIn(max = 760.dp).align(Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            if (tab == 0) "Términos y condiciones de uso" else "Política de privacidad y protección de datos personales",
                            color = Night, fontWeight = FontWeight.Black, fontSize = 22.sp, lineHeight = 28.sp,
                        )
                        Text("Última actualización: ${DinexLegal.UPDATED} · Perú", color = Gray, fontSize = 12.sp)
                    }
                    items(sections) { (title, body) ->
                        Surface(shape = RoundedCornerShape(RadiusMd), color = Card, border = BorderStroke(1.dp, Border)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(title, color = GreenDark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(body, color = NightSoft, fontSize = 13.sp, lineHeight = 19.sp)
                            }
                        }
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(20.dp).height(50.dp),
                    shape = RoundedCornerShape(RadiusMd),
                    colors = ButtonDefaults.buttonColors(containerColor = Green),
                ) { Text("Entendido", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Enlace de texto pequeño, usado en el registro y en la guía. */
@Composable
internal fun LegalLink(label: String, onClick: () -> Unit) {
    Text(
        label, color = GreenDark, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clickable(onClickLabel = "Abrir $label", onClick = onClick).padding(vertical = 4.dp),
    )
}
