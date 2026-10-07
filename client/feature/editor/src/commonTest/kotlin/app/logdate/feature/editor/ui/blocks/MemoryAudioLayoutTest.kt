package app.logdate.feature.editor.ui.blocks

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class MemoryAudioLayoutTest {
    @Test
    fun `portrait recording fills the entry viewport while reserving Add`() {
        assertEquals(700.dp, unfinishedAudioHeight(viewportHeight = 780.dp, viewportWidth = 390.dp))
        assertEquals(920.dp, unfinishedAudioHeight(viewportHeight = 1000.dp, viewportWidth = 390.dp))
    }

    @Test
    fun `expanded Add choices reserve room beside the recording controls`() {
        assertEquals(524.dp, unfinishedAudioHeight(viewportHeight = 780.dp, viewportWidth = 390.dp, footerHeight = 232.dp))
    }

    @Test
    fun `focused recording reclaims footer space without leaving its spacer`() {
        assertEquals(772.dp, unfinishedAudioHeight(viewportHeight = 780.dp, viewportWidth = 390.dp, footerHeight = 0.dp))
    }

    @Test
    fun `wider recording surfaces have a reasonable maximum height`() {
        assertEquals(640.dp, unfinishedAudioHeight(viewportHeight = 1000.dp, viewportWidth = 900.dp))
    }

    @Test
    fun `short viewports retain enough room for recording controls and can scroll the entry`() {
        assertEquals(420.dp, unfinishedAudioHeight(viewportHeight = 300.dp, viewportWidth = 700.dp))
    }
}
