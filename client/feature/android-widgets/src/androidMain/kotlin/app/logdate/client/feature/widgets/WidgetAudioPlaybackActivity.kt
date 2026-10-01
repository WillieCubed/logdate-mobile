package app.logdate.client.feature.widgets

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.user.UserStateRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlin.uuid.Uuid

/** Handles the dedicated play action while leaving the rest of the card linked to the entry. */
class WidgetAudioPlaybackActivity : FragmentActivity() {
    private val notesRepository: JournalNotesRepository by inject()
    private val playbackManager: AudioPlaybackManager by inject()
    private val userStateRepository: UserStateRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val noteId = intent.getStringExtra(EXTRA_WIDGET_NOTE_ID)?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        if (noteId == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            try {
                if (!unlockWidgetContent(userStateRepository)) return@launch
                val note = notesRepository.getNoteById(noteId)
                if (note is JournalNote.Audio) {
                    playbackManager.startPlayback(
                        uri = note.mediaRef,
                        metadata = AudioPlaybackMetadata(title = getString(R.string.widget_audio_title), noteId = noteId),
                        onProgressUpdated = {},
                        onPlaybackCompleted = {},
                    )
                }
            } catch (error: Exception) {
                Napier.w("Unable to play audio from widget", error)
            } finally {
                finish()
            }
        }
    }
}
