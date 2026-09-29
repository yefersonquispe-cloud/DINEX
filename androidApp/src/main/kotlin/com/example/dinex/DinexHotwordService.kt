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

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESUME -> pausedForCommand = false
        }
        startForeground(NOTIFICATION_ID, listeningNotification())
        if (!pausedForCommand) scheduleListening(180)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        stopRecognizer()
        super.onDestroy()
    }

    private fun scheduleListening(delayMs: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (!destroyed && !pausedForCommand) beginListening() }, delayMs)
    }

    private fun beginListening() {
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
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { speech ->
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    if (phrases.any(::isWakePhrase)) onWakeWord() else scheduleListening(350)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val phrases = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    if (phrases.any(::isWakePhrase)) onWakeWord()
                }

                override fun onError(error: Int) {
                    val delay = when (error) {
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1_500L
                        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> 8_000L
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
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            })
        }
    }

    private fun onWakeWord() {
        if (pausedForCommand) return
        pausedForCommand = true
        stopRecognizer()
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
        handler.postDelayed({
            pausedForCommand = false
            scheduleListening(200)
        }, 35_000)
    }

    private fun stopRecognizer() {
        recognizer?.runCatching { cancel() }
        recognizer?.runCatching { destroy() }
        recognizer = null
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
        const val PREFS = "dinex_hotword"
        const val KEY_ENABLED = "enabled"
        const val KEY_PENDING_COMMAND = "pending_command"
        private const val SERVICE_CHANNEL = "dinex_hotword_service"
        private const val COMMAND_CHANNEL = "dinex_voice_commands"
        private const val NOTIFICATION_ID = 4100
        private const val COMMAND_NOTIFICATION_ID = 4102
    }
}
