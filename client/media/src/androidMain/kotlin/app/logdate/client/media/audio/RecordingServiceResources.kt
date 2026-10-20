package app.logdate.client.media.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.PowerManager
import app.logdate.client.media.audio.AudioRecordingService.Companion.DEFAULT_WAKE_LOCK_TIMEOUT_MS
import app.logdate.client.media.audio.AudioRecordingService.Companion.WAKE_LOCK_HEADROOM_MS
import app.logdate.client.media.audio.AudioRecordingService.Companion.WAKE_LOCK_TAG
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun AudioRecordingService.acquireSessionResources() {
    releaseSessionResources()
    acquireWakeLock()
    if (options.pauseOnInterruption) requestAudioFocus()
}

/**
 * The lock's timeout is the longest the session can still record. The recorder counts recorded
 * time only, so it is dropped while paused and taken again, with a fresh timeout, on resume.
 */
internal fun AudioRecordingService.acquireWakeLock() {
    if (!options.holdWakeLock) return
    releaseWakeLock()
    wakeLockAcquiredAt = android.os.SystemClock.elapsedRealtime()
    try {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val timeoutMs =
            if (options.maxDurationMs > 0) options.maxDurationMs + WAKE_LOCK_HEADROOM_MS else DEFAULT_WAKE_LOCK_TIMEOUT_MS
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply { acquire(timeoutMs) }
    } catch (e: Exception) {
        Napier.w("Could not acquire the recording wake lock", e)
    }
}

internal fun AudioRecordingService.releaseWakeLock() {
    try {
        if (wakeLock?.isHeld == true) wakeLock?.release()
    } catch (e: Exception) {
        Napier.w("Error releasing the recording wake lock", e)
    }
    wakeLock = null
}

internal fun AudioRecordingService.requestAudioFocus() {
    try {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                ).setOnAudioFocusChangeListener(audioFocusListener)
                .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    } catch (e: Exception) {
        Napier.w("Could not request audio focus for the recording", e)
    }
}

internal fun AudioRecordingService.releaseSessionResources() {
    releaseWakeLock()
    val request = focusRequest ?: return
    focusRequest = null
    try {
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(request)
    } catch (e: Exception) {
        Napier.w("Error abandoning audio focus", e)
    }
}

/**
 * The recorder ended the session itself at a configured limit. Finalizing here means the
 * file is complete before the bound client learns the session is over.
 */
internal fun AudioRecordingService.onRecorderInfo(what: Int) {
    val limitReached =
        what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
            what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED
    if (!limitReached) return
    Napier.d("Recorder reached its limit ($what); finalizing the recording")
    serviceScope.launch {
        stopRecording()
        releaseSessionResources()
    }
}

internal fun AudioRecordingService.onRecorderError(
    what: Int,
    extra: Int,
) {
    Napier.e("Recorder error what=$what extra=$extra")
    serviceScope.launch {
        stopRecording()
        releaseSessionResources()
        mutableRecordingState.update { it.copy(failureMessage = "Recording stopped unexpectedly (error $what)") }
    }
}

internal fun AudioRecordingService.releaseQuietly(recorder: MediaRecorder?) {
    if (recorder == null) return
    try {
        recorder.reset()
    } catch (e: Exception) {
        Napier.w("Error resetting recorder", e)
    }
    try {
        recorder.release()
    } catch (e: Exception) {
        Napier.w("Error releasing recorder", e)
    }
}
