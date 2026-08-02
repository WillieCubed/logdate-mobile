package app.logdate.feature.editor.ui.text

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextFieldValueSyncTest {
    @Test
    fun selectionAndCompositionOnlyChangesDoNotPropagateText() {
        val previous = TextFieldValue(text = "draft", selection = TextRange(5))
        val next =
            TextFieldValue(
                text = "draft",
                selection = TextRange(1, 4),
                composition = TextRange(0, 5),
            )

        assertFalse(shouldPropagateTextChange(previous, next))
    }

    @Test
    fun localTextChangesPropagateBeforeParentRecomposition() {
        val previous = TextFieldValue(text = "draft", selection = TextRange(5))
        val next = TextFieldValue(text = "drafts", selection = TextRange(6))

        assertTrue(shouldPropagateTextChange(previous, next))
    }

    @Test
    fun matchingExternalTextPreservesSelectionAndComposition() {
        val current =
            TextFieldValue(
                text = "draft",
                selection = TextRange(2, 4),
                composition = TextRange(1, 5),
            )

        val merged = mergeExternalText(current, "draft")

        assertEquals(current, merged)
    }

    @Test
    fun externalAppendPreservesAMidStringSelection() {
        val current = TextFieldValue(text = "draft", selection = TextRange(2, 4))

        val merged = mergeExternalText(current, "draft restored")

        assertEquals("draft restored", merged.text)
        assertEquals(TextRange(2, 4), merged.selection)
        assertEquals(null, merged.composition)
    }

    @Test
    fun shorterExternalTextClampsSelectionToTheNewText() {
        val current = TextFieldValue(text = "long draft", selection = TextRange(4, 10))

        val merged = mergeExternalText(current, "short")

        assertEquals("short", merged.text)
        assertEquals(TextRange(4, 5), merged.selection)
    }
}
