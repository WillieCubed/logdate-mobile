package app.logdate.client.media.audio.transcription

import android.content.Context
import app.logdate.client.media.audio.download.ModelDownloadStatus
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
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
 * [TranscriptionService] proxy that loads the Sherpa-ONNX implementation from
 * the `speech_recognition` dynamic feature module.
 *
 * On debug/sideloaded builds the module class is always in the APK, so the
 * delegate loads immediately in [init]. On Play Store builds the module may
 * need to be installed first; when [startLiveTranscription] is called without
 * a loaded delegate, the install is requested automatically and the caller
 * receives [TranscriptionResult.InProgress] while the download runs. Once the
 * module installs, transcription starts without any further user action.
 *
 * Owns a stable [_transcriptionFlow] so observers never need to re-subscribe
 * when the delegate switches. Results from whichever delegate is active are
 * forwarded here via [forwardingJob].
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

    @Volatile
    private var delegate: TranscriptionService? = null

    @Volatile
    private var pendingLiveTranscription = false

    private val _transcriptionFlow = MutableSharedFlow<TranscriptionResult>(replay = 1)
    private var forwardingJob: Job? = null

    private val installListener =
        SplitInstallStateUpdatedListener { state ->
            when (state.status()) {
                SplitInstallSessionStatus.INSTALLED -> {
                    Napier.i("speech_recognition module installed — loading delegate")
                    loadDelegate()
                    if (pendingLiveTranscription) {
                        pendingLiveTranscription = false
                        scope.launch { startAndForward() }
                    }
                }
                SplitInstallSessionStatus.FAILED -> {
                    Napier.e("speech_recognition module install failed (error ${state.errorCode()})")
                    pendingLiveTranscription = false
                    scope.launch {
                        _transcriptionFlow.emit(
                            TranscriptionResult.Error("Transcription engine failed to download"),
                        )
                    }
                }
                SplitInstallSessionStatus.DOWNLOADING -> {
                    val total = state.totalBytesToDownload()
                    val done = state.bytesDownloaded()
                    if (total > 0) Napier.d("Downloading speech_recognition: $done/$total bytes")
                }
                else -> Unit
            }
        }

    init {
        splitInstallManager.registerListener(installListener)
        // Attempt immediate load — succeeds on debug/sideloaded builds where
        // the class is in the APK, and on Play Store builds after the module
        // has been installed previously.
        loadDelegate()
    }

    private fun resolvedDelegate(): TranscriptionService =
        delegate ?: error("speech_recognition module not loaded — use startLiveTranscription() to trigger install")

    override fun getTranscriptionFlow(): SharedFlow<TranscriptionResult> = _transcriptionFlow.asSharedFlow()

    override suspend fun startLiveTranscription(): Boolean {
        if (delegate == null) {
            // Module not in the APK yet — request install and let the
            // installListener handle starting transcription once it arrives.
            Napier.i("speech_recognition module not ready — requesting install")
            requestModuleInstall()
            pendingLiveTranscription = true
            _transcriptionFlow.emit(TranscriptionResult.InProgress)
            return false
        }
        return startAndForward()
    }

    /**
     * Wires the active delegate's flow into [_transcriptionFlow] and starts
     * live transcription on that delegate.
     */
    private suspend fun startAndForward(): Boolean {
        val active = requireNotNull(delegate) { "delegate must be non-null when startAndForward() is called" }
        forwardingJob?.cancel()
        forwardingJob =
            scope.launch {
                active.getTranscriptionFlow().collect { result ->
                    _transcriptionFlow.emit(result)
                }
            }
        return active.startLiveTranscription()
    }

    private fun requestModuleInstall() {
        val request =
            SplitInstallRequest
                .newBuilder()
                .addModule(MODULE_NAME)
                .build()
        splitInstallManager
            .startInstall(request)
            .addOnSuccessListener { sessionId ->
                Napier.i("Module install session started (id=$sessionId)")
            }.addOnFailureListener { e ->
                Napier.e("Module install request failed", e)
                pendingLiveTranscription = false
                scope.launch {
                    _transcriptionFlow.emit(
                        TranscriptionResult.Error("Failed to start transcription engine download"),
                    )
                }
            }
    }

    override suspend fun stopLiveTranscription() {
        delegate?.stopLiveTranscription()
        pendingLiveTranscription = false
    }

    override suspend fun transcribeAudioFile(audioUri: String): TranscriptionResult =
        delegate?.transcribeAudioFile(audioUri)
            ?: TranscriptionResult.Error("Transcription engine not available")

    override fun cancelTranscription() {
        pendingLiveTranscription = false
        delegate?.cancelTranscription()
    }

    override fun getSupportedLanguages(): List<String> = delegate?.getSupportedLanguages() ?: emptyList()

    override fun setLanguage(languageCode: String) {
        delegate?.setLanguage(languageCode)
    }

    override val supportsLiveTranscription: Boolean
        get() = delegate?.supportsLiveTranscription ?: false

    override val supportsFileTranscription: Boolean
        get() = delegate?.supportsFileTranscription ?: false

    override suspend fun resetTranscription() {
        delegate?.resetTranscription()
    }

    override suspend fun warmUp() {
        delegate?.warmUp()
    }

    override val isOfflineModelAvailable: Boolean
        get() = delegate?.isOfflineModelAvailable == true

    override val offlineModelDownloadStatus: StateFlow<ModelDownloadStatus>
        get() = delegate?.offlineModelDownloadStatus ?: NotSupportedDownloadStatus

    override fun startOfflineModelDownload() {
        delegate?.startOfflineModelDownload()
    }

    override fun release() {
        splitInstallManager.unregisterListener(installListener)
        forwardingJob?.cancel()
        forwardingJob = null
        delegate?.release()
    }

    fun isSherpaOnnxAvailable(): Boolean = delegate != null

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
            Napier.i("Sherpa-ONNX transcription module loaded")
        } catch (e: ClassNotFoundException) {
            Napier.i("speech_recognition module class not found — will install on demand")
        } catch (e: Exception) {
            Napier.e("Failed to load speech_recognition module", e)
        }
    }
}
