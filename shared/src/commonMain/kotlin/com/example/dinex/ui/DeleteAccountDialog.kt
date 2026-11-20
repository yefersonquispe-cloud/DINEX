package com.example.dinex

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Eliminar mi cuenta: explica qué se borra y qué no, pide confirmar que no se puede deshacer
 * y vuelve a pedir la contraseña o Google (Firebase exige un inicio de sesión reciente).
 */
@Composable
internal fun DeleteAccountDialog(
    email: String,
    googleAvailable: Boolean,
    onLoadMethods: (onDone: (methods: Set<String>?, error: String?) -> Unit) -> Unit,
    onDelete: (password: String?, useGoogle: Boolean, onDone: (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var methods by remember { mutableStateOf<Set<String>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loadAttempt by remember { mutableStateOf(0) }
    var understood by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(loadAttempt) {
        methods = null
        loadError = null
        onLoadMethods { found, problem -> methods = found; loadError = problem }
    }
    val usesPassword = methods?.contains("password") == true
    val usesGoogle = methods?.contains("google.com") == true && googleAvailable
    val localOnly = methods?.isEmpty() == true
    fun delete(useGoogle: Boolean) {
        busy = true
        error = null
        onDelete(if (useGoogle) null else password, useGoogle) { problem -> busy = false; error = problem }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = AppBg) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding()
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.fillMaxWidth().widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss, enabled = !busy) {
                            Icon(DinexNavigationIcons.Back, null, tint = GreenDark, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t("Volver"), color = GreenDark, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Red.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        Icon(DinexNavigationIcons.Close, null, tint = Red, modifier = Modifier.size(28.dp))
                    }
                    Text(t("Eliminar mi cuenta"), fontWeight = FontWeight.Black, fontSize = 26.sp, color = Night)
                    Text(
                        t("Vas a eliminar la cuenta {0} y sus datos. Es permanente: no podremos recuperarla.", email),
                        color = NightSoft, fontSize = 15.sp,
                    )

                    DeleteInfoCard(t("Se borrará"), Red, DinexNavigationIcons.Close, listOf(
                        t("Tu cuenta de acceso y la posibilidad de volver a entrar con ella."),
                        t("Tus ingresos y gastos, con su lugar en el mapa, tus presupuestos, metas de ahorro y recordatorios."),
                        t("Los reportes que enviaste sobre respuestas de Dinex IA y tu conteo de consultas."),
                        t("En este teléfono: tus datos guardados, tu foto de perfil, los avisos de pago detectados, las fotos temporales de comprobantes y Oye Dinex."),
                    ))
                    DeleteInfoCard(t("Lo que no podemos borrar desde aquí"), Gray, DinexNavigationIcons.Shield, listOf(
                        t("La copia en otros teléfonos o navegadores donde usaste tu cuenta: se borra cuando DINEX se conecte a internet en ellos."),
                        t("Los archivos que exportaste (PDF, Excel o CSV): están donde los guardaste."),
                        t("Las consultas ya enviadas a Google Gemini, que siguen las condiciones de Google."),
                    ))
                    Text(t("Si quieres conservar tus movimientos, primero exporta un reporte desde Movimientos."), color = Gray, fontSize = 13.sp)

                    Surface(shape = RoundedCornerShape(RadiusLg), color = Card, border = BorderStroke(1.dp, Border)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Toda la fila (casilla y texto) marca o desmarca la confirmación.
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .toggleable(value = understood, enabled = !busy, role = Role.Checkbox) { understood = it; error = null },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(understood, onCheckedChange = null, enabled = !busy, modifier = Modifier.padding(12.dp))
                                Text(t("Entiendo que esta acción es permanente y no se puede deshacer."), color = Night, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            when {
                                loadError != null -> {
                                    Text(translatedError(loadError.orEmpty()), color = Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    OutlinedButton(onClick = { loadAttempt++ }, enabled = !busy) { Text(t("Reintentar")) }
                                }
                                methods == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = GreenDark)
                                    Spacer(Modifier.width(10.dp))
                                    Text(t("Revisando cómo confirmar que eres tú…"), color = Gray, fontSize = 13.sp)
                                }
                                else -> {
                                    if (!localOnly) Text(t("Para tu seguridad, confirma que eres tú."), color = NightSoft, fontSize = 13.sp)
                                    if (usesPassword) {
                                        OutlinedTextField(
                                            value = password,
                                            onValueChange = { password = it.take(64); error = null },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(t("Tu contraseña de DINEX")) },
                                            singleLine = true,
                                            enabled = !busy,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                            trailingIcon = { PasswordEye(showPassword) { showPassword = !showPassword } },
                                            shape = RoundedCornerShape(15.dp),
                                        )
                                        Button(
                                            onClick = { delete(useGoogle = false) },
                                            enabled = understood && password.isNotBlank() && !busy,
                                            modifier = Modifier.fillMaxWidth().height(50.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
                                        ) { Text(if (busy) t("Eliminando…") else t("Eliminar mi cuenta para siempre"), fontWeight = FontWeight.Bold) }
                                    }
                                    if (usesGoogle) {
                                        if (usesPassword) Text(t("o"), color = Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                                        Button(
                                            onClick = { delete(useGoogle = true) },
                                            enabled = understood && !busy,
                                            modifier = Modifier.fillMaxWidth().height(50.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
                                        ) { Text(if (busy) t("Eliminando…") else t("Confirmar con Google y eliminar"), fontWeight = FontWeight.Bold) }
                                    }
                                    if (localOnly) Button(
                                        onClick = { delete(useGoogle = false) },
                                        enabled = understood && !busy,
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
                                    ) { Text(if (busy) t("Eliminando…") else t("Eliminar mi cuenta para siempre"), fontWeight = FontWeight.Bold) }
                                    if (!usesPassword && !usesGoogle && !localOnly) Text(
                                        t("No pudimos confirmar tu identidad desde aquí. Escribe a dinex401@gmail.com para eliminar tu cuenta."),
                                        color = Red, fontSize = 13.sp,
                                    )
                                }
                            }
                            error?.let { Text(translatedError(it), color = Red, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red, trackColor = Red.copy(alpha = .15f))
                        }
                    }
                    TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(t("No, quiero conservar mi cuenta"), color = GreenDark, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        t("¿Dudas? Escríbenos a dinex401@gmail.com."),
                        color = Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

/** Los mensajes llegan en español; aquí se traducen, también el aviso final "Tu cuenta no se eliminó." */
private fun translatedError(message: String): String {
    val suffix = " Tu cuenta no se eliminó."
    return if (message.endsWith(suffix)) t(message.removeSuffix(suffix)) + " " + t(suffix.trim()) else t(message)
}

@Composable
private fun DeleteInfoCard(title: String, tint: Color, icon: ImageVector, lines: List<String>) {
    Surface(shape = RoundedCornerShape(RadiusLg), color = Card, border = BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title.uppercase(), color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            lines.forEach { line ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(icon, null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(line, color = Night, fontSize = 14.sp)
                }
            }
        }
    }
}
