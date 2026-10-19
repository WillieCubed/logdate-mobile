package app.logdate.client.launch

import app.logdate.client.media.audio.CrashSafeRecording
import io.github.aakira.napier.Napier
import java.io.File

/** Publishes completed files left by an interrupted recording without restarting capture. */
internal fun recoverCompletedRecordingFiles(filesDirectory: File) {
    runCatching {
        val recovered = CrashSafeRecording.recoverCompletedRecordings(filesDirectory.resolve("audio_notes"))
        if (recovered > 0) Napier.i("Recovered $recovered completed audio recording(s)", tag = "LogDateStartup")
    }.onFailure { Napier.w("Completed audio recordings remain queued for recovery", it, tag = "LogDateStartup") }
}
