package app.logdate.client.data.notes

import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.PhotoPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PhotoPresentationMappingTest {
    @Test
    fun `database mapping preserves framing and tolerates an unknown future style`() {
        val now = Instant.fromEpochMilliseconds(1000)
        val photo =
            JournalNote.Image(
                creationTimestamp = now,
                lastUpdated = now,
                mediaRef = "photo.jpg",
                presentation = PhotoPresentation.Framed,
            )
        val entity = photo.toEntity()
        assertEquals(PhotoPresentation.Framed, entity.toModel().presentation)
        assertEquals(PhotoPresentation.EdgeToEdge, entity.copy(presentation = "future").toModel().presentation)
    }
}
