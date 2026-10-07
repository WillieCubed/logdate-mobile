package app.logdate.feature.editor.ui.blocks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntryAddGestureStateTest {
    @Test
    fun `open panel follows a short drag and stays open until release`() {
        val gesture = EntryAddGestureState(expanded = true).dragBy(48f)
        assertTrue(gesture.expanded)
        assertEquals(128f / 176f, gesture.progress(176f), 0.001f)
        assertTrue(gesture.release(112f).expanded)
    }

    @Test
    fun `a deliberate closing pull settles closed only on release`() {
        val gesture = EntryAddGestureState(expanded = true).dragBy(128f)
        assertTrue(gesture.expanded)
        assertFalse(gesture.release(112f).expanded)
    }

    @Test
    fun `reversing a closing pull restores the open panel`() {
        val gesture = EntryAddGestureState(expanded = true).dragBy(128f).dragBy(-104f)
        assertTrue(gesture.release(112f).expanded)
    }

    @Test
    fun `reversing after the panel reaches its limit immediately reveals it again`() {
        val gesture = EntryAddGestureState(expanded = true).dragBy(400f, maxDistance = 176f).dragBy(-100f, maxDistance = 176f)
        assertEquals(100f / 176f, gesture.progress(176f), 0.001f)
        assertTrue(gesture.release(112f).expanded)
    }

    @Test
    fun `canceling a pull preserves its initial panel state`() {
        assertTrue(EntryAddGestureState(expanded = true).dragBy(128f).cancel().expanded)
        assertFalse(EntryAddGestureState().dragBy(-128f).cancel().expanded)
    }

    @Test
    fun `opening follows the finger and reversals do not expand the panel`() {
        val gesture = EntryAddGestureState().dragBy(-128f)
        assertEquals(128f / 176f, gesture.progress(176f), 0.001f)
        assertFalse(gesture.expanded)
        assertTrue(gesture.release(112f).expanded)
        assertFalse(gesture.dragBy(96f).release(112f).expanded)
    }
}
