package com.example.dinex

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
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

// ═══════════════════════════════════════════════════════════════════════════
// AUTHENTICATION
// Create account, sign in and validation helpers.
// ═══════════════════════════════════════════════════════════════════════════
@Composable
internal fun AuthFlow(
    accounts: List<UserAccount>,
    busy: Boolean,
    serverError: String?,
    notice: String?,
    onRegister: (name: String, email: String, password: String) -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onForgotPassword: (email: String) -> Unit,
    googleAvailable: Boolean,
    onGoogleSignIn: (acceptedTerms: Boolean) -> Unit,
    googleTermsRequired: Boolean,
    onAcceptGoogleTerms: () -> Unit,
    onDeclineGoogleTerms: () -> Unit,
    onClearMessages: () -> Unit,
) {
    var createMode by remember { mutableStateOf(accounts.isEmpty()) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    var acceptedTerms by remember { mutableStateOf(false) }
    var legalTab by remember { mutableStateOf<Int?>(null) }
    var attempted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val reducedMotion = LocalDinexReducedMotion.current
    val cardRise = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) { cardRise.animateTo(1f, tween(if (reducedMotion) 0 else 620, delayMillis = if (reducedMotion) 0 else 160, easing = FastOutSlowInEasing)) }

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
    val consentMissing = attempted && !acceptedTerms

    BoxWithConstraints(Modifier.fillMaxSize().background(AppBg).statusBarsPadding().navigationBarsPadding()) {
        val wideLayout = maxWidth >= DesktopBreakpoint
        DinexAuroraBackground()
        val formSide = maxWidth * .54f
        if (wideLayout) {
            val panel = Modifier.align(Alignment.CenterStart).fillMaxHeight().width(maxWidth - formSide)
            // En la web, el panel de marca muestra una vista previa del producto (WebWorkspace.kt).
            if (LocalWebLayout.current) WebAuthShowcase(panel) else AuthBrandStory(modifier = panel)
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
                        // Logo de Dinex (el mismo de la cabecera de la app) junto al nombre.
                        Box(
                            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Mint),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(painterResource(Res.drawable.dinex_mascot_ai), "Logo Dinex", Modifier.size(40.dp), contentScale = ContentScale.Fit)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("DINEX", color = Night, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 2.sp)
                            Text("Tu dinero, con propósito.", color = Gray, fontSize = 12.sp)
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
                    modifier = Modifier.graphicsLayer {
                        alpha = cardRise.value
                        translationY = (1f - cardRise.value) * 90.dp.toPx()
                    },
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
                            trailingIcon = { PasswordEye(showPassword) { showPassword = !showPassword } },
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
                                visualTransformation = if (showConfirmation) VisualTransformation.None else PasswordVisualTransformation(),
                                isError = attempted && confirmation != password,
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (confirmation.isNotBlank() && confirmation == password) Icon(DinexNavigationIcons.Check, null, tint = Lime, modifier = Modifier.size(18.dp))
                                        PasswordEye(showConfirmation) { showConfirmation = !showConfirmation }
                                    }
                                },
                                shape = RoundedCornerShape(15.dp),
                            )
                            if (attempted && confirmation != password) ValidationHint("Las contraseñas deben coincidir", false)
                            Spacer(Modifier.height(12.dp))
                            // Consentimiento expreso y por escrito (Ley N.° 29733): la casilla empieza sin marcar.
                            Surface(
                                shape = RoundedCornerShape(15.dp),
                                color = if (consentMissing) Red.copy(alpha = .08f) else CardRaised,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (consentMissing) Red.copy(alpha = .5f) else Border),
                            ) {
                                Row(Modifier.fillMaxWidth().padding(end = 10.dp), verticalAlignment = Alignment.Top) {
                                    Checkbox(checked = acceptedTerms, onCheckedChange = { acceptedTerms = it; error = null })
                                    Column(Modifier.padding(top = 12.dp)) {
                                        Text(
                                            "Leí y acepto los Términos y la Política de Privacidad, y autorizo a Dinex a tratar mis datos personales y financieros según la Ley N.° 29733.",
                                            color = NightSoft, fontSize = 11.sp, lineHeight = 15.sp,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            LegalLink("Ver términos") { legalTab = 0 }
                                            LegalLink("Ver privacidad") { legalTab = 1 }
                                        }
                                    }
                                }
                            }
                        }

                        (error ?: serverError)?.let {
                            Spacer(Modifier.height(12.dp))
                            Surface(shape = RoundedCornerShape(13.dp), color = Red.copy(alpha = .12f), border = androidx.compose.foundation.BorderStroke(1.dp, Red.copy(alpha = .38f))) {
                                Text("!  $it", color = Red, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(11.dp))
                            }
                        }
                        notice?.let {
                            Spacer(Modifier.height(12.dp))
                            Surface(shape = RoundedCornerShape(13.dp), color = Green.copy(alpha = .12f), border = androidx.compose.foundation.BorderStroke(1.dp, Green.copy(alpha = .38f))) {
                                Text(it, color = GreenDark, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(11.dp))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            enabled = !busy,
                            onClick = {
                                attempted = true
                                onClearMessages()
                                if (createMode) {
                                    when {
                                        !nameValid || !emailValid || !passwordValid || confirmation != password -> error = "Revisa los campos marcados antes de continuar."
                                        !acceptedTerms -> error = "Para crear tu cuenta acepta los Términos y la Política de Privacidad."
                                        accounts.any { it.email == normalizedEmail } -> error = "Ya existe una cuenta con este correo. Inicia sesión."
                                        else -> onRegister(name.trim(), normalizedEmail, password)
                                    }
                                } else {
                                    if (!emailValid || password.isBlank()) error = "Escribe tu correo y tu contraseña."
                                    else onSignIn(normalizedEmail, password)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(RadiusMd),
                            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
                        ) {
                            if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            else Text(if (createMode) "Crear cuenta" else "Iniciar sesión", fontWeight = FontWeight.Bold)
                        }
                        if (googleAvailable) {
                            Spacer(Modifier.height(12.dp))
                            GoogleSignInButton(busy) {
                                // Google no usa el correo ni la contraseña: se limpian sus avisos para que se vea su respuesta.
                                onClearMessages()
                                error = null
                                attempted = false
                                onGoogleSignIn(createMode && acceptedTerms)
                            }
                        }
                        if (!createMode) {
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    onClearMessages()
                                    if (emailValid) onForgotPassword(normalizedEmail) else error = "Escribe tu correo para enviarte el enlace."
                                },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) { Text("¿Olvidaste tu contraseña?", color = Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        }
                        TextButton(
                            onClick = { createMode = !createMode; attempted = false; error = null; password = ""; confirmation = ""; onClearMessages() },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(if (createMode) "¿Ya tienes cuenta? Iniciar sesión" else "¿Primera vez? Crear nueva cuenta", color = GreenDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(15.dp))
                if (!createMode) {
                    Text("Al iniciar sesión aceptas los Términos y la Política de Privacidad vigentes.", color = Gray, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
                        LegalLink("Términos") { legalTab = 0 }
                        LegalLink("Privacidad") { legalTab = 1 }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
    if (googleTermsRequired) GoogleTermsDialog(busy, serverError, onAcceptGoogleTerms, onDeclineGoogleTerms) { legalTab = it }
    legalTab?.let { LegalDialog(it) { legalTab = null } }
}

/** Solo aparece la primera vez que alguien crea su cuenta con Google (Ley N.° 29733). */
@Composable
private fun GoogleTermsDialog(busy: Boolean, error: String?, onAccept: () -> Unit, onDecline: () -> Unit, onLegal: (Int) -> Unit) {
    var accepted by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        containerColor = Card,
        shape = RoundedCornerShape(RadiusLg),
        title = { Text("Un último paso", color = Night, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Estás creando tu cuenta de Dinex con Google. Solo te lo pedimos esta vez.", color = NightSoft, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(accepted, { accepted = it }, enabled = !busy)
                    Text(
                        "Leí y acepto los Términos y la Política de Privacidad, y autorizo a Dinex a tratar mis datos personales y financieros según la Ley N.° 29733.",
                        color = NightSoft, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Row(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegalLink("Ver términos") { onLegal(0) }
                    LegalLink("Ver privacidad") { onLegal(1) }
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(onClick = onAccept, enabled = accepted && !busy, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White)) {
                Text("Crear mi cuenta", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDecline, enabled = !busy) { Text("Cancelar", color = Gray) } },
    )
}

/** Logo oficial de Google ("G" de cuatro colores). */
private val GoogleMark: ImageVector by lazy {
    ImageVector.Builder("GoogleG", 24.dp, 24.dp, 48f, 48f).apply {
        listOf(
            0xFFEA4335 to "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z",
            0xFF4285F4 to "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z",
            0xFFFBBC05 to "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z",
            0xFF34A853 to "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z",
        ).forEach { (color, path) -> addPath(addPathNodes(path), fill = SolidColor(Color(color))) }
    }.build()
}

/** Botón de Google: logo sobre círculo blanco, brillo que recorre el botón y respuesta al tocarlo. */
@Composable
private fun GoogleSignInButton(busy: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val reduced = LocalDinexReducedMotion.current
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(busy) { if (!busy) started = false }
    val lift by animateFloatAsState(if (hovered && !busy && !reduced) 1f else 0f, tween(180), label = "botón de Google elevado")
    val sheen by rememberInfiniteTransition(label = "brillo de Google").animateFloat(
        -.4f, 1.4f, infiniteRepeatable(tween(3400, easing = LinearEasing)), label = "recorrido del brillo",
    )
    val shape = CircleShape
    Box(
        Modifier.fillMaxWidth().height(58.dp).dinexPress(interaction)
            .graphicsLayer { translationY = -lift * 2.dp.toPx(); alpha = if (busy && !started) .6f else 1f }
            .shadow((5 + lift * 9).dp, shape, ambientColor = Color.Black.copy(alpha = .35f), spotColor = Color.Black.copy(alpha = .45f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF303134), Color(0xFF131314))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .30f), Color.White.copy(alpha = .06f))), shape)
            .drawWithContent {
                drawContent()
                if (!reduced && !busy) {
                    val x = size.width * sheen
                    drawRect(Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = .13f), Color.Transparent),
                        Offset(x - 70.dp.toPx(), 0f), Offset(x + 70.dp.toPx(), size.height),
                    ))
                }
            }
            .hoverable(interaction)
            .clickable(enabled = !busy, role = Role.Button, interactionSource = interaction, indication = LocalIndication.current) { started = true; onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).padding(start = 9.dp).size(40.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            if (busy && started) CircularProgressIndicator(color = Color(0xFF4285F4), strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            else Icon(GoogleMark, null, tint = Color.Unspecified, modifier = Modifier.size(22.dp))
        }
        Text(if (busy && started) "Conectando con Google…" else "Continuar con Google", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = .1.sp)
    }
}

/** Ojito para mostrar u ocultar la contraseña. */
@Composable
private fun PasswordEye(visible: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (visible) DinexNavigationIcons.EyeOff else DinexNavigationIcons.Eye,
            contentDescription = if (visible) "Ocultar contraseña" else "Mostrar contraseña",
            tint = if (visible) GreenDark else Gray,
            modifier = Modifier.size(22.dp),
        )
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
                    Text("Tu dinero, con propósito.", color = Color.White.copy(alpha = .72f), fontSize = 12.sp)
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
                AuthBenefit(DinexNavigationIcons.Wallet, "Privado", "Solo tú puedes ver tus datos, en el celular y en la web.")
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
