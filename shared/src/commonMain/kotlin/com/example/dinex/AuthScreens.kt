package com.example.dinex

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dinex.shared.generated.resources.Res
import dinex.shared.generated.resources.dinex_mascot_ai
import org.jetbrains.compose.resources.painterResource
import kotlin.math.max

// ═══════════════════════════════════════════════════════════════════════════
// AUTHENTICATION
// Create account, sign in and validation helpers.
// ═══════════════════════════════════════════════════════════════════════════
@Composable
internal fun AuthFlow(
    accounts: List<UserAccount>,
    onCreateAccount: (UserAccount) -> Unit,
    onAuthenticated: (String) -> Unit,
) {
    var createMode by remember { mutableStateOf(accounts.isEmpty()) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val normalizedEmail = email.trim().lowercase()
    val emailValid = normalizedEmail.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
    val nameValid = name.trim().length >= 2 && name.any(Char::isLetter)
    val passwordRules = listOf(
        "Mínimo 8 caracteres" to (password.length >= 8),
        "Una letra mayúscula (A-Z)" to password.any(Char::isUpperCase),
        "Una letra minúscula (a-z)" to password.any(Char::isLowerCase),
        "Un número (0-9)" to password.any(Char::isDigit),
        "Un símbolo (ej. ! @ # $ %)" to password.any { !it.isLetterOrDigit() && !it.isWhitespace() },
    )
    val passwordValid = passwordRules.all { it.second }

    BoxWithConstraints(Modifier.fillMaxSize().background(AppBg).statusBarsPadding().navigationBarsPadding()) {
        val wideLayout = maxWidth >= DesktopBreakpoint
        DinexAuroraBackground()
        val formSide = maxWidth * .54f
        if (wideLayout) {
            AuthBrandStory(
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().width(maxWidth - formSide),
            )
        }
        LazyColumn(
            modifier = Modifier
                .align(if (wideLayout) Alignment.CenterEnd else Alignment.TopCenter)
                .fillMaxHeight()
                .then(if (wideLayout) Modifier.width(formSide) else Modifier.widthIn(max = 500.dp).fillMaxWidth())
                .imePadding(),
            // On desktop the form sits centered in its half, at a comfortable reading width.
            contentPadding = PaddingValues(
                horizontal = if (wideLayout) maxOf(32.dp, (formSide - 440.dp) / 2) else 26.dp,
                vertical = 28.dp,
            ),
            verticalArrangement = if (wideLayout) Arrangement.Center else Arrangement.Top,
        ) {
            item {
                if (!wideLayout) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("DINEX", color = Night, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 2.sp)
                            Text("Decide mejor. Ahorra más.", color = Gray, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                }
                Text(if (createMode) "Crea tu cuenta" else "Bienvenido de nuevo", color = Night, fontWeight = FontWeight.Black, fontSize = 30.sp)
                Text(
                    if (createMode) "Un espacio financiero personal, privado y hecho para ti."
                    else "Inicia sesión para continuar con tu dashboard.",
                    color = Gray,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(22.dp))
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Card.copy(alpha = .96f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                    shadowElevation = 6.dp,
                ) {
                    Column(Modifier.padding(20.dp)) {
                        if (createMode) {
                            Text("NOMBRE", color = Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it.take(50); error = null },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Juan Pérez") },
                                singleLine = true,
                                isError = attempted && !nameValid,
                                trailingIcon = { if (nameValid) Icon(DinexNavigationIcons.Check, null, tint = Lime, modifier = Modifier.size(18.dp)) },
                                shape = RoundedCornerShape(15.dp),
                            )
                            if (attempted && !nameValid) ValidationHint("Escribe un nombre válido", false)
                            Spacer(Modifier.height(12.dp))
                        }

                        Text("EMAIL", color = Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it.take(80); error = null },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("usuario@ejemplo.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            isError = (attempted || email.isNotBlank()) && !emailValid,
                            trailingIcon = { if (emailValid) Icon(DinexNavigationIcons.Check, null, tint = Lime, modifier = Modifier.size(18.dp)) },
                            shape = RoundedCornerShape(15.dp),
                        )
                        if ((attempted || email.isNotBlank()) && !emailValid) ValidationHint("Ingresa un email válido", false)
                        Spacer(Modifier.height(12.dp))

                        Text("CONTRASEÑA", color = Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it.take(64); error = null },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Tu contraseña segura") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            isError = attempted && (password.isBlank() || (createMode && !passwordValid)),
                            trailingIcon = {
                                Text(
                                    if (showPassword) "OCULTAR" else "VER",
                                    color = GreenDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.heightIn(min = 48.dp).clickable(onClickLabel = if (showPassword) "Ocultar contraseña" else "Mostrar contraseña") { showPassword = !showPassword }.wrapContentHeight().padding(6.dp),
                                )
                            },
                            shape = RoundedCornerShape(15.dp),
                        )
                        if (createMode) {
                            Spacer(Modifier.height(9.dp))
                            passwordRules.forEach { (label, valid) -> ValidationHint(label, valid) }
                            Spacer(Modifier.height(12.dp))
                            Text("CONFIRMAR CONTRASEÑA", color = Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = confirmation,
                                onValueChange = { confirmation = it.take(64); error = null },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Repítela") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                visualTransformation = PasswordVisualTransformation(),
                                isError = attempted && confirmation != password,
                                trailingIcon = { if (confirmation.isNotBlank() && confirmation == password) Icon(DinexNavigationIcons.Check, null, tint = Lime, modifier = Modifier.size(18.dp)) },
                                shape = RoundedCornerShape(15.dp),
                            )
                            if (attempted && confirmation != password) ValidationHint("Las contraseñas deben coincidir", false)
                        }

                        error?.let {
                            Spacer(Modifier.height(12.dp))
                            Surface(shape = RoundedCornerShape(13.dp), color = Red.copy(alpha = .12f), border = androidx.compose.foundation.BorderStroke(1.dp, Red.copy(alpha = .38f))) {
                                Text("!  $it", color = Red, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(11.dp))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                attempted = true
                                if (createMode) {
                                    when {
                                        !nameValid || !emailValid || !passwordValid || confirmation != password -> error = "Revisa los campos marcados antes de continuar."
                                        accounts.any { it.email == normalizedEmail } -> error = "Ya existe una cuenta con este correo."
                                        else -> onCreateAccount(UserAccount(name.trim(), normalizedEmail, credentialDigest(normalizedEmail, password)))
                                    }
                                } else {
                                    val account = accounts.firstOrNull { it.email == normalizedEmail }
                                    if (account != null && account.passwordDigest == credentialDigest(normalizedEmail, password)) onAuthenticated(normalizedEmail)
                                    else error = "Correo o contraseña incorrectos."
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(RadiusMd),
                            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
                        ) { Text(if (createMode) "Crear cuenta" else "Iniciar sesión", fontWeight = FontWeight.Bold) }
                        TextButton(
                            onClick = { createMode = !createMode; attempted = false; error = null; password = ""; confirmation = "" },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(if (createMode) "¿Ya tienes cuenta? Iniciar sesión" else "¿Primera vez? Crear nueva cuenta", color = GreenDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(15.dp))
                Text("Cuenta local de este dispositivo. No se sincroniza todavía con otros celulares ni con la web. Dinex no almacena tu contraseña visible.", color = Gray, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AuthBrandStory(modifier: Modifier = Modifier) {
    // Brand panel: the same deep green as the balance card, in light and dark mode.
    Box(
        modifier.background(Brush.linearGradient(listOf(Color(0xFF0B4229), Color(0xFF145E3A), Color(0xFF2B8456)))),
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 30.dp).size(260.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = .10f), Color.Transparent))),
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 44.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(Res.drawable.dinex_mascot_ai), "Logo Dinex", Modifier.size(40.dp), contentScale = ContentScale.Fit)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("DINEX", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 2.sp)
                    Text("Decide mejor. Ahorra más.", color = Color.White.copy(alpha = .72f), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Convierte cada sol\nen una mejor decisión.",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 40.sp,
                lineHeight = 46.sp,
                letterSpacing = (-.6).sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Controla tus gastos, crea metas y deja que Dinex te acompañe con una visión clara de tu dinero.",
                color = Color.White.copy(alpha = .8f),
                fontSize = 15.sp,
                lineHeight = 23.sp,
                modifier = Modifier.widthIn(max = 460.dp),
            )
            Spacer(Modifier.height(30.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 460.dp)) {
                AuthBenefit(DinexNavigationIcons.Wallet, "Privado", "Tus datos se quedan en tu dispositivo.")
                AuthBenefit(DinexNavigationIcons.Analysis, "Claro", "Ve en qué gastas y cuánto puedes ahorrar.")
                AuthBenefit(DinexNavigationIcons.Sparkle, "Inteligente", "Dinex IA te da consejos con tu contexto.")
            }
            Spacer(Modifier.weight(1f))
            Text("Tus finanzas, en soles y a tu ritmo.", color = Color.White.copy(alpha = .6f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun AuthBenefit(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(body, color = Color.White.copy(alpha = .72f), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
