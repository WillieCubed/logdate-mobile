package app.logdate.client.media.audio

import android.content.Context
import app.logdate.client.media.audio.tagging.AudioTaggingService
import app.logdate.client.media.audio.transcription.TranscriptionService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope

/**
 * Base-module contract implemented by the install-time speech feature.
 *
 * Keeping the implementation behind this interface lets the base app compile
 * without linking Sherpa-ONNX while still treating transcription as an
 * installed product capability.
 */
interface SpeechFeatureProvider {
    fun createTranscription(
        context: Context,
        scope: CoroutineScope,
    ): TranscriptionService

    fun createAudioTagging(context: Context): AudioTaggingService
}

/** Loads the install-time speech feature without a direct base-to-feature dependency. */
internal class SpeechFeatureProviderLoader {
    companion object {
        private const val PROVIDER_CLASS =
            "app.logdate.feature.speech.recognition.SpeechRecognitionProvider"
    }

    fun load(): SpeechFeatureProvider? =
        try {
            val providerClass = Class.forName(PROVIDER_CLASS)
            providerClass.getDeclaredField("INSTANCE").get(null) as SpeechFeatureProvider
        } catch (e: ClassNotFoundException) {
            Napier.e("Install-time speech feature is missing from this app installation", e)
            null
        } catch (e: Exception) {
            Napier.e("Install-time speech feature could not be loaded", e)
            null
        }
}
