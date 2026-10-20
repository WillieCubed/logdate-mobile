package app.logdate.client.media.audio

import android.media.AudioRouting
import android.media.MediaRecorder
import android.os.Build
import app.logdate.client.media.audio.AudioRecordingService.Companion.DURATION_TICK_MS
import app.logdate.client.media.audio.AudioRecordingService.Companion.LEVEL_POLL_INTERVAL_MS
import app.logdate.client.media.audio.AudioRecordingService.Companion.MAX_AMPLITUDE
import app.logdate.client.media.device.AndroidAudioRouteDevices
import app.logdate.client.media.device.MediaDeviceKind
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Polls the input level and advances the elapsed time while the recorder runs. */
internal fun AudioRecordingService.monitorRecording() {
    val generation = sessionGeneration
    serviceScope.launch {
        while (mutableRecordingState.value.isRecording && sessionGeneration == generation) {
            if (!isPaused) {
                if (options.holdWakeLock && android.os.SystemClock.elapsedRealtime() - wakeLockAcquiredAt >= 15 * 60_000L) {
                    acquireWakeLock()
                }
                val amplitude =
                    try {
                        synchronized(recorderLock) {
                            val recorder = mediaRecorder
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                recorder?.activeRecordingConfiguration?.isClientSilenced == true
                            ) {
                                null
                            } else {
                                recorder?.maxAmplitude
                            }
                        }
                    } catch (e: Exception) {
                        Napier.e("Error getting audio level", e)
                        null
                    }
                val level = amplitude?.let { (it / MAX_AMPLITUDE).coerceIn(0f, 1f) }
                mutableRecordingState.update { it.copy(audioLevel = level ?: 0f) }
                if (options.quietSave &&
                    synchronized(recorderLock) {
                        quietSaveDetector.observe(
                            level,
                            android.os.SystemClock.elapsedRealtime(),
                            mutableRecordingState.value.routedInputDeviceId,
                        )
                    }
                ) {
                    synchronized(recorderLock) {
                        if (sessionGeneration != generation || !mutableRecordingState.value.isRecording || isPaused) return@launch
                        mutableRecordingState.update { it.copy(quietSaveTriggered = true) }
                        stopRecordingLocked()
                    }
                    break
                }
            }
            delay(LEVEL_POLL_INTERVAL_MS)
        }
    }
    serviceScope.launch {
        var elapsedTimeSeconds = 0
        while (mutableRecordingState.value.isRecording && sessionGeneration == generation) {
            delay(DURATION_TICK_MS)
            if (sessionGeneration != generation || !mutableRecordingState.value.isRecording) break
            if (!isPaused) {
                elapsedTimeSeconds++
            }
            mutableRecordingState.update { it.copy(durationSeconds = elapsedTimeSeconds) }
        }
    }
}

internal fun AudioRecordingService.createRecorder(
    file: File,
    inputDeviceId: String?,
): MediaRecorder {
    val recorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    return recorder.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(if (options.crashSafe) MediaRecorder.OutputFormat.AAC_ADTS else MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setOutputFile(file.absolutePath)
        setAudioEncodingBitRate(128000)
        setAudioSamplingRate(44100)
        if (options.maxDurationMs > 0) setMaxDuration(options.maxDurationMs.toInt())
        setOnInfoListener { _, what, _ -> onRecorderInfo(what) }
        setOnErrorListener { _, what, extra -> onRecorderError(what, extra) }
        applyPreferredInputDevice(inputDeviceId)
        addOnRoutingChangedListener(
            AudioRouting.OnRoutingChangedListener { routing ->
                synchronized(recorderLock) {
                    if (routing === mediaRecorder && mutableRecordingState.value.isRecording) publishInputRoute(this)
                }
            },
            null,
        )
    }
}

internal fun AudioRecordingService.publishInputRoute(recorder: MediaRecorder) {
    try {
        val deviceId = recorder.routedDevice?.let { AndroidAudioRouteDevices.deviceKey(it, MediaDeviceKind.AUDIO_INPUT) }
        mutableRecordingState.update { state ->
            state.copy(routedInputDeviceId = deviceId ?: state.routedInputDeviceId.takeIf { state.isPaused })
        }
    } catch (e: Exception) {
        Napier.w("Could not verify the recording microphone route", e)
    }
}
