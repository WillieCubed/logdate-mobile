package app.logdate.feature.speech.recognition

import android.content.Context
import app.logdate.client.media.audio.tagging.AudioTaggingService
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

    /**
     * Creates the on-device ambient sound tagger. Returned as the
     * [AudioTaggingService] interface so the rest of the app doesn't depend on
     * the dynamic feature module directly. The underlying CED model is
     * downloaded on demand, and [AudioTaggingService.isAvailable] reports
     * whether tagging can actually run.
     */
    fun createAudioTagging(context: Context): AudioTaggingService {
        val decoder = AudioDecoder(context)
        return SherpaOnnxAudioTaggingService(context, decoder)
    }
}
