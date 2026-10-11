package com.example.dinex

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.app.KeyguardManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.Locale

/**
 * Escucha opcional y visible para la frase "Oye Dinex".
 *
 * Android no permite registrar un hotword privado en el DSP del sistema. Por
 * eso esta función usa un servicio de micrófono visible con una notificación
 * permanente y solo se inicia después de una acción expresa del usuario.
 */
class DinexHotwordService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var pausedForCommand = false
    private var destroyed = false
    private var generation = 0
    private var foregroundStarted = false
    private lateinit var overlay: DinexVoiceOverlay
    private val restartListening = Runnable {
        if (!destroyed && !pausedForCommand) beginListening()
    }
    private val resumeAfterCommandTimeout = Runnable {
        pausedForCommand = false
        scheduleListening(200)
    }

    override fun onCreate() {
        super.onCreate()
        configureDinexStorage(this)
        overlay = DinexVoiceOverlay(this,
            onListen = { beginFloatingCommand() },
            onOpenApp = { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            onStop = { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply(); stopSelf() },
        )
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_APP_VISIBLE -> {
                if (!foregroundStarted) { clearStaleActivation(); return START_NOT_STICKY }
                overlay.hide(); return START_NOT_STICKY
            }
            ACTION_APP_HIDDEN -> {
                if (!foregroundStarted) { clearStaleActivation(); return START_NOT_STICKY }
                if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ENABLED, false)) overlay.show()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                if (!foregroundStarted) { clearStaleActivation(); return START_NOT_STICKY }
                handler.removeCallbacks(resumeAfterCommandTimeout)
                pausedForCommand = false
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForeground(NOTIFICATION_ID, listeningNotification())
            foregroundStarted = true
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, true).apply()
        } catch (_: SecurityException) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_APP_VISIBLE, false)) overlay.show()
        if (!pausedForCommand) scheduleListening(180)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun clearStaleActivation() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
        stopSelf()
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        stopRecognizer()
        overlay.hide()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
        super.onDestroy()
    }

    private fun scheduleListening(delayMs: Long) {
        handler.removeCallbacks(restartListening)
        handler.postDelayed(restartListening, delayMs)
    }

    private fun beginListening(commandMode: Boolean = false) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
            stopSelf()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            scheduleListening(8_000)
            return
        }
        stopRecognizer()
        val activeGeneration = generation
        try {
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { speech ->
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    if (activeGeneration != generation || destroyed) return
                    val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    if (commandMode) {
                        val command = phrases.firstOrNull().orEmpty()
                        val message = if (command.isBlank()) "No entendí la frase. Pulsa Hablar para repetir." else runCatching {
                            recordDinexVoiceCommand(command)
                        }.getOrDefault("No pude guardar. Abre Dinex y vuelve a intentarlo.")
                        overlay.showResult(message)
                        stopRecognizer()
                        handler.removeCallbacks(resumeAfterCommandTimeout)
                        handler.postDelayed(resumeAfterCommandTimeout, 1800)
                    } else if (phrases.any(::isWakePhrase)) onWakeWord() else scheduleListening(900)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (activeGeneration != generation || destroyed) return
                    val phrases = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    if (commandMode) phrases.firstOrNull()?.let { overlay.showResult("Escuché: " + it) }
                    else if (phrases.any(::isWakePhrase)) onWakeWord()
                }

                override fun onError(error: Int) {
                    if (activeGeneration != generation || destroyed) return
                    if (commandMode) {
                        overlay.showResult(if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT)
                            "Revisa tu conexión y pulsa Hablar." else "No entendí la frase. Pulsa Hablar para repetir.")
                        handler.removeCallbacks(resumeAfterCommandTimeout)
                        handler.postDelayed(resumeAfterCommandTimeout, 1800)
                        return
                    }
                    val delay = when (error) {
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1_500L
                        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> 8_000L
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 8_000L
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
                            stopSelf()
                            return
                        }
                        else -> 850L
                    }
                    scheduleListening(delay)
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4)
                // No se fuerza el modo offline: varios Redmi/HyperOS no incluyen
                // el paquete español local y devolvían error inmediatamente.
            })
        }
        } catch (_: Exception) {
            stopRecognizer()
            if (commandMode) {
                overlay.showResult("El servicio de voz no está disponible. Abre Dinex para revisar los permisos.")
                handler.postDelayed(resumeAfterCommandTimeout, 1800)
            } else scheduleListening(8_000)
        }
    }

    private fun onWakeWord() {
        if (pausedForCommand) return
        pausedForCommand = true
        handler.removeCallbacks(restartListening)
        handler.removeCallbacks(resumeAfterCommandTimeout)
        stopRecognizer()
        val locked = (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
        if (Settings.canDrawOverlays(this) && !locked &&
            !getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_APP_VISIBLE, false)) {
            beginFloatingCommand()
            return
        }
        val commandIntent = Intent(this, DinexVoiceCommandActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // Se intenta abrir directamente. Si el fabricante bloquea inicios desde
        // segundo plano, la notificación de alta prioridad permite continuar con
        // un toque sin perder la activación.
        runCatching { startActivity(commandIntent) }
        val pending = PendingIntent.getActivity(
            this,
            4102,
            commandIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, COMMAND_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("¡Te escucho!")
            .setContentText("Toca para decirle a Dinex qué deseas registrar.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(COMMAND_NOTIFICATION_ID, notification)
        // Protección por si la actividad fue cerrada por el sistema.
        handler.postDelayed(resumeAfterCommandTimeout, 35_000)
    }

    private fun stopRecognizer() {
        generation++
        recognizer?.runCatching { cancel() }
        recognizer?.runCatching { destroy() }
        recognizer = null
    }

    private fun beginFloatingCommand() {
        if (destroyed) return
        pausedForCommand = true
        handler.removeCallbacks(restartListening)
        handler.removeCallbacks(resumeAfterCommandTimeout)
        stopRecognizer()
        overlay.showPanel("Te escucho. Por ejemplo: registra 20 soles en comida.")
        handler.postDelayed({ if (!destroyed) beginListening(commandMode = true) }, 250)
        handler.postDelayed(resumeAfterCommandTimeout, 25_000)
    }

    private fun isWakePhrase(value: String): Boolean {
        val normalized = Normalizer.normalize(value.lowercase(Locale("es", "PE")), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return listOf("oye dinex", "hey dinex", "oye dines", "oye inex", "hola dinex").any(normalized::contains)
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL,
                "Oye Dinex activo",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Indica cuándo Dinex está escuchando la frase de activación." }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                COMMAND_CHANNEL,
                "Comandos de voz Dinex",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Permite abrir el registro de voz después de decir Oye Dinex." }
        )
    }

    private fun listeningNotification() = NotificationCompat.Builder(this, SERVICE_CHANNEL)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Oye Dinex está activo")
        .setContentText("Di “Oye Dinex” para registrar un gasto, ingreso o recordatorio.")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                4100,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .addAction(
            0,
            "Desactivar",
            PendingIntent.getService(
                this,
                4101,
                Intent(this, DinexHotwordService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    companion object {
        const val ACTION_START = "com.example.dinex.action.START_HOTWORD"
        const val ACTION_STOP = "com.example.dinex.action.STOP_HOTWORD"
        const val ACTION_RESUME = "com.example.dinex.action.RESUME_HOTWORD"
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
    }
}
