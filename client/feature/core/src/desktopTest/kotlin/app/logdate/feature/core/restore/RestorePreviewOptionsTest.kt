package app.logdate.feature.core.restore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.client.domain.export.ExportSchemaVersion
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.domain.restore.ArchivePreview
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class RestorePreviewOptionsTest {
    @Test
    fun labelAndSwitchChangeOnlyTheirOwnImportOptionExactlyOnce() =
        runDesktopComposeUiTest(width = 411, height = 1200) {
            var options by mutableStateOf(ImportOptions(includeDrafts = true, includeMedia = false))
            val updates = mutableListOf<ImportOptions>()
            setContent {
                LogDateTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        RestorePreviewContent(
                            preview = preview(2, 3),
                            fileName = "journal.zip",
                            options = options,
                            onOptionsChanged = {
                                options = it
                                updates += it
                            },
                            onConfirmImport = {},
                            onDismiss = {},
                        )
                    }
                }
            }
            onAllNodes(isToggleable()).assertCountEquals(2)
            // Touch the leading label area, away from the switch.
            onNode(hasText("Drafts") and isToggleable()).performTouchInput { click(Offset(24f, center.y)) }
            runOnIdle { assertEquals(listOf(ImportOptions(false, false)), updates) }
            // The visual switch shares its row action instead of firing a second callback.
            onNode(hasText("Media") and isToggleable()).performTouchInput { click(Offset(width - 40f, center.y)) }
            runOnIdle { assertEquals(listOf(ImportOptions(false, false), ImportOptions(false, true)), updates) }
        }

    @Test
    fun onlyCategoriesPresentInTheArchiveHaveImportOptions() {
        listOf(0 to 0, 2 to 0, 0 to 3, 2 to 3).forEach { (drafts, media) ->
            runDesktopComposeUiTest(width = 411, height = 1200) {
                setContent {
                    LogDateTheme {
                        RestorePreviewContent(preview(drafts, media), "journal.zip", ImportOptions(), {}, {}, {})
                    }
                }
                onAllNodes(hasText("Drafts") and isToggleable()).assertCountEquals(if (drafts > 0) 1 else 0)
                onAllNodes(hasText("Media") and isToggleable()).assertCountEquals(if (media > 0) 1 else 0)
                onAllNodesWithText("Import options").assertCountEquals(if (drafts + media > 0) 1 else 0)
            }
        }
    }

    @Test
    fun importCategoryCountsUseSingularAndPluralDescriptions() =
        runDesktopComposeUiTest(width = 411, height = 1200) {
            setContent {
                LogDateTheme {
                    RestorePreviewContent(preview(1, 2), "journal.zip", ImportOptions(), {}, {}, {})
                }
            }
            onNode(hasText("Drafts") and hasText("1 item") and isToggleable()).assertExists()
            onNode(hasText("Media") and hasText("2 items") and isToggleable()).assertExists()
            onAllNodesWithText("1 items").assertCountEquals(0)
        }

    private fun preview(
        drafts: Int,
        media: Int,
    ) = ArchivePreview(
        version = ExportSchemaVersion.CURRENT,
        exportDate = Instant.parse("2026-10-15T12:00:00Z"),
        appVersion = "1.0",
        stats = ExportStats(journalCount = 1, noteCount = 4, draftCount = drafts, mediaCount = media),
    )
}
