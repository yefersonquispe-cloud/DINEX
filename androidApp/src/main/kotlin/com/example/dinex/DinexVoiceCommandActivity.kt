package com.example.dinex

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pantalla breve y visible que captura el comando después de "Oye Dinex". */
class DinexVoiceCommandActivity : ComponentActivity() {
    private var recognizer: SpeechRecognizer? = null
    private var status by mutableStateOf("Preparando el micrófono…")
    private var error by mutableStateOf<String?>(null)
    private var level by mutableFloatStateOf(0.15f)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { VoiceCommandScreen() }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            window.decorView.postDelayed(::startListening, 350)
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
        else {
            status = "Dinex necesita acceso al micrófono"
            error = "Activa el permiso de micrófono para utilizar comandos de voz."
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    @androidx.compose.runtime.Composable
    private fun VoiceCommandScreen() {
        val colors = darkColorScheme(
            primary = ComposeColor(0xFF33D6A5),
            background = ComposeColor(0xFF07151F),
            surface = ComposeColor(0xFF102431),
            onSurface = ComposeColor(0xFFF4FBFF),
        )
        MaterialTheme(colorScheme = colors) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(ComposeColor(0xFF07151F), ComposeColor(0xFF0B2936)))
                ).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    color = ComposeColor(0xFF102431),
                    shadowElevation = 20.dp,
                ) {
                    Column(
                        Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            modifier = Modifier.size((84 + level * 24).dp),
                            shape = CircleShape,
                            color = ComposeColor(0xFF33D6A5).copy(alpha = 0.13f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(58.dp),
                                    color = ComposeColor(0xFF33D6A5),
                                    strokeWidth = 5.dp,
                                )
                                Text("D", color = ComposeColor.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("Dinex te escucha", fontWeight = FontWeight.Black, fontSize = 27.sp)
                        Spacer(Modifier.height(7.dp))
                        Text(status, color = ComposeColor(0xFF8CF2D4), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        Surface(shape = RoundedCornerShape(18.dp), color = ComposeColor(0xFF183344)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Puedes decir:", color = ComposeColor(0xFF9CB4C2), fontSize = 11.sp)
                                Text("“Registra 20 soles en comida”", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("“Recibí 150 soles por un trabajo”", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("“Agenda pagar internet mañana por 50 soles”", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        error?.let {
                            Spacer(Modifier.height(14.dp))
                            Text(it, color = ComposeColor(0xFFFF7180), textAlign = TextAlign.Center, fontSize = 12.sp)
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = ::startListening,
                                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF36BFF2)),
                            ) { Text("Intentar otra vez", fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            TextButton(onClick = ::cancelAndFinish) { Text("Cancelar", color = ComposeColor(0xFF9CB4C2)) }
                        }
                    }
                }
            }
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status = "Reconocimiento no disponible"
            error = "Instala o activa el servicio de reconocimiento de voz de Google."
            return
        }
        error = null
        status = "Di qué deseas registrar"
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { speech ->
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val command = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (command.isBlank()) showRecognitionError("No entendí el comando. Inténtalo nuevamente.")
                    else saveAndOpenDinex(command)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                        if (it.isNotBlank()) status = "Escuché: “$it”"
                    }
                }

                override fun onError(code: Int) {
                    val message = when (code) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No pude reconocer la frase. Habla un poco más cerca."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No escuché nada. Pulsa para volver a intentarlo."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "No hay conexión para reconocer la voz."
                        else -> "No pude escuchar el comando. Inténtalo nuevamente."
                    }
                    showRecognitionError(message)
                }

                override fun onReadyForSpeech(params: Bundle?) { status = "Te escucho…" }
                override fun onBeginningOfSpeech() { status = "Procesando tu voz…" }
                override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 14f).coerceIn(0.05f, 1f) }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { status = "Entendiendo el comando…" }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué deseas registrar en Dinex?")
            })
        }
    }

    private fun showRecognitionError(message: String) {
        status = "No pude completar el registro"
        error = message
        level = 0.15f
    }

    private fun saveAndOpenDinex(command: String) {
        getSharedPreferences(DinexHotwordService.PREFS, Context.MODE_PRIVATE)
            .edit().putString(DinexHotwordService.KEY_PENDING_COMMAND, command).apply()
        resumeHotword()
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        finish()
    }

    private fun cancelAndFinish() {
        resumeHotword()
        finish()
    }

    private fun resumeHotword() {
        val prefs = getSharedPreferences(DinexHotwordService.PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(DinexHotwordService.KEY_ENABLED, false)) {
            startService(Intent(this, DinexHotwordService::class.java).setAction(DinexHotwordService.ACTION_RESUME))
        }
    }

    companion object {
        private const val REQUEST_AUDIO = 7201
    }
}
