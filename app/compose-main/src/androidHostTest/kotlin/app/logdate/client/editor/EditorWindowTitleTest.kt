package app.logdate.client.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditorWindowTitleTest {
    @Test
    fun `an empty entry has no title of its own`() {
        assertNull(editorWindowTitle(null))
        assertNull(editorWindowTitle("   \n  "))
    }

    @Test
    fun `the title is the first line the person wrote`() {
        assertEquals("Coffee with Maya", editorWindowTitle("Coffee with Maya\nWe talked about the move."))
    }

    @Test
    fun `long first lines are shortened on a word boundary`() {
        assertEquals(
            "Walked the whole length of the river path…",
            editorWindowTitle("Walked the whole length of the river path this morning before anyone else was up"),
        )
    }
}
