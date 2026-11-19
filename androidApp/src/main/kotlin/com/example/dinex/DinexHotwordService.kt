package com.example.dinex

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.Locale

/**
 * Oye Dinex: asistente de voz opcional y siempre visible.
 *
 * - Solo se inicia cuando la persona lo activa en Dinex (nunca solo).
 * - Mientras está activo hay una notificación permanente con el botón "Desactivar",
 *   y Android muestra su indicador de micrófono cuando se escucha.
 * - La escucha manos libres de "Oye Dinex" se pausa sola tras [HANDS_FREE_IDLE_MS] sin uso,
 *   para no mantener el micrófono abierto sin necesidad. La burbuja sigue disponible:
 *   tocarla siempre permite hablar.
 * - Cada comando tiene un tiempo máximo: el asistente nunca se queda escuchando.
 */
class DinexHotwordService : Service(), DinexVoiceOverlayActions {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var destroyed = false
    private var generation = 0
    private var foregroundStarted = false
    private var commandActive = false
    private var handsFree = true
    private var lastInteraction = SystemClock.elapsedRealtime()
    private var cooldownUntil = 0L
    private var lastSavedId: Int? = null
    private var lastAiQuestion: String? = null
    private val engine = DinexVoiceEngine()
    private lateinit var overlay: DinexVoiceOverlay

    private val restartWake = Runnable { if (!destroyed && !commandActive && handsFree) beginWakeListening() }
    private val commandWatchdog = Runnable {
        // Nunca se queda escuchando: si no llegó un resultado a tiempo, se corta.
        if (commandActive) {
            stopRecognizer()
            showError("No escuché nada. Toca la burbuja o “Volver a escuchar”.")
        }
    }
    private val idleCheck = object : Runnable {
        override fun run() {
            if (destroyed) return
            if (handsFree && !commandActive && SystemClock.elapsedRealtime() - lastInteraction > HANDS_FREE_IDLE_MS) pauseHandsFree()
            handler.postDelayed(this, 60_000)
        }
    }
    private val autoClose = Runnable { finishConversation() }

