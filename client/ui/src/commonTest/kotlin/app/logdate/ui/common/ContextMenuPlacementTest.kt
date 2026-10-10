package app.logdate.ui.common

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class ContextMenuPlacementTest {
    @Test
    fun `a right-click menu opens at the pointer rather than under the item`() {
        // Dropdown offsets are measured from the anchor's bottom-left corner.
        assertEquals(Offset(40f, -60f), contextMenuOffset(pointer = Offset(40f, 20f), anchorHeight = 80))
    }

    @Test
    fun `a click at the bottom-left corner needs no offset`() {
        assertEquals(Offset(0f, 0f), contextMenuOffset(pointer = Offset(0f, 80f), anchorHeight = 80))
    }
}
