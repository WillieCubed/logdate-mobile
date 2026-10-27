package app.logdate.feature.editor.ui.handwriting

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class EntryPageStateRestorationTest {
    @Test
    fun restoresBothScrollPositionsAndTheFocusedBlockAnchor() {
        val id = Uuid.random()
        val before =
            EntryPageOverviewState(true, initialScroll = 640, initialHistoryScroll = 320).apply {
                focusedAnchor = EntryPageScrollAnchor(id, 240f, 1000)
            }
        val scope =
            object : SaverScope {
                override fun canBeSaved(value: Any) = true
            }
        val saved = with(EntryPageStateSaver) { scope.save(before) }
        val after = assertNotNull(EntryPageStateSaver.restore(assertNotNull(saved)))
        assertEquals(640, after.scroll.value)
        assertEquals(320, after.historyScroll.value)
        assertEquals(before.focusedAnchor, after.focusedAnchor)
        assertEquals(true, after.earlierExpanded)
    }
}
