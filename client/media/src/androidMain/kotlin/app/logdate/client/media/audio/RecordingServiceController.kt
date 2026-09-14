package app.logdate.client.media.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The recording manager's view of [AudioRecordingService].
 *
 * Everything that touches the Android service (starting it in the foreground, binding,
 * calling through the binder, tearing it down) lives behind this interface so the
 * manager's session state machine can be exercised without a device.
 */
interface RecordingServiceController {
    /**
     * Live state mirrored from the bound service. Null while no service is bound, which
     * includes the window between [start] and the service connecting.
     */
    val serviceState: StateFlow<RecordingServiceState?>

    /**
     * Starts the foreground service recording to [outputPath] and binds to it.
     *
     * @return false when the service could not be started or the bind was refused. The
     *   caller still owns [shutdown] in that case.
     */
    fun start(
        outputPath: String,
        inputDeviceId: String?,
    ): Boolean

    /**
     * Finalizes the recorder through the binder and returns the recorded file path, or null
     * when the service is not bound, not recording, or failed to finalize the file.
     */
    fun stopRecordingNow(): String?

    /** Stops the service and releases the binding. Safe to call when nothing is running. */
    fun shutdown()

    fun pause(): Boolean

    fun resume(): Boolean

    fun updatePreferredInputDevice(inputDeviceId: String?)
}

/**
 * Production [RecordingServiceController] backed by the app's [AudioRecordingService].
 */
class AndroidRecordingServiceController(
    private val context: Context,
) : RecordingServiceController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableServiceState = MutableStateFlow<RecordingServiceState?>(null)
    override val serviceState: StateFlow<RecordingServiceState?> = mutableServiceState.asStateFlow()

    @Volatile
    private var service: AudioRecordingService? = null

    @Volatile
    private var bindRequested = false
    private var mirrorJob: Job? = null

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?,
            ) {
                val connected = (binder as? AudioRecordingService.AudioServiceBinder)?.getService()
                if (connected == null) {
                    Napier.e("Recording service connected with an unexpected binder")
                    return
                }
                service = connected
                mirrorJob?.cancel()
                mirrorJob =
                    scope.launch {
                        connected.recordingState.collect { state -> mutableServiceState.value = state }
                    }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                mirrorJob?.cancel()
                mirrorJob = null
                service = null
                mutableServiceState.value = null
            }
        }

    override fun start(
        outputPath: String,
        inputDeviceId: String?,
    ): Boolean =
        try {
            context.startAudioRecordingService(outputFilePath = outputPath, inputDeviceId = inputDeviceId)
            bindRequested = true
            val bound = context.bindService(Intent(context, AudioRecordingService::class.java), connection, Context.BIND_AUTO_CREATE)
            if (!bound) Napier.e("Recording service refused the bind request")
            bound
        } catch (e: Exception) {
            Napier.e("Error starting the recording service", e)
            false
        }

    override fun stopRecordingNow(): String? =
        try {
            service?.stopRecording()
        } catch (e: Exception) {
            Napier.e("Error finalizing the recording through the service binder", e)
            null
        }

    override fun shutdown() {
        mirrorJob?.cancel()
        mirrorJob = null
        try {
            context.stopAudioRecordingService()
        } catch (e: Exception) {
            Napier.e("Error stopping the recording service", e)
        }
        if (bindRequested) {
            bindRequested = false
            try {
                context.unbindService(connection)
            } catch (e: Exception) {
                Napier.e("Error unbinding from the recording service", e)
            }
        }
        service = null
        mutableServiceState.value = null
    }

    override fun pause(): Boolean {
        val active = service ?: return false
        active.pauseRecording()
        return active.isRecordingPaused()
    }

    override fun resume(): Boolean {
        val active = service ?: return false
        active.resumeRecording()
        return !active.isRecordingPaused()
    }

    override fun updatePreferredInputDevice(inputDeviceId: String?) {
        service?.updatePreferredInputDevice(inputDeviceId)
    }
}
