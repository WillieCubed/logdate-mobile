package app.logdate.feature.speech.recognition

import android.content.Context
import app.logdate.client.media.audio.SpeechFeatureProvider
import app.logdate.client.media.audio.tagging.AudioTaggingService
import app.logdate.client.media.audio.transcription.TranscriptAccumulator
import app.logdate.client.media.audio.transcription.TranscriptionService
import kotlinx.coroutines.CoroutineScope

/**
 * Provider loaded by the base app from the install-time speech feature split.
 */
object SpeechRecognitionProvider : SpeechFeatureProvider {
    override fun createTranscription(
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
    override fun createAudioTagging(context: Context): AudioTaggingService {
        val decoder = AudioDecoder(context)
        return SherpaOnnxAudioTaggingService(context, decoder)
    }
}
