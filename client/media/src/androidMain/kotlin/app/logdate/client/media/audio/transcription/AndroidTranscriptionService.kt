package app.logdate.client.media.audio.transcription

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Bundle
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Android implementation of TranscriptionService using Android's SpeechRecognizer
 * 
 * TODO: Replace with a more robust speech transcription solution. 
 * Android SpeechRecognizer is not intended for continuous recognition as noted in docs:
 * "The implementation of this API is likely to stream audio to remote servers to perform speech
 * recognition. As such this API is not intended to be used for continuous recognition, which would
 * consume a significant amount of battery and bandwidth."
 * 
 * Consider alternatives like:
 * - ML Kit's Speech Recognition API (on-device option)
 * - Google Cloud Speech-to-Text API (server-based, more robust)
 * - Whisper API from OpenAI (high accuracy)
 * - Custom implementation with a local ML model
 */
class AndroidTranscriptionService(
    private val context: Context
) : TranscriptionService {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _transcriptionFlow = MutableSharedFlow<TranscriptionResult>(replay = 1)
    override fun getTranscriptionFlow(): SharedFlow<TranscriptionResult> = _transcriptionFlow.asSharedFlow()
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var currentLanguage = "en-US"
    private var isListening = false
    
    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            createSpeechRecognizer()
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
    
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
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
                val errorMessage = when (error) {
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
                    val transcribedText = matches[0] // Get the most likely result
                    scope.launch {
                        _transcriptionFlow.emit(TranscriptionResult.Success(transcribedText))
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
                    val partialText = matches[0] // Get the most likely result
                    scope.launch {
                        _transcriptionFlow.emit(TranscriptionResult.Success(partialText))
                    }
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {
                // Reserved for future events
            }
        }
    }
    
    private fun startListening() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
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
    
    override suspend fun startLiveTranscription(): Boolean {
        if (speechRecognizer == null) {
            createSpeechRecognizer()
        }
        
        if (speechRecognizer == null) {
            _transcriptionFlow.emit(TranscriptionResult.Error("Speech recognizer not available"))
            return false
        }
        
        isListening = true
        startListening()
        return true
    }
    
    override suspend fun stopLiveTranscription() {
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
        speechRecognizer?.cancel()
    }
    
    override fun getSupportedLanguages(): List<String> {
        return Locale.getAvailableLocales()
            .filter { SpeechRecognizer.isRecognitionAvailable(context) }
            .map { it.toLanguageTag() }
            .distinct()
    }
    
    override fun setLanguage(languageCode: String) {
        currentLanguage = languageCode
    }
    
    override val supportsLiveTranscription: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)
    
    override val supportsFileTranscription: Boolean
        get() = false
    
    override fun release() {
        isListening = false
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}