    override fun onCreate() {
        super.onCreate()
        configureDinexStorage(this)
        overlay = DinexVoiceOverlay(this, runCatching { isDinexReducedMotionEnabled() }.getOrDefault(false), this)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_APP_VISIBLE -> {
                // Si el sistema (por ejemplo, el ahorro de batería de Xiaomi) cerró el servicio,
                // al volver a Dinex se vuelve a encender en vez de apagar Oye Dinex.
                if (!foregroundStarted) {
                    if (!prefs().getBoolean(KEY_ENABLED, false)) { stopSelf(); return START_NOT_STICKY }
                } else {
                    // Llamado con startForegroundService: se confirma el primer plano (no cambia nada visible).
                    runCatching { startForeground(NOTIFICATION_ID, listeningNotification()) }
                    handler.removeCallbacks(restartWake)
                    if (!commandActive) stopRecognizer()
                    overlay.hide(); return keepAlive()
                }
            }
            ACTION_APP_HIDDEN -> {
                // Sin servicio activo no se puede abrir el micrófono desde segundo plano: se espera a que vuelva la app.
                if (!foregroundStarted) { stopSelf(); return START_NOT_STICKY }
                if (prefs().getBoolean(KEY_ENABLED, false)) overlay.render(idleUi(), openPanel = false)
                if (!commandActive) scheduleWake(600)
                return keepAlive()
            }
            ACTION_STOP -> { disable(); return START_NOT_STICKY }
            ACTION_RESUME -> {
                if (!foregroundStarted) { stopSelf(); return START_NOT_STICKY }
                commandActive = false
                touch()
                scheduleWake(400)
                return keepAlive()
            }
            ACTION_RESUME_HANDS_FREE -> {
                if (!foregroundStarted) { stopSelf(); return START_NOT_STICKY }
                handsFree = true
                touch()
                updateNotification()
                scheduleWake(300)
                return keepAlive()
            }
            ACTION_LISTEN -> if (foregroundStarted) { onListen(); return keepAlive() }
            // Solo en la versión de desarrollo: simula una frase dictada para probar sin micrófono.
            ACTION_DEBUG_PHRASE -> if (foregroundStarted && isDebuggable()) {
                val phrase = intent.getStringExtra(EXTRA_PHRASE).orEmpty()
                handler.postDelayed({ if (!destroyed) process(phrase) }, 300)
                return keepAlive()
            }
        }
        // Sin intent = el sistema reinició el servicio después de cerrarlo (ahorro de batería).
        val systemRestart = intent == null
        if (systemRestart) {
            if (!prefs().getBoolean(KEY_ENABLED, false)) { stopSelf(); return START_NOT_STICKY }
            // El proceso murió, así que Dinex ya no está en pantalla: la burbuja debe volver a verse.
            prefs().edit().putBoolean(KEY_APP_VISIBLE, false).apply()
        }
        if (!hasMicPermission()) { disable(); return START_NOT_STICKY }
        try {
            startForeground(NOTIFICATION_ID, listeningNotification())
            foregroundStarted = true
            prefs().edit().putBoolean(KEY_ENABLED, true).apply()
        } catch (_: SecurityException) {
            // Android 14+ no permite abrir el micrófono si la app no está a la vista.
            // Si fue un reinicio del sistema, Oye Dinex sigue activado y vuelve al abrir Dinex.
            if (!systemRestart) prefs().edit().putBoolean(KEY_ENABLED, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        handsFree = true
        touch()
        handler.removeCallbacks(idleCheck)
        handler.post(idleCheck)
        if (!prefs().getBoolean(KEY_APP_VISIBLE, false)) overlay.render(idleUi(), openPanel = false)
        if (!commandActive) scheduleWake(200)
        // Si el sistema cierra Dinex para ahorrar batería, Android vuelve a encender la burbuja.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Mientras Oye Dinex está encendido, Android debe volver a iniciarlo si lo cierra. */
    private fun keepAlive() = if (foregroundStarted) START_STICKY else START_NOT_STICKY

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        stopRecognizer()
        overlay.hide()
        // Solo el usuario apaga Oye Dinex (disable). Si lo cerró el sistema, queda activado para volver.
        super.onDestroy()
    }

    // ── Acciones de la burbuja y el panel ─────────────────────────────────────
    override fun onListen() {
        if (destroyed) return
        touch()
        handler.removeCallbacks(autoClose)
        startCommand(prefix = null)
    }

    override fun onCancel() {
        touch()
        stopRecognizer()
        engine.reset()
        finishConversation()
    }

    override fun onDoneSpeaking() {
        // "Listo": se deja de grabar y se procesa lo dicho hasta ahora.
        runCatching { recognizer?.stopListening() }
    }

    override fun onConfirm(save: Boolean) {
        touch()
        present(engine.confirm(save))
    }

    override fun onUndo() {
        touch()
        val id = lastSavedId ?: return
        lastSavedId = null
        val undone = engine.undo(id)
        if (undone) DinexWidgets.updateAll(this)
        render(VoicePanelUi(VoicePanelMode.ANSWER, if (undone) "Listo, lo deshice." else "Ese movimiento ya no está."))
        scheduleAutoClose(5_000)
    }

    override fun onAskAi() {
        val question = lastAiQuestion ?: return
        lastAiQuestion = null
        // Dinex IA se abre en la app, donde se aplica el límite diario y la sesión segura.
        prefs().edit().putString(KEY_PENDING_COMMAND, DINEX_VOICE_AI_PREFIX + question).apply()
        overlay.hidePanel()
        overlay.render(idleUi(), openPanel = false)
        onOpenApp()
    }

    override fun onOpenApp() {
        runCatching { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
    }

    override fun onDisable() = disable()

    override fun onPanelClosed() {
        if (commandActive) { stopRecognizer() }
        engine.reset()
        finishConversation()
    }

    // ── Escucha de la frase "Oye Dinex" (manos libres) ────────────────────────
    private fun scheduleWake(delayMs: Long) {
        handler.removeCallbacks(restartWake)
        if (handsFree) handler.postDelayed(restartWake, delayMs)
    }

    private fun beginWakeListening() {
        if (!hasMicPermission()) { disable(); return }
        // Con Dinex abierto no se escucha la frase: la app tiene su propio botón de voz.
        if (prefs().getBoolean(KEY_APP_VISIBLE, false)) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { scheduleWake(8_000); return }
        stopRecognizer()
        val active = generation
        recognizer = createRecognizer(object : SimpleRecognitionListener() {
            override fun onPartialResults(partialResults: Bundle?) {
                if (active != generation || destroyed) return
                // En resultados parciales solo cuenta la frase exacta, para evitar activaciones accidentales.
                if (results(partialResults).any { wakeCommand(it, strict = true) != null }) onWakeWord(null)
            }

            override fun onResults(results: Bundle?) {
                if (active != generation || destroyed) return
                val phrases = results(results)
                val command = phrases.firstNotNullOfOrNull { wakeCommand(it, strict = false) }
                when {
                    command == null -> scheduleWake(700)
                    // "Oye Dinex, ¿cuánto gasté hoy?" en una sola frase: se atiende directo.
                    command.isNotBlank() -> { onWakeWord(command) }
                    else -> onWakeWord(null)
                }
            }

            override fun onError(error: Int) {
                if (active != generation || destroyed) return
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> disable()
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleWake(1_500)
                    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS, SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> scheduleWake(8_000)
                    else -> scheduleWake(800)
                }
            }
        }, maxResults = 4)
        if (recognizer == null) scheduleWake(8_000)
    }

    private fun onWakeWord(command: String?) {
        if (commandActive || SystemClock.elapsedRealtime() < cooldownUntil) return
        touch()
        handler.removeCallbacks(restartWake)
        stopRecognizer()
        if (command != null) { process(command); return }
        startCommand(prefix = null)
    }

    // ── Comando de voz ────────────────────────────────────────────────────────
    private fun startCommand(prefix: String?) {
        handler.removeCallbacks(restartWake)
        stopRecognizer()
        if (!canUseBubble()) { openCommandActivity(); return }
        commandActive = true
        render(VoicePanelUi(VoicePanelMode.LISTENING, "Te escucho…", aiOnline = isOnline(), handsFree = handsFree))
        handler.postDelayed({ if (commandActive && !destroyed) listenForCommand() }, 150)
        handler.removeCallbacks(commandWatchdog)
        handler.postDelayed(commandWatchdog, COMMAND_TIMEOUT_MS)
    }

    private fun listenForCommand() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showError("Tu teléfono no tiene reconocimiento de voz disponible.")
            return
        }
        val active = generation
        recognizer = createRecognizer(object : SimpleRecognitionListener() {
            override fun onRmsChanged(rmsdB: Float) {
                if (active == generation) overlay.setLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (active != generation || destroyed) return
                results(partialResults).firstOrNull()?.takeIf { it.isNotBlank() }?.let { heard ->
                    render(VoicePanelUi(VoicePanelMode.LISTENING, "Te escucho…", transcript = heard, aiOnline = isOnline(), handsFree = handsFree))
                }
            }

            override fun onResults(results: Bundle?) {
                if (active != generation || destroyed) return
                handler.removeCallbacks(commandWatchdog)
                val text = results(results).firstOrNull().orEmpty()
                stopRecognizer()
                if (text.isBlank()) showError("No entendí la frase. Inténtalo otra vez, un poco más cerca.")
                else process(wakeCommand(text, strict = false)?.ifBlank { null } ?: text)
            }

            override fun onError(error: Int) {
                if (active != generation || destroyed) return
                handler.removeCallbacks(commandWatchdog)
                stopRecognizer()
                showError(when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No te entendí. Prueba: “¿cuánto gasté esta semana?”"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No escuché nada. Toca “Volver a escuchar”."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> "Revisa tu conexión a internet y vuelve a intentarlo."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Dinex necesita el permiso de micrófono."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El micrófono está ocupado por otra app."
                    else -> "No pude escucharte. Vuelve a intentarlo."
                })
            }
        }, maxResults = 3)
        if (recognizer == null) showError("El servicio de voz no está disponible. Abre Dinex para revisar los permisos.")
    }

    /** Interpreta la frase con los datos reales de la cuenta y muestra la respuesta. */
    private fun process(text: String) {
        commandActive = false
        if (!canUseBubble()) {
            // Sin burbuja visible (app abierta o pantalla bloqueada) nada se guarda a ciegas:
            // la frase pasa a la app, que muestra la respuesta o la confirmación.
            prefs().edit().putString(KEY_PENDING_COMMAND, text).apply()
            onOpenApp()
            return
        }
        render(VoicePanelUi(VoicePanelMode.PROCESSING, "Un momento…", transcript = text, aiOnline = isOnline(), handsFree = handsFree))
        handler.postDelayed({
            if (destroyed) return@postDelayed
            val result = runCatching { engine.handle(text) }
                .getOrElse { DinexVoiceResult(DinexVoiceResult.Kind.NO_SESSION, "No pude leer tus datos. Abre Dinex y vuelve a intentarlo.") }
            present(result, transcript = text)
        }, 250)
    }

    private fun present(result: DinexVoiceResult, transcript: String = "") {
        val online = isOnline()
        when (result.kind) {
            DinexVoiceResult.Kind.SAVED -> {
                lastSavedId = result.savedMovementId
                DinexWidgets.updateAll(this)
                render(VoicePanelUi(VoicePanelMode.SAVED, result.text, transcript, aiOnline = online, handsFree = handsFree))
                scheduleAutoClose(15_000)
            }
            DinexVoiceResult.Kind.CONFIRM -> {
                render(VoicePanelUi(VoicePanelMode.CONFIRM, result.text, transcript, aiOnline = online, handsFree = handsFree))
                scheduleAutoClose(45_000)
            }
            DinexVoiceResult.Kind.ASK_AI -> {
                lastAiQuestion = result.question
                val message = if (online) result.text + " Usará una de tus consultas diarias." else "Sin internet no puedo consultar a Dinex IA."
                render(VoicePanelUi(VoicePanelMode.ASK_AI, message, transcript, aiOnline = online, handsFree = handsFree))
                scheduleAutoClose(20_000)
            }
            DinexVoiceResult.Kind.NO_SESSION -> showError(result.text)
            DinexVoiceResult.Kind.CANCELLED -> {
                render(VoicePanelUi(VoicePanelMode.ANSWER, result.text, transcript, aiOnline = online, handsFree = handsFree))
                scheduleAutoClose(2_500)
            }
            DinexVoiceResult.Kind.REMINDER -> {
                DinexWidgets.updateAll(this)
                render(VoicePanelUi(VoicePanelMode.ANSWER, result.text, transcript, aiOnline = online, handsFree = handsFree))
                scheduleAutoClose(8_000)
            }
            DinexVoiceResult.Kind.ANSWER, DinexVoiceResult.Kind.HELP -> {
                render(VoicePanelUi(VoicePanelMode.ANSWER, result.text, transcript, result.items, online, handsFree))
                scheduleAutoClose(if (result.items.isEmpty()) 12_000 else 20_000)
            }
        }
    }

    private fun showError(message: String) {
        commandActive = false
        handler.removeCallbacks(commandWatchdog)
        render(VoicePanelUi(VoicePanelMode.ERROR, message, aiOnline = isOnline(), handsFree = handsFree))
        scheduleAutoClose(8_000)
    }

    private fun render(ui: VoicePanelUi) {
        if (canUseBubble()) overlay.render(ui)
    }

    private fun scheduleAutoClose(delayMs: Long) {
        handler.removeCallbacks(autoClose)
        handler.postDelayed(autoClose, delayMs)
    }

    /** Cierra la conversación: burbuja en reposo y vuelve a esperar "Oye Dinex". */
    private fun finishConversation() {
        commandActive = false
        handler.removeCallbacks(commandWatchdog)
        handler.removeCallbacks(autoClose)
        overlay.hidePanel()
        if (!prefs().getBoolean(KEY_APP_VISIBLE, false)) overlay.render(idleUi(), openPanel = false)
        cooldownUntil = SystemClock.elapsedRealtime() + 1_500
        scheduleWake(1_600)
    }

    private fun idleUi() = VoicePanelUi(
        VoicePanelMode.HINT,
        if (handsFree) "Di “Oye Dinex” o toca “Hablar”. Ejemplo: “¿cuánto gasté esta semana?”"
        else "La escucha manos libres está en pausa. Toca “Hablar” cuando me necesites.",
        aiOnline = isOnline(),
        handsFree = handsFree,
    )

    /** Sin la burbuja (permiso no concedido o pantalla bloqueada) se usa la pantalla de voz. */
    private fun canUseBubble(): Boolean {
        val locked = (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
        return Settings.canDrawOverlays(this) && !locked && !prefs().getBoolean(KEY_APP_VISIBLE, false)
    }

    private fun openCommandActivity() {
        commandActive = false
        val commandIntent = Intent(this, DinexVoiceCommandActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // Si el fabricante bloquea abrir pantallas desde segundo plano, la notificación permite continuar.
        runCatching { startActivity(commandIntent) }
        val pending = PendingIntent.getActivity(this, 4102, commandIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, COMMAND_CHANNEL)
            .setSmallIcon(R.drawable.ic_dinex_notification)
            .setContentTitle("¡Te escucho!")
            .setContentText("Toca para decirle a Dinex qué necesitas.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setTimeoutAfter(30_000)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(COMMAND_NOTIFICATION_ID, notification)
    }

    /** Tras un rato sin uso se apaga la escucha continua; la burbuja sigue para hablar con un toque. */
    private fun pauseHandsFree() {
        handsFree = false
        handler.removeCallbacks(restartWake)
        if (!commandActive) stopRecognizer()
        updateNotification()
        if (!prefs().getBoolean(KEY_APP_VISIBLE, false) && !overlay.panelOpen) overlay.render(idleUi(), openPanel = false)
    }

    private fun disable() {
        prefs().edit().putBoolean(KEY_ENABLED, false).apply()
        stopSelf()
    }

    private fun touch() { lastInteraction = SystemClock.elapsedRealtime() }

    private fun isDebuggable() = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    // ── Utilidades ────────────────────────────────────────────────────────────
    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    private fun hasMicPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun isOnline(): Boolean = runCatching {
        val manager = getSystemService(ConnectivityManager::class.java)
        manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }.getOrDefault(false)

    private fun createRecognizer(listener: RecognitionListener, maxResults: Int): SpeechRecognizer? = runCatching {
        SpeechRecognizer.createSpeechRecognizer(this).also { speech ->
            speech.setRecognitionListener(listener)
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, maxResults)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                // No se fuerza el modo sin conexión: varios Redmi/HyperOS no traen el paquete español local.
            })
        }
    }.getOrElse { stopRecognizer(); null }

    private fun stopRecognizer() {
        generation++
        overlay.setLevel(0f)
        recognizer?.runCatching { cancel() }
        recognizer?.runCatching { destroy() }
        recognizer = null
    }

    private fun results(bundle: Bundle?) = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()

    /**
     * Si la frase empieza con "Oye Dinex", devuelve lo que viene después (vacío si no dijo
     * nada más). null si no es la frase de activación. [strict] exige la frase exacta.
     */
    private fun wakeCommand(value: String, strict: Boolean): String? {
        val normalized = Normalizer.normalize(value.lowercase(Locale.forLanguageTag("es-PE")), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9ñ ]"), " ").replace(Regex("\\s+"), " ").trim()
        val phrases = if (strict) listOf("oye dinex", "hey dinex") else listOf("oye dinex", "hey dinex", "oye dines", "oye inex", "hola dinex", "oye dinez", "ey dinex")
        val match = phrases.firstOrNull { normalized.startsWith(it) || normalized.contains(" $it") } ?: return null
        val index = normalized.indexOf(match)
        return normalized.substring(index + match.length).trim()
    }

    private fun createChannels() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(SERVICE_CHANNEL, "Oye Dinex activo", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Indica que Oye Dinex está activo y permite desactivarlo." },
        )
        manager.createNotificationChannel(
            NotificationChannel(COMMAND_CHANNEL, "Comandos de voz Dinex", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Permite abrir el registro de voz después de decir Oye Dinex." },
        )
    }

    private fun updateNotification() {
        if (!foregroundStarted) return
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, listeningNotification())
    }

    private fun listeningNotification() = NotificationCompat.Builder(this, SERVICE_CHANNEL)
        .setSmallIcon(R.drawable.ic_dinex_notification)
        .setContentTitle(if (handsFree) "Oye Dinex está activo" else "Oye Dinex en pausa")
        .setContentText(
            if (handsFree) "Escuchando la frase “Oye Dinex”. Se pausa sola si no la usas."
            else "Toca la burbuja para hablar. La escucha manos libres está en pausa.",
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(PendingIntent.getActivity(this, 4100, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .apply {
            if (!handsFree) addAction(0, "Reanudar", PendingIntent.getService(this@DinexHotwordService, 4103,
                Intent(this@DinexHotwordService, DinexHotwordService::class.java).setAction(ACTION_RESUME_HANDS_FREE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        .addAction(0, "Desactivar", PendingIntent.getService(this, 4101,
            Intent(this, DinexHotwordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .build()

    companion object {
        const val ACTION_START = "com.example.dinex.action.START_HOTWORD"
        const val ACTION_STOP = "com.example.dinex.action.STOP_HOTWORD"
        const val ACTION_RESUME = "com.example.dinex.action.RESUME_HOTWORD"
        const val ACTION_RESUME_HANDS_FREE = "com.example.dinex.action.RESUME_HANDS_FREE"
        const val ACTION_LISTEN = "com.example.dinex.action.LISTEN"
        const val ACTION_DEBUG_PHRASE = "com.example.dinex.action.DEBUG_PHRASE"
        const val EXTRA_PHRASE = "phrase"
        const val ACTION_APP_VISIBLE = "com.example.dinex.action.APP_VISIBLE"
        const val ACTION_APP_HIDDEN = "com.example.dinex.action.APP_HIDDEN"
        const val PREFS = "dinex_hotword"
        const val KEY_ENABLED = "enabled"
        const val KEY_PENDING_COMMAND = "pending_command"
        const val KEY_APP_VISIBLE = "app_visible"
        private const val SERVICE_CHANNEL = "dinex_hotword_service"
        private const val COMMAND_CHANNEL = "dinex_voice_commands"
        private const val NOTIFICATION_ID = 4100
        private const val COMMAND_NOTIFICATION_ID = 4102
        /** Tiempo máximo escuchando un comando. */
        private const val COMMAND_TIMEOUT_MS = 12_000L
        /** Sin uso durante este tiempo, se pausa la escucha manos libres. */
        private const val HANDS_FREE_IDLE_MS = 15 * 60_000L
    }
}

/** Escucha con métodos vacíos por defecto, para sobrescribir solo lo necesario. */
private open class SimpleRecognitionListener : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onError(error: Int) = Unit
    override fun onResults(results: Bundle?) = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
