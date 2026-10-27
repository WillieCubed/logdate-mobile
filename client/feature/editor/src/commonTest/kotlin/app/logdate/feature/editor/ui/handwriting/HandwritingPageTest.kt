package app.logdate.feature.editor.ui.handwriting

import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.InkPoint
import app.logdate.feature.postcards.model.InkTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class HandwritingPageTest {
    private val stroke =
        CanvasElement.Ink(
            id = Uuid.random(),
            tool = InkTool.PEN,
            color = "#253457",
            strokeWidth = 3f,
            points = listOf(InkPoint(20f, 30f, 0.25f), InkPoint(300f, 30f, 0.9f)),
        )

    @Test
    fun `original coordinates pressure and tool survive document serialization`() {
        val page = HandwritingPage(strokes = listOf(stroke))
        val restored = HandwritingPage.decode(page.encode())
        assertEquals(page, restored)
    }

    @Test
    fun `erasing between sampled points removes the whole touched stroke`() {
        val page = HandwritingPage(strokes = listOf(stroke))
        val erased = page.erase(listOf(InkPoint(160f, 0f), InkPoint(160f, 70f)), radius = 8f)
        assertTrue(erased.strokes.isEmpty())
        assertEquals(listOf(stroke), page.strokes)
    }

    @Test
    fun `eraser leaves distant strokes intact`() {
        val page = HandwritingPage(strokes = listOf(stroke))
        assertEquals(page, page.erase(listOf(InkPoint(400f, 400f)), radius = 8f))
    }

    @Test
    fun `undo and redo restore the exact original handwriting`() {
        val page = HandwritingPage(strokes = listOf(stroke))
        val history = HandwritingHistory().edit(page)
        assertEquals(page, history.page)
        assertEquals(HandwritingPage(), history.undo().page)
        assertEquals(page, history.undo().redo().page)
    }

    @Test
    fun `new writing clears redo and empty actions do not create undo steps`() {
        val page = HandwritingPage(strokes = listOf(stroke))
        val undone = HandwritingHistory().edit(page).undo()
        val replacement = page.copy(strokes = listOf(stroke.copy(color = "#872F4A")))
        assertTrue(undone.edit(replacement).future.isEmpty())
        assertEquals(HandwritingHistory(), HandwritingHistory().edit(HandwritingPage()))
    }

    @Test
    fun `practice entry preserves edited text alongside the original ink`() {
        val entry = HandwritingPracticeEntry(HandwritingPage(strokes = listOf(stroke)), text = "A quieter walk home.")
        assertEquals(entry, HandwritingPracticeEntry.decode(entry.encode()))
    }
}
