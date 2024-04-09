package app.logdate.feature.onboarding.editor

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class DefaultAudioEntryRecorder @Inject constructor(
    context: Context,
) : AudioEntryRecorder {

    private var _isRecording = false

    private val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION") (MediaRecorder())
    }

    override fun startRecording(outputFilename: String) {
        if (_isRecording) {
            return
        }

        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_2_TS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFilename)
        }

        with(mediaRecorder) {
            prepare()
            start()
        }
    }

    override fun stopRecording() {
        if (!_isRecording) {
            return
        }
        mediaRecorder.stop()
        mediaRecorder.release()
    }

    override fun cancelRecording() {
        mediaRecorder.reset()
    }

    override val isRecording: Boolean
        get() = _isRecording

}