package app.logdate.client.media.audio.transcription

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Fallback transcription service backed by Android's built-in [SpeechRecognizer].
 *
 * Used by [OnDemandTranscriptionService] before the on-device Sherpa-ONNX model
 * has been downloaded. Once the model is present this service is no longer called.
 *
 * Note: Android SpeechRecognizer streams audio to remote servers and is not
 * designed for continuous recognition, so it is intentionally limited to the
 * fallback role.
 */
class AndroidTranscriptionService(
    private val context: Context,
) : TranscriptionService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _transcriptionFlow = MutableSharedFlow<TranscriptionResult>(replay = 1)

    override fun getTranscriptionFlow(): SharedFlow<TranscriptionResult> = _transcriptionFlow.asSharedFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var currentLanguage = "en-US"
    private var isListening = false

    // Accumulated transcription: finalized segments joined together
    private val accumulatedSegments = mutableListOf<String>()
    private var currentPartial: String = ""

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            // SpeechRecognizer requires the main thread; dispatch via the scope
            // so construction on a background thread (e.g. lazy init from a
            // coroutine) doesn't crash.
            scope.launch { createSpeechRecognizer() }
        } else {
            scope.launch {
                _transcriptionFlow.emit(TranscriptionResult.Error("Speech recognition not available on this device"))
            }
            Napier.e("Speech recognition not available on this device")
        }
    }

    private fun createSpeechRecognizer() {
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
        } catch (e: Exception) {
            Napier.e("Error creating speech recognizer", e)
            scope.launch {
                _transcriptionFlow.emit(TranscriptionResult.Error("Failed to create speech recognizer", e))
            }
        }
    }

    private fun createRecognitionListener(): RecognitionListener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                scope.launch {
                    _transcriptionFlow.emit(TranscriptionResult.InProgress)
                }
            }

            override fun onBeginningOfSpeech() {
                // Speech input has started
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Audio level changed - could be used for visualization
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // More sound
            }

            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                val errorMessage =
                    when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech input"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Unknown error: $error"
                    }

                scope.launch {
                    _transcriptionFlow.emit(TranscriptionResult.Error(errorMessage))
                }
                Napier.e("Speech recognition error: $errorMessage")

                // Restart listening if this was a temporary error
                if (isListening && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    startListening()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val finalizedText = matches[0]
                    if (finalizedText.isNotBlank()) {
                        accumulatedSegments.add(finalizedText)
                    }
                    currentPartial = ""
                    scope.launch {
                        _transcriptionFlow.emit(TranscriptionResult.Success(buildAccumulatedText()))
                    }

                    // Auto-restart listening if we're in continuous mode
                    if (isListening) {
                        startListening()
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    currentPartial = matches[0] ?: ""
                    scope.launch {
                        _transcriptionFlow.emit(TranscriptionResult.Success(buildAccumulatedText()))
                    }
                }
            }

            override fun onEvent(
                eventType: Int,
                params: Bundle?,
            ) {
                // Reserved for future events
            }
        }

    private fun startListening() {
        try {
            val intent =
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    // For continuous recognition, adjust the timeout
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
                }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Napier.e("Error starting speech recognition", e)
            scope.launch {
                _transcriptionFlow.emit(TranscriptionResult.Error("Failed to start speech recognition", e))
            }
        }
    }

    override suspend fun startLiveTranscription(): Boolean =
        withContext(Dispatchers.Main) {
            if (speechRecognizer == null) createSpeechRecognizer()
            if (speechRecognizer == null) {
                _transcriptionFlow.emit(TranscriptionResult.Error("Speech recognizer not available"))
                return@withContext false
            }
            isListening = true
            startListening()
            true
        }

    override suspend fun stopLiveTranscription() =
        withContext(Dispatchers.Main) {
            isListening = false
            speechRecognizer?.stopListening()
        }

    override suspend fun transcribeAudioFile(audioUri: String): TranscriptionResult {
        // Android's SpeechRecognizer doesn't directly support transcribing files
        // We'd need to use a more sophisticated API like Google Cloud Speech-to-Text
        // For now, we'll return an error
        return TranscriptionResult.Error("File transcription not supported on Android")
    }

    override fun cancelTranscription() {
        isListening = false
        // Post to main thread — SpeechRecognizer requires it.
        scope.launch { speechRecognizer?.cancel() }
    }

    override fun getSupportedLanguages(): List<String> =
        Locale
            .getAvailableLocales()
            .filter { SpeechRecognizer.isRecognitionAvailable(context) }
            .map { it.toLanguageTag() }
            .distinct()

    override fun setLanguage(languageCode: String) {
        currentLanguage = languageCode
    }

    override val supportsLiveTranscription: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    override val supportsFileTranscription: Boolean
        get() = false

    override suspend fun resetTranscription() {
        accumulatedSegments.clear()
        currentPartial = ""
        _transcriptionFlow.emit(TranscriptionResult.InProgress)
        // If actively listening, restart recognition for a clean slate.
        // Both cancel() and startListening() require the main thread.
        if (isListening) {
            withContext(Dispatchers.Main) {
                speechRecognizer?.cancel()
                startListening()
            }
        }
    }

    override fun release() {
        isListening = false
        accumulatedSegments.clear()
        currentPartial = ""
        // SpeechRecognizer.destroy() also requires the main thread.
        scope.launch {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    private fun buildAccumulatedText(): String {
        val base = accumulatedSegments.joinToString(" ")
        return if (currentPartial.isNotBlank()) {
            if (base.isNotBlank()) "$base $currentPartial" else currentPartial
        } else {
            base
        }
    }
}
