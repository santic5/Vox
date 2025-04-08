package com.github.santic5.voxtest1

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

class MainActivity : ComponentActivity(), RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var isListening = false

    companion object {
        private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar permisos de grabación de audio
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSIONS_REQUEST_RECORD_AUDIO)
        } else {
            initModel()
        }

        setContent {
            VoiceRecognitionScreen()
        }
    }

    private fun initModel() {
        StorageService.unpack(this, "vosk-model-small-es-0.42", "model",
            { loadedModel ->
                model = loadedModel
                Log.d("Vosk", "Modelo cargado correctamente")
            },
            { exception ->
                Log.e("Vosk", "Error al cargar el modelo: ${exception.message}")
                setContent {
                    VoiceRecognitionScreen(errorMessage = "Error al cargar el modelo: ${exception.message}")
                }
            })
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initModel()
            } else {
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechService?.stop()
        speechService?.shutdown()
    }

    override fun onResult(hypothesis: String) {
        Log.d("Vosk", "Resultado: $hypothesis")
        setContent {
            VoiceRecognitionScreen(addResult = hypothesis)
        }
    }

    override fun onFinalResult(hypothesis: String) {
        Log.d("Vosk", "Resultado final: $hypothesis")
        setContent {
            VoiceRecognitionScreen(addResult = hypothesis)
        }
        isListening = false
        speechService = null
    }

    override fun onPartialResult(hypothesis: String) {
        Log.d("Vosk", "Resultado parcial: $hypothesis")
        setContent {
            VoiceRecognitionScreen(addResult = hypothesis)
        }
    }

    override fun onError(e: Exception) {
        Log.e("Vosk", "Error: ${e.message}")
        setContent {
            VoiceRecognitionScreen(errorMessage = "Error: ${e.message}")
        }
        isListening = false
        speechService = null
    }

    override fun onTimeout() {
        Log.d("Vosk", "Timeout")
        isListening = false
        speechService = null
        setContent {
            VoiceRecognitionScreen(addResult = "Timeout")
        }
    }

    @Composable
    fun VoiceRecognitionScreen(
        addResult: String? = null,
        errorMessage: String? = null
    ) {
        // Lista para acumular los resultados
        val results = remember { mutableStateListOf<String>() }

        // Estado para el mensaje actual
        var currentMessage by remember { mutableStateOf("Presiona 'Iniciar' para comenzar") }

        // Agregar un nuevo resultado a la lista
        LaunchedEffect(addResult) {
            if (addResult != null) {
                results.add(addResult)
                currentMessage = addResult
            }
        }

        // Mostrar mensaje de error si existe
        LaunchedEffect(errorMessage) {
            if (errorMessage != null) {
                currentMessage = errorMessage
                results.add(errorMessage)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Reconocimiento de voz",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Mostrar la lista de resultados acumulados
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(results) { result ->
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mostrar el mensaje actual (estado o último resultado)
            Text(
                text = currentMessage,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row {
                Button(
                    onClick = {
                        if (!isListening) {
                            try {
                                val recognizer = Recognizer(model, 16000.0f)
                                speechService = SpeechService(recognizer, 16000.0f)
                                speechService?.startListening(this@MainActivity)
                                isListening = true
                                currentMessage = "Escuchando..."
                                results.add("Escuchando...")
                            } catch (e: Exception) {
                                currentMessage = "Error al iniciar: ${e.message}"
                                results.add("Error al iniciar: ${e.message}")
                            }
                        }
                    },
                    enabled = !isListening
                ) {
                    Text("Iniciar")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (isListening) {
                            speechService?.stop()
                            speechService = null
                            isListening = false
                            currentMessage = "Detenido"
                            results.add("Detenido")
                        }
                    },
                    enabled = isListening
                ) {
                    Text("Detener")
                }
            }
        }
    }
}