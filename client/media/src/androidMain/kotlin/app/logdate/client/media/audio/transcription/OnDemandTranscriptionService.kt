package app.logdate.client.media.audio.transcription

import android.content.Context
import app.logdate.client.media.audio.download.ModelDownloadStatus
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOf

/**
 * [TranscriptionService] proxy that loads the Sherpa-ONNX implementation from the
 * `speech_recognition` dynamic feature module when it is installed.
 *
 * When the module is not yet installed, delegates to [AndroidTranscriptionService]
 * (Android's built-in SpeechRecognizer) as a fallback.
 */
class OnDemandTranscriptionService(
    private val context: Context,
    private val scope: CoroutineScope,
) : TranscriptionService {
    companion object {
        private const val MODULE_NAME = "speech_recognition"
        private const val PROVIDER_CLASS = "app.logdate.feature.speech.recognition.SpeechRecognitionProvider"
    }

    private val splitInstallManager = SplitInstallManagerFactory.create(context)
    private val fallback: TranscriptionService by lazy { AndroidTranscriptionService(context) }

    @Volatile
    private var delegate: TranscriptionService? = null

    init {
        if (isModuleInstalled()) {
            loadDelegate()
        }
    }

    private fun resolvedDelegate(): TranscriptionService = delegate ?: fallback

    override fun getTranscriptionFlow(): SharedFlow<TranscriptionResult> = resolvedDelegate().getTranscriptionFlow()

    override suspend fun startLiveTranscription(): Boolean {
        // Try to load the dynamic module if it became available since init
        if (delegate == null && isModuleInstalled()) {
            loadDelegate()
        }
        return resolvedDelegate().startLiveTranscription()
    }

    override suspend fun stopLiveTranscription() = resolvedDelegate().stopLiveTranscription()

    override suspend fun transcribeAudioFile(audioUri: String): TranscriptionResult = resolvedDelegate().transcribeAudioFile(audioUri)

    override fun cancelTranscription() = resolvedDelegate().cancelTranscription()

    override fun getSupportedLanguages(): List<String> = resolvedDelegate().getSupportedLanguages()

    override fun setLanguage(languageCode: String) = resolvedDelegate().setLanguage(languageCode)

    override val supportsLiveTranscription: Boolean
        get() = resolvedDelegate().supportsLiveTranscription

    override val supportsFileTranscription: Boolean
        get() = resolvedDelegate().supportsFileTranscription

    override suspend fun resetTranscription() = resolvedDelegate().resetTranscription()

    override suspend fun warmUp() = resolvedDelegate().warmUp()

    override val isOfflineModelAvailable: Boolean
        get() = delegate?.isOfflineModelAvailable == true

    override fun downloadOfflineModel(): Flow<ModelDownloadStatus> {
        // The download lives in the dynamic feature module — if the user
        // hasn't installed it yet, we have nowhere to put the model. The
        // model download UX should kick the split install first; until then,
        // surface NotSupported so the UI can prompt for the dynamic feature.
        if (delegate == null && isModuleInstalled()) {
            loadDelegate()
        }
        return delegate?.downloadOfflineModel() ?: flowOf(ModelDownloadStatus.NotSupported)
    }

    override fun release() {
        delegate?.release()
        // Don't release fallback eagerly — it's lazy-initialized
    }

    /**
     * Whether the Sherpa-ONNX dynamic module is available on device.
     */
    fun isSherpaOnnxAvailable(): Boolean = delegate != null || isModuleInstalled()

    private fun isModuleInstalled(): Boolean = splitInstallManager.installedModules.contains(MODULE_NAME)

    private fun loadDelegate() {
        try {
            val providerClass = Class.forName(PROVIDER_CLASS)
            val instance = providerClass.getDeclaredField("INSTANCE").get(null)
            val createMethod =
                providerClass.getMethod(
                    "create",
                    Context::class.java,
                    CoroutineScope::class.java,
                )
            val service = createMethod.invoke(instance, context, scope) as TranscriptionService
            delegate = service
            Napier.i("Sherpa-ONNX transcription module loaded successfully")
        } catch (e: Exception) {
            Napier.e("Failed to load Sherpa-ONNX transcription module; using fallback", e)
        }
    }
}
