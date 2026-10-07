package app.logdate.feature.speech.recognition

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import app.logdate.client.media.device.AndroidAudioRouteDevices
import io.github.aakira.napier.Napier

internal class TranscriptionAudioCapture(
    private val context: Context,
) {
    private var audioRecord: AudioRecord? = null
    private val inputRouteLock = Any()
    private var preferredInputDeviceId: String? = null

    fun updatePreferredInputDevice(deviceId: String?): Boolean {
        synchronized(inputRouteLock) {
            preferredInputDeviceId = deviceId
            return audioRecord?.let(::applyInputRoute) ?: true
        }
    }

    private fun applyInputRoute(recorder: AudioRecord): Boolean {
        return try {
            val device = AndroidAudioRouteDevices.findPreferredInputDevice(context, preferredInputDeviceId)
            if (preferredInputDeviceId != null && device == null) return false
            if (!recorder.setPreferredDevice(device)) {
                Napier.w("Could not apply the live transcription microphone route")
                return false
            }
            recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING ||
                (recorder.routedDevice != null && (device == null || recorder.routedDevice.id == device.id))
        } catch (e: Exception) {
            Napier.e("Could not switch the live transcription microphone", e)
            false
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(minimumBufferSizeBytes: Int): AudioRecord {
        val bufferSize =
            AudioRecord
                .getMinBufferSize(
                    SherpaOnnxRecognizerProvider.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(minimumBufferSizeBytes)

        val ar =
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SherpaOnnxRecognizerProvider.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )

        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            ar.release()
            throw AudioCaptureException("AudioRecord failed to initialize")
        }

        synchronized(inputRouteLock) {
            try {
                if (!applyInputRoute(ar)) throw AudioCaptureException("Could not apply transcription input")
                ar.startRecording()
                audioRecord = ar
            } catch (e: Exception) {
                ar.release()
                throw e
            }
        }
        return ar
    }

    fun stop() {
        synchronized(inputRouteLock) {
            val recorder = audioRecord
            audioRecord = null
            try {
                recorder?.stop()
            } catch (e: Exception) {
                Napier.e("Error stopping AudioRecord", e)
            } finally {
                try {
                    recorder?.release()
                } catch (e: Exception) {
                    Napier.e("Error releasing AudioRecord", e)
                }
            }
        }
    }

}

internal class AudioCaptureException(
    message: String,
) : IllegalStateException(message)
