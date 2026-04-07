package app.logdate.feature.speech.recognition

import android.content.Context
import app.logdate.client.media.audio.transcription.TranscriptAccumulator
import app.logdate.client.media.audio.transcription.TranscriptionService
import kotlinx.coroutines.CoroutineScope

/**
 * Provider object loaded via reflection by [OnDemandTranscriptionService][app.logdate.client.media.audio.transcription.OnDemandTranscriptionService]
 * when the speech-recognition dynamic feature module is installed.
 */
object SpeechRecognitionProvider {
    fun create(
        context: Context,
        scope: CoroutineScope,
    ): TranscriptionService {
        val accumulator = TranscriptAccumulator()
        val recognizerProvider = SherpaOnnxRecognizerProvider(context)
        val vadProvider = SherpaOnnxVadProvider(context)
        val offlineRecognizerProvider = SherpaOnnxOfflineRecognizerProvider(context)
        return SherpaOnnxTranscriptionService(
            context,
            recognizerProvider,
            vadProvider,
            offlineRecognizerProvider,
            scope,
            accumulator,
        )
    }
}
