package app.logdate.client.sync

import android.content.Context
import app.logdate.client.domain.watch.WatchNotificationSettings
import app.logdate.client.domain.watch.WatchSettingsRepository
import app.logdate.client.repository.journals.JournalNote
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import app.logdate.client.notifications.R as NotificationResources

/**
 * The Watch settings switch for new-entry notifications was stored and never read: every note
 * from the watch posted a notification, always in English.
 */
class WatchNoteArrivalNotifierTest {
    private val context =
        mockk<Context> {
            every { getString(NotificationResources.string.notification_watch_note_audio_title) } returns "Voice note from your watch"
            every { getString(NotificationResources.string.notification_watch_note_text_title) } returns "Note from your watch"
            every { getString(NotificationResources.string.notification_watch_note_photo_title) } returns "Photo from your watch"
            every { getString(NotificationResources.string.notification_watch_note_video_title) } returns "Video from your watch"
            every { getString(NotificationResources.string.notification_watch_note_open_body) } returns "Tap to open it in the editor"
        }
    private val posted = mutableListOf<Pair<Uuid, WatchNoteArrivalContent>>()
    private val settings = FakeWatchSettingsRepository()
    private val notifier =
        WatchNoteArrivalNotifier(
            context = context,
            settingsRepository = settings,
            poster = { noteId, content -> posted += noteId to content },
        )
    private val now = Instant.fromEpochMilliseconds(1_710_000_000_000)

    @Test
    fun `no notification is posted while new-entry notifications are off`() =
        runTest {
            settings.showEntryNotifications.value = false

            notifier.noteReceived(audioNote())

            assertTrue(posted.isEmpty())
        }

    @Test
    fun `a voice note from the watch is announced with the localized copy`() =
        runTest {
            val note = audioNote()

            notifier.noteReceived(note)

            assertEquals(listOf(note.uid to WatchNoteArrivalContent("Voice note from your watch", "Tap to open it in the editor")), posted)
        }

    @Test
    fun `photos and videos from the watch are announced with their own titles`() =
        runTest {
            notifier.noteReceived(
                JournalNote.Image(uid = Uuid.random(), creationTimestamp = now, lastUpdated = now, mediaRef = "/storage/photo.jpg"),
            )
            notifier.noteReceived(
                JournalNote.Video(uid = Uuid.random(), creationTimestamp = now, lastUpdated = now, mediaRef = "/storage/clip.mp4"),
            )

            assertEquals(
                listOf(
                    WatchNoteArrivalContent("Photo from your watch", "Tap to open it in the editor"),
                    WatchNoteArrivalContent("Video from your watch", "Tap to open it in the editor"),
                ),
                posted.map { it.second },
            )
        }

    @Test
    fun `a short text note shows all of what was written`() =
        runTest {
            notifier.noteReceived(textNote("#mood: calm after the walk"))

            assertEquals(WatchNoteArrivalContent("Note from your watch", "#mood: calm after the walk"), posted.single().second)
        }

    @Test
    fun `a long text note previews the first 80 characters of what was written`() =
        runTest {
            notifier.noteReceived(textNote("A".repeat(200)))

            assertEquals(WatchNoteArrivalContent("Note from your watch", "A".repeat(80)), posted.single().second)
        }

    private fun audioNote() =
        JournalNote.Audio(
            uid = Uuid.random(),
            creationTimestamp = now,
            lastUpdated = now,
            mediaRef = "/storage/recording.m4a",
            durationMs = 4_200,
        )

    private fun textNote(content: String) =
        JournalNote.Text(uid = Uuid.random(), creationTimestamp = now, lastUpdated = now, content = content)

    private class FakeWatchSettingsRepository : WatchSettingsRepository {
        val showEntryNotifications = MutableStateFlow(true)

        override fun observeNotificationSettings(): Flow<WatchNotificationSettings> =
            showEntryNotifications.map { WatchNotificationSettings(showEntryNotifications = it) }

        override suspend fun setShowEntryNotifications(enabled: Boolean) {
            showEntryNotifications.value = enabled
        }
    }
}
