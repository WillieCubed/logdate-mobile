package app.logdate.feature.postcards

import androidx.compose.ui.unit.dp
import app.logdate.feature.postcards.ui.postcardGridColumnCountForBookPane
import app.logdate.feature.postcards.ui.splitPostcardsForBookPosture
import kotlin.test.Test
import kotlin.test.assertEquals

class PostcardsCollectionLayoutTest {
    @Test
    fun splitPostcardsForBookPostureBalancesCardsAcrossPanes() {
        val (startPaneItems, endPaneItems) = splitPostcardsForBookPosture((1..7).toList())

        assertEquals(listOf(1, 2, 3, 4), startPaneItems)
        assertEquals(listOf(5, 6, 7), endPaneItems)
    }

    @Test
    fun splitPostcardsForBookPostureKeepsSingleCardInStartPane() {
        val (startPaneItems, endPaneItems) = splitPostcardsForBookPosture(listOf("only"))

        assertEquals(listOf("only"), startPaneItems)
        assertEquals(emptyList(), endPaneItems)
    }

    @Test
    fun postcardGridColumnCountForBookPaneUsesPhysicalPaneWidth() {
        val columnCount =
            postcardGridColumnCountForBookPane(
                paneWidth = 708.dp,
                requestedColumnCount = 4,
            )

        assertEquals(3, columnCount)
    }
}
