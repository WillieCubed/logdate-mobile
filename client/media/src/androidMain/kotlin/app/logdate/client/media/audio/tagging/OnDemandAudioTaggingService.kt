package app.logdate.client.media.audio.tagging

import android.content.Context
import app.logdate.client.media.audio.download.ModelDownloadStatus
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * [AudioTaggingService] proxy that loads the Sherpa-ONNX implementation from
 * the `speech_recognition` dynamic feature module when it's installed.
 *
 * Until the module is on device — and even after install, until the CED
 * model has been downloaded — [isAvailable] is false and [tagAudio] returns
 * a single [AudioTaggingResult.Unavailable] emission so callers can degrade
 * gracefully without special-casing the missing-module path.
 */
class OnDemandAudioTaggingService(
    private val context: Context,
) : AudioTaggingService {
    companion object {
        private const val MODULE_NAME = "speech_recognition"
        private const val PROVIDER_CLASS = "app.logdate.feature.speech.recognition.SpeechRecognitionProvider"
    }

    private val splitInstallManager = SplitInstallManagerFactory.create(context)

    @Volatile
    private var delegate: AudioTaggingService? = null

    init {
        if (isModuleInstalled()) {
            loadDelegate()
        }
    }

    override val isAvailable: Boolean
        get() = delegate?.isAvailable == true

    override suspend fun warmUp(): Boolean {
        if (delegate == null && isModuleInstalled()) {
            loadDelegate()
        }
        return delegate?.warmUp() ?: false
    }

    override fun tagAudio(audioUri: String): Flow<AudioTaggingResult> {
        if (delegate == null && isModuleInstalled()) {
            loadDelegate()
        }
        val current = delegate ?: return flowOf(AudioTaggingResult.Unavailable)
        return current.tagAudio(audioUri)
    }

    override fun downloadModel(): Flow<ModelDownloadStatus> {
        if (delegate == null && isModuleInstalled()) {
            loadDelegate()
        }
        return delegate?.downloadModel() ?: flowOf(ModelDownloadStatus.NotSupported)
    }

    override fun release() {
        delegate?.release()
    }

    private fun isModuleInstalled(): Boolean = splitInstallManager.installedModules.contains(MODULE_NAME)

    private fun loadDelegate() {
        try {
            val providerClass = Class.forName(PROVIDER_CLASS)
            val instance = providerClass.getDeclaredField("INSTANCE").get(null)
            val createMethod =
                providerClass.getMethod(
                    "createAudioTagging",
                    Context::class.java,
                )
            delegate = createMethod.invoke(instance, context) as AudioTaggingService
            Napier.i("Sherpa-ONNX audio tagging module loaded successfully")
        } catch (e: Exception) {
            Napier.e("Failed to load Sherpa-ONNX audio tagging module", e)
        }
    }
}
