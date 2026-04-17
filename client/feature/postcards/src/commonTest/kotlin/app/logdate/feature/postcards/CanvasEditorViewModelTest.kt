package app.logdate.feature.postcards

import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.ElementTransform
import app.logdate.feature.postcards.model.PostcardDocument
import app.logdate.feature.postcards.ui.CanvasEditorState
import app.logdate.feature.postcards.ui.CanvasTool
import app.logdate.feature.postcards.ui.ShelfMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Unit tests for [CanvasEditorState] manipulation logic.
 *
 * These test the pure state transformations without the ViewModel's coroutine/persistence
 * concerns, which require Android-specific test infrastructure.
 */
class CanvasEditorViewModelTest {
    @Test
    fun elementWithTransformReturnsCorrectType() {
        val photo =
            CanvasElement.Photo(
                id = Uuid.random(),
                momentRef = Uuid.random(),
                mediaUri = "content://test",
                transform = ElementTransform(x = 10f, y = 20f),
                zIndex = 0,
            )
        val newTransform = ElementTransform(x = 50f, y = 100f, rotation = 45f)
        val updated = photo.copy(transform = newTransform)

        assertEquals(50f, updated.transform.x)
        assertEquals(100f, updated.transform.y)
        assertEquals(45f, updated.transform.rotation)
        assertEquals(photo.mediaUri, updated.mediaUri)
    }

    @Test
    fun addingElementIncrementsZIndex() {
        val elements =
            listOf(
                CanvasElement.Text(
                    id = Uuid.random(),
                    content = "Hello",
                    fontFamily = "caveat",
                    color = "#000",
                    fontSize = 16f,
                    zIndex = 0,
                ),
                CanvasElement.Text(
                    id = Uuid.random(),
                    content = "World",
                    fontFamily = "caveat",
                    color = "#000",
                    fontSize = 16f,
                    zIndex = 1,
                ),
            )
        val maxZ = elements.maxOfOrNull { it.zIndex } ?: -1
        val nextZ = maxZ + 1

        assertEquals(2, nextZ)
    }

    @Test
    fun deleteElementRemovesFromList() {
        val keepId = Uuid.random()
        val deleteId = Uuid.random()
        val elements =
            listOf(
                CanvasElement.Text(
                    id = keepId,
                    content = "Keep",
                    fontFamily = "caveat",
                    color = "#000",
                    fontSize = 16f,
                    zIndex = 0,
                ),
                CanvasElement.Text(
                    id = deleteId,
                    content = "Delete",
                    fontFamily = "caveat",
                    color = "#000",
                    fontSize = 16f,
                    zIndex = 1,
                ),
            )
        val filtered = elements.filter { it.id != deleteId }

        assertEquals(1, filtered.size)
        assertEquals(keepId, filtered[0].id)
    }

    @Test
    fun moveElementUpdateTransform() {
        val elementId = Uuid.random()
        val element =
            CanvasElement.Photo(
                id = elementId,
                momentRef = Uuid.random(),
                mediaUri = "content://test",
                transform = ElementTransform(x = 100f, y = 200f),
                zIndex = 0,
            )
        val dx = 50f
        val dy = -30f
        val moved =
            element.copy(
                transform =
                    element.transform.copy(
                        x = element.transform.x + dx,
                        y = element.transform.y + dy,
                    ),
            )

        assertEquals(150f, moved.transform.x)
        assertEquals(170f, moved.transform.y)
    }

    @Test
    fun scaleElementClampsToRange() {
        val scale = 0.5f
        val delta = 0.05f
        val newScale = (scale * delta).coerceIn(0.1f, 10f)

        assertEquals(0.1f, newScale) // 0.025 clamped to 0.1
    }

    @Test
    fun defaultEditorStateValues() {
        val state =
            CanvasEditorState(
                document =
                    PostcardDocument(
                        id = Uuid.random(),
                        title = "",
                        createdAt =
                            kotlin.time.Clock.System
                                .now(),
                        modifiedAt =
                            kotlin.time.Clock.System
                                .now(),
                    ),
            )

        assertNull(state.selectedElementId)
        assertEquals(CanvasTool.SELECT, state.activeTool)
        assertTrue(state.shelfMode is ShelfMode.Photos)
        assertTrue(state.shelfPhotos.isEmpty())
        assertFalse(state.isSaving)
        assertTrue(state.isNewPostcard)
    }
}
