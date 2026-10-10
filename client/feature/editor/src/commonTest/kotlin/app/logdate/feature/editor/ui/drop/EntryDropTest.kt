package app.logdate.feature.editor.ui.drop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntryDropTest {
    @Test
    fun `the editor accepts text, photos, videos, and audio`() {
        listOf("text/plain", "text/html", "image/jpeg", "image/*", "video/mp4", "audio/mpeg").forEach { mime ->
            assertTrue(acceptsDroppedMimeType(mime), "$mime should be accepted")
        }
    }

    @Test
    fun `the editor refuses files it cannot place in an entry`() {
        listOf("application/pdf", "application/zip", "").forEach { mime ->
            assertFalse(acceptsDroppedMimeType(mime), "$mime should be refused")
        }
    }

    @Test
    fun `dropped items split into media to import and text to insert`() {
        val items =
            listOf(
                DroppedItem(mimeType = "image/jpeg", uri = "content://photos/1", text = null),
                DroppedItem(mimeType = "text/plain", uri = null, text = "A note from the browser"),
                DroppedItem(mimeType = "video/mp4", uri = "content://videos/2", text = null),
                DroppedItem(mimeType = "application/pdf", uri = "content://docs/3", text = null),
            )

        assertEquals(
            EntryDrop(mediaUris = listOf("content://photos/1", "content://videos/2"), texts = listOf("A note from the browser")),
            routeDroppedItems(items),
        )
    }
}
