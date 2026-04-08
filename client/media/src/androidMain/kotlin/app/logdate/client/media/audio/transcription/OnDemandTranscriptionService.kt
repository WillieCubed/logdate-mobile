package app.logdate.client.media.audio.transcription

import android.content.Context
import app.logdate.client.media.audio.download.ModelDownloadStatus
import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.DataUsagePolicy
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
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
 *
 * Downloads are gated on [DataUsagePolicy]: [DataUsageMode.Restricted] (Data
 * Saver active or no connection) blocks the install immediately with a clear
 * error. [DataUsageMode.Conservative] (cellular, no Data Saver) and
 * [DataUsageMode.Unrestricted] (Wi-Fi) both proceed — the module is ~15 MB
 * and is required for the feature to function at all.
 */
class OnDemandTranscriptionService(
    private val context: Context,
    private val scope: CoroutineScope,
    private val dataUsagePolicy: DataUsagePolicy,
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
                SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                    // The system confirmation dialog requires an Activity reference that
                    // the service layer cannot hold. Surface an error so recording can
                    // continue without transcription; retrying on an unmetered network
                    // (Wi-Fi) bypasses the confirmation requirement entirely.
                    Napier.i("speech_recognition download requires user confirmation")
                    pendingLiveTranscription = false
                    scope.launch {
                        _transcriptionFlow.emit(
                            TranscriptionResult.Error(TranscriptionFailure.Unknown),
                        )
                    }
                }
                SplitInstallSessionStatus.FAILED -> {
                    val failureReason =
                        when (state.errorCode()) {
                            SplitInstallErrorCode.NETWORK_ERROR -> TranscriptionFailure.NoNetwork
                            SplitInstallErrorCode.INSUFFICIENT_STORAGE -> TranscriptionFailure.OutOfStorage
                            else -> TranscriptionFailure.Unknown
                        }
                    Napier.e("speech_recognition module install failed: errorCode=${state.errorCode()}")
                    pendingLiveTranscription = false
                    scope.launch {
                        _transcriptionFlow.emit(
                            TranscriptionResult.Error(failureReason),
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
            if (!requestModuleInstall()) return false
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

    /**
     * Submits a Play Core install request for the [MODULE_NAME] feature module.
     *
     * Returns `false` and emits a [TranscriptionResult.Error] to
     * [_transcriptionFlow] if a pre-flight check prevents the download (Data
     * Saver active, or no network connection). Returns `true` if the request
     * was submitted — the actual outcome arrives via [installListener].
     */
    private suspend fun requestModuleInstall(): Boolean {
        val mode = dataUsagePolicy.currentMode()
        if (mode is DataUsageMode.Restricted) {
            Napier.w("speech_recognition download skipped — network restricted (Data Saver or no connection)")
            _transcriptionFlow.emit(TranscriptionResult.Error(TranscriptionFailure.NoNetwork))
            return false
        }

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
                        TranscriptionResult.Error(TranscriptionFailure.Unknown),
                    )
                }
            }
        return true
    }

    override suspend fun stopLiveTranscription() {
        delegate?.stopLiveTranscription()
        pendingLiveTranscription = false
    }

    override suspend fun transcribeAudioFile(audioUri: String): TranscriptionResult =
        delegate?.transcribeAudioFile(audioUri)
            ?: TranscriptionResult.Error(TranscriptionFailure.NotAvailable)

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
