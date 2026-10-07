package app.logdate.client.sync.conflict

import app.logdate.client.repository.journals.JournalNote
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Instant

class AudioCaptionConflictTest {
    @Test
    fun `different audio captions require review instead of silently taking remote metadata`() {
        val created = Instant.parse("2026-01-02T03:04:05Z")
        val local =
            JournalNote.Audio(
                mediaRef = "/audio/station.m4a",
                creationTimestamp = created,
                lastUpdated = created,
                caption = "At the station",
            )
        val remote = local.copy(caption = "Back at home")

        val result = JournalNoteConflictResolver().resolve(local, remote, created, created)

        assertIs<ConflictResolution.RequiresManualResolution<JournalNote>>(result)
    }
}
