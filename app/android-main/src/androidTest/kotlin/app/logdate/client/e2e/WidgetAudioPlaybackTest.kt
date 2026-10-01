package app.logdate.client.e2e

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.feature.widgets.EXTRA_WIDGET_NOTE_ID
import app.logdate.client.feature.widgets.WidgetAudioPlaybackActivity
import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class WidgetAudioPlaybackTest {
    @Test
    fun `widget audio action starts the selected recording`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val note = JournalNote.Audio(
            mediaRef = "file:///widget-test-recording.m4a",
            creationTimestamp = Clock.System.now(),
            lastUpdated = Clock.System.now(),
        )
        val playback = RecordingPlaybackManager()
        val testModule = module {
            single<JournalNotesRepository> { WidgetAudioNotesRepository(note) }
            single<AudioPlaybackManager> { playback }
        }
        loadKoinModules(testModule)
        try {
            context.startActivity(
                Intent(context, WidgetAudioPlaybackActivity::class.java)
                    .putExtra(EXTRA_WIDGET_NOTE_ID, note.uid.toString())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )

            assertTrue(playback.started.await(10, TimeUnit.SECONDS), "The widget play action did not start playback")
            assertEquals(note.mediaRef, playback.uri)
            assertEquals(note.uid, playback.metadata?.noteId)
        } finally {
            unloadKoinModules(testModule)
        }
    }

}

private class RecordingPlaybackManager : AudioPlaybackManager {
    val started = CountDownLatch(1)
    @Volatile var uri: String? = null
    @Volatile var metadata: AudioPlaybackMetadata? = null

    override fun startPlayback(
        uri: String,
        metadata: AudioPlaybackMetadata?,
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit,
    ) {
        this.uri = uri
        this.metadata = metadata
        started.countDown()
    }

    override fun pausePlayback() {}
    override fun stopPlayback() {}
    override fun seekTo(position: Float) {}
    override fun release() {}
}

private class WidgetAudioNotesRepository(private val note: JournalNote.Audio) : JournalNotesRepository {
    override val allNotesObserved: Flow<List<JournalNote>> = flowOf(listOf(note))
    override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = allNotesObserved
    override fun observeNotesInRange(start: Instant, end: Instant): Flow<List<JournalNote>> = allNotesObserved
    override fun observeNotesPage(pageSize: Int, offset: Int): Flow<List<JournalNote>> = allNotesObserved
    override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = allNotesObserved
    override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = allNotesObserved
    override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()
    override suspend fun getNoteById(noteId: Uuid): JournalNote? = note.takeIf { it.uid == noteId }
    override suspend fun create(note: JournalNote): Uuid = error("Not supported")
    override suspend fun remove(note: JournalNote) {}
    override suspend fun removeById(noteId: Uuid) {}
    override suspend fun create(note: JournalNote, journalId: Uuid) {}
    override suspend fun removeFromJournal(noteId: Uuid, journalId: Uuid) {}
}
