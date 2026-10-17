package app.logdate.client.text

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UiTextTest {
    @Test
    fun rawTextResolvesToItsValue() =
        runTest {
            assertEquals("Pixel Buds Pro", UiText.Raw("Pixel Buds Pro").resolve())
        }

    @Test
    fun rawTextWithTheSameValueIsEqual() {
        assertEquals(UiText.Raw("Front camera"), "Front camera".toUiText())
        assertNotEquals(UiText.Raw("Front camera"), UiText.Raw("Back camera"))
    }
}
