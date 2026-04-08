package app.logdate.client.media.audio.transcription

import android.content.Context
import app.logdate.client.media.audio.download.ModelDownloadStatus
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [TranscriptionService] proxy that loads the Sherpa-ONNX implementation from the
 * `speech_recognition` dynamic feature module when it is installed.
 *
 * When the module is not yet installed, delegates to [AndroidTranscriptionService]
 * (Android's built-in SpeechRecognizer) as a fallback.
 *
 * Owns a stable [_transcriptionFlow] that observers subscribe to once. When
 * [startLiveTranscription] runs, a forwarding coroutine is started that pipes
 * the active delegate's emissions into this stable flow. Without this, a
 * delegate switch between [getTranscriptionFlow] and [startLiveTranscription]
 * leaves the observer watching the wrong flow and receiving nothing.
 */
class OnDemandTranscriptionService(
    private val context: Context,
    private val scope: CoroutineScope,
) : TranscriptionService {
    companion object {
        private const val MODULE_NAME = "speech_recognition"
        private const val PROVIDER_CLASS = "app.logdate.feature.speech.recognition.SpeechRecognitionProvider"

        private val NotSupportedDownloadStatus: StateFlow<ModelDownloadStatus> =
            MutableStateFlow(ModelDownloadStatus.NotSupported).asStateFlow()
    }

    private val splitInstallManager = SplitInstallManagerFactory.create(context)
    private val fallback: TranscriptionService by lazy { AndroidTranscriptionService(context) }

    @Volatile
    private var delegate: TranscriptionService? = null

    // Stable flow that external observers always collect from. Forwarded-to
    // from whichever delegate is actually running live transcription.
    private val _transcriptionFlow = MutableSharedFlow<TranscriptionResult>(replay = 1)
    private var forwardingJob: Job? = null

    init {
        if (isModuleInstalled()) {
            loadDelegate()
        }
    }

    private fun resolvedDelegate(): TranscriptionService = delegate ?: fallback

    override fun getTranscriptionFlow(): SharedFlow<TranscriptionResult> = _transcriptionFlow.asSharedFlow()

    override suspend fun startLiveTranscription(): Boolean {
        if (delegate == null && isModuleInstalled()) {
            loadDelegate()
        }
        val active = resolvedDelegate()

        // Replace the forwarding subscription so results from this specific
        // delegate instance reach the stable _transcriptionFlow. The previous
        // job is canceled — its delegate is no longer driving transcription.
        // We intentionally start forwarding BEFORE calling startLiveTranscription
        // so no early emissions are missed.
        forwardingJob?.cancel()
        forwardingJob =
            scope.launch {
                active.getTranscriptionFlow().collect { result ->
                    _transcriptionFlow.emit(result)
                }
            }

        return active.startLiveTranscription()
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

    override val offlineModelDownloadStatus: StateFlow<ModelDownloadStatus>
        get() {
            if (delegate == null && isModuleInstalled()) {
                loadDelegate()
            }
            return delegate?.offlineModelDownloadStatus ?: NotSupportedDownloadStatus
        }

    override fun startOfflineModelDownload() {
        // The download lives in the dynamic feature module — if the user
        // hasn't installed it yet, we have nowhere to put the model. The
        // download UX should kick the split install first; until then this
        // is a no-op and the StateFlow stays at NotSupported.
        if (delegate == null && isModuleInstalled()) {
            loadDelegate()
        }
        delegate?.startOfflineModelDownload()
    }

    override fun release() {
        forwardingJob?.cancel()
        forwardingJob = null
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
