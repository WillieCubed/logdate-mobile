package app.logdate.feature.library.ui.components

import androidx.compose.ui.unit.dp
import app.logdate.feature.library.ui.LibraryGridGroup
import app.logdate.feature.library.ui.LibraryMediaItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MediaThumbnailGridTest {
    @Test
    fun splitMediaGroupsForBookPostureSplitsSingleLargeGroupAcrossPanes() {
        val group = mediaGroup("March 2026", itemCount = 7)

        val (leftGroups, rightGroups) = splitMediaGroupsForBookPosture(listOf(group))

        assertEquals(listOf(4), leftGroups.map { it.items.size })
        assertEquals(listOf(3), rightGroups.map { it.items.size })
        assertEquals("March 2026", leftGroups.single().label)
        assertEquals("March 2026", rightGroups.single().label)
    }

    @Test
    fun splitMediaGroupsForBookPostureBalancesMultipleGroupsByItemCount() {
        val groups =
            listOf(
                mediaGroup("March 2026", itemCount = 3),
                mediaGroup("February 2026", itemCount = 2),
                mediaGroup("January 2026", itemCount = 4),
            )

        val (leftGroups, rightGroups) = splitMediaGroupsForBookPosture(groups)

        assertEquals(5, leftGroups.sumOf { it.items.size })
        assertEquals(4, rightGroups.sumOf { it.items.size })
        assertEquals(listOf("March 2026", "February 2026"), leftGroups.map { it.label })
        assertEquals(listOf("January 2026"), rightGroups.map { it.label })
    }

    @Test
    fun mediaGridColumnCountForBookPaneUsesPhysicalPaneWidth() {
        val columnCount =
            mediaGridColumnCountForBookPane(
                paneWidth = 708.dp,
                requestedColumnCount = 5,
            )

        assertEquals(4, columnCount)
    }

    private fun mediaGroup(
        label: String,
        itemCount: Int,
    ): LibraryGridGroup =
        LibraryGridGroup(
            label = label,
            items =
                List(itemCount) { index ->
                    LibraryMediaItem(
                        uid = Uuid.random(),
                        uri = "content://library/$label/$index",
                        thumbnailUri = null,
                        isVideo = false,
                        timestamp = Instant.fromEpochMilliseconds(index.toLong()),
                    )
                },
        )
}
