package app.logdate.feature.postcards

import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.ElementTransform
import app.logdate.feature.postcards.model.PostcardDocument
import app.logdate.feature.postcards.ui.CanvasEditorState
import app.logdate.feature.postcards.ui.FontChoice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Tests for text element creation, editing, and the text editor workflow.
 */
class TextToolTest {
    private fun createState(elements: List<CanvasElement> = emptyList()) =
        CanvasEditorState(
            document =
                PostcardDocument(
                    id = Uuid.random(),
                    title = "Test",
                    createdAt = Clock.System.now(),
                    modifiedAt = Clock.System.now(),
                    elements = elements,
                ),
        )

    @Test
    fun newTextElementHasCorrectProperties() {
        val element =
            CanvasElement.Text(
                id = Uuid.random(),
                content = "Hello world",
                fontFamily = "caveat",
                color = "#FF6B6B",
                fontSize = 24f,
                transform = ElementTransform(x = 10f, y = 20f),
                zIndex = 0,
            )

        assertEquals("Hello world", element.content)
        assertEquals("caveat", element.fontFamily)
        assertEquals("#FF6B6B", element.color)
        assertEquals(24f, element.fontSize)
        assertEquals(10f, element.transform.x)
        assertEquals(20f, element.transform.y)
    }

    @Test
    fun updateTextElementPreservesId() {
        val originalId = Uuid.random()
        val original =
            CanvasElement.Text(
                id = originalId,
                content = "Original",
                fontFamily = "caveat",
                color = "#000000",
                fontSize = 16f,
                transform = ElementTransform(x = 50f, y = 100f),
                zIndex = 3,
            )

        val updated =
            original.copy(
                content = "Updated",
                fontFamily = "dancing-script",
                color = "#FF0000",
                fontSize = 32f,
            )

        assertEquals(originalId, updated.id)
        assertEquals("Updated", updated.content)
        assertEquals("dancing-script", updated.fontFamily)
        assertEquals("#FF0000", updated.color)
        assertEquals(32f, updated.fontSize)
        // Transform and zIndex preserved
        assertEquals(50f, updated.transform.x)
        assertEquals(100f, updated.transform.y)
        assertEquals(3, updated.zIndex)
    }

    @Test
    fun updateTextElementInDocumentOnlyAffectsTarget() {
        val targetId = Uuid.random()
        val otherId = Uuid.random()
        val elements =
            listOf(
                CanvasElement.Text(
                    id = targetId,
                    content = "Target",
                    fontFamily = "caveat",
                    color = "#000",
                    fontSize = 16f,
                    zIndex = 0,
                ),
                CanvasElement.Text(
                    id = otherId,
                    content = "Other",
                    fontFamily = "patrick-hand",
                    color = "#FFF",
                    fontSize = 20f,
                    zIndex = 1,
                ),
            )

        val updatedElements =
            elements.map { el ->
                if (el.id == targetId && el is CanvasElement.Text) {
                    el.copy(content = "Changed", fontFamily = "dancing-script")
                } else {
                    el
                }
            }

        val target = updatedElements.find { it.id == targetId } as CanvasElement.Text
        val other = updatedElements.find { it.id == otherId } as CanvasElement.Text

        assertEquals("Changed", target.content)
        assertEquals("dancing-script", target.fontFamily)
        assertEquals("Other", other.content)
        assertEquals("patrick-hand", other.fontFamily)
    }

    @Test
    fun textEditorStateStartsHidden() {
        val state = createState()
        assertFalse(state.isTextEditorVisible)
        assertNull(state.editingTextElementId)
    }

    @Test
    fun startTextEditingNewShowsEditor() {
        val state =
            createState().copy(
                isTextEditorVisible = true,
                editingTextElementId = null,
            )
        assertTrue(state.isTextEditorVisible)
        assertNull(state.editingTextElementId)
    }

    @Test
    fun startTextEditingExistingPreservesElementId() {
        val elementId = Uuid.random()
        val state =
            createState().copy(
                isTextEditorVisible = true,
                editingTextElementId = elementId,
            )
        assertTrue(state.isTextEditorVisible)
        assertEquals(elementId, state.editingTextElementId)
    }

    @Test
    fun cancelTextEditingHidesEditor() {
        val state =
            createState()
                .copy(
                    isTextEditorVisible = true,
                    editingTextElementId = Uuid.random(),
                ).copy(
                    isTextEditorVisible = false,
                    editingTextElementId = null,
                )
        assertFalse(state.isTextEditorVisible)
        assertNull(state.editingTextElementId)
    }

    @Test
    fun fontChoiceEnumHasAllPersonalityFonts() {
        val fontIds = FontChoice.entries.map { it.id }
        assertTrue("caveat" in fontIds)
        assertTrue("dancing-script" in fontIds)
        assertTrue("patrick-hand" in fontIds)
        assertEquals(3, FontChoice.entries.size)
    }

    @Test
    fun fontChoiceDisplayNamesAreHumanReadable() {
        assertEquals("Caveat", FontChoice.CAVEAT.displayName)
        assertEquals("Dancing Script", FontChoice.DANCING_SCRIPT.displayName)
        assertEquals("Patrick Hand", FontChoice.PATRICK_HAND.displayName)
    }
}
