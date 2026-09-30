package app.logdate.feature.editor.ui.mapper

import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.getEditorDraftFingerprint
import app.logdate.shared.model.PhotoPresentation
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PhotoPresentationTest {
    @Test
    fun `photo style survives saving and reopening with its caption`() {
        val photo = ImageBlockUiState(uri = "photo.jpg", caption = "Our campsite", presentation = PhotoPresentation.Framed)
        val restored = photo.toJournalNote()!!.toDomainBlock() as ImageBlockUiState
        assertEquals(photo.presentation, restored.presentation)
        assertEquals(photo.caption, restored.caption)
    }

    @Test
    fun `style-only edits change the autosave fingerprint`() {
        val photo = ImageBlockUiState(uri = "photo.jpg")
        assertNotEquals(
            getEditorDraftFingerprint(EditorState(blocks = listOf(photo))),
            getEditorDraftFingerprint(EditorState(blocks = listOf(photo.copy(presentation = PhotoPresentation.Framed)))),
        )
    }

    @Test
    fun `serialized drafts preserve framing and old drafts default to edge to edge`() {
        val photo = ImageBlockUiState(uri = "photo.jpg", presentation = PhotoPresentation.Framed).toJournalNote()!!
        val json = Json.encodeToString<JournalNote>(photo)
        assertEquals(PhotoPresentation.Framed, (Json.decodeFromString<JournalNote>(json) as JournalNote.Image).presentation)
        val legacy = json.replace(",\"presentation\":\"Framed\"", "")
        assertEquals(PhotoPresentation.EdgeToEdge, (Json.decodeFromString<JournalNote>(legacy) as JournalNote.Image).presentation)
    }
}
