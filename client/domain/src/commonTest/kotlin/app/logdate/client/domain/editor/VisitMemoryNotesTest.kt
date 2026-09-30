package app.logdate.client.domain.editor

import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.location.VisitMemoryContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class VisitMemoryNotesTest {
    @Test
    fun `all memory types use the historical place without backdating their creation`() {
        val created = Instant.parse("2026-09-29T22:00:00Z")
        val visit =
            VisitMemoryContext(
                "evidence",
                "00000000-0000-0000-0000-000000000072",
                "Mothership Coffee",
                36.1,
                -115.1,
                Instant.parse("2026-09-28T16:00:00Z"),
            )
        val notes =
            listOf(
                JournalNote.Text(creationTimestamp = created, lastUpdated = created, content = "Yesterday"),
                JournalNote.Image(creationTimestamp = created, lastUpdated = created, mediaRef = "photo"),
                JournalNote.Video(creationTimestamp = created, lastUpdated = created, mediaRef = "video"),
                JournalNote.Audio(creationTimestamp = created, lastUpdated = created, mediaRef = "audio"),
            )
        notes.forEach { original ->
            val updated = original.withVisitContext(visit)
            assertEquals(36.1, updated.location?.effectiveLatitude)
            assertEquals(-115.1, updated.location?.effectiveLongitude)
            assertEquals("Mothership Coffee", updated.location?.displayName)
            assertEquals(created, updated.creationTimestamp)
            assertEquals(created, updated.lastUpdated)
            assertEquals(original.uid, updated.uid)
        }
    }
}
