package app.logdate.feature.core.export

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import app.logdate.client.domain.export.ExportCounts
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ExportCategoryOptionsTest {
    @Test
    fun normalPhoneDateChoicesKeepTheirUnits() =
        runDesktopComposeUiTest(width = 411, height = 900) {
            setContent {
                LogDateTheme {
                    ExportConfigContent(ExportOptions(), null, {}, {}, {})
                }
            }
            onNodeWithText("Last 30 days").assertIsDisplayed()
            onNodeWithText("Last 90 days").assertIsDisplayed()
        }

    @Test
    fun exportCategoryCountsUseSingularPluralAndZeroDescriptions() =
        runDesktopComposeUiTest(width = 411, height = 1400) {
            setContent {
                LogDateTheme {
                    ExportConfigContent(ExportOptions(), ExportCounts(1, 0, 2, 3), {}, {}, {})
                }
            }
            onNode(hasText("Journals") and hasText("1 item") and isToggleable()).assertExists()
            onNode(hasText("Notes") and hasText("0 items") and isToggleable()).assertExists()
            onNode(hasText("Drafts") and hasText("2 items") and isToggleable()).assertExists()
            onNode(hasText("Media") and hasText("3 items") and isToggleable()).assertExists()
        }

    @Test
    fun everyCategoryLabelTogglesOnlyItsOwnOptionOnce() =
        runDesktopComposeUiTest(width = 411, height = 1400) {
            var options by mutableStateOf(ExportOptions())
            val updates = mutableListOf<ExportOptions>()
            setContent {
                LogDateTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        ExportConfigContent(
                            options = options,
                            counts = ExportCounts(1, 2, 3, 4),
                            onOptionsChanged = {
                                options = it
                                updates += it
                            },
                            onConfirm = {},
                            onDismiss = {},
                        )
                    }
                }
            }
            onAllNodes(isToggleable() and (hasText("Journals") or hasText("Notes") or hasText("Drafts") or hasText("Media")))
                .assertCountEquals(4)
            var expected = ExportOptions()
            listOf("Journals", "Notes", "Drafts", "Media").forEachIndexed { index, category ->
                onNode(hasText(category) and isToggleable()).performTouchInput { click(Offset(24f, center.y)) }
                expected =
                    when (category) {
                        "Journals" -> expected.copy(includeJournals = false)
                        "Notes" -> expected.copy(includeNotes = false)
                        "Drafts" -> expected.copy(includeDrafts = false)
                        else -> expected.copy(includeMedia = false)
                    }
                runOnIdle {
                    assertEquals(index + 1, updates.size)
                    assertEquals(expected, updates.last())
                }
            }
        }

    @Test
    fun selectingAnExportDestinationDisablesCategoryRowsAndTheirSwitches() =
        runDesktopComposeUiTest(width = 411, height = 1400) {
            var updates = 0
            setContent {
                LogDateTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        ExportConfigContent(ExportOptions(), null, { updates++ }, {}, {}, buttonsEnabled = false)
                    }
                }
            }
            onAllNodes(isToggleable() and (hasText("Journals") or hasText("Notes") or hasText("Drafts") or hasText("Media")))
                .assertCountEquals(4)
            listOf("Journals", "Notes", "Drafts", "Media").forEach { category ->
                val row = onNode(hasText(category) and isToggleable())
                row.assertIsNotEnabled().performClick()
                row.performTouchInput { click(Offset(width - 40f, center.y)) }
            }
            runOnIdle { assertEquals(0, updates) }
        }

    @Test
    fun largeTextDateChoicesRemainReadableAndSelectable() =
        runDesktopComposeUiTest(width = 411, height = 900) {
            var options by mutableStateOf(ExportOptions())
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    LogDateTheme {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            ExportConfigContent(
                                options = options,
                                counts = ExportCounts(1, 2, 3, 4),
                                onOptionsChanged = { options = it },
                                onConfirm = {},
                                onDismiss = {},
                            )
                        }
                    }
                }
            }
            onNodeWithText("Last year").performScrollTo().assertIsDisplayed().performClick()
            runOnIdle { assertEquals(ExportDateRange.LastYear, options.dateRange) }
            onNode(hasText("Media") and isToggleable()).performScrollTo().assertIsDisplayed()
            onNodeWithText("Cancel").performScrollTo().assertIsDisplayed()
            onNodeWithText("Export").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun exportConfirmationRemainsReachableInCompactLandscapeAtLargeText() =
        runDesktopComposeUiTest(width = 640, height = 320) {
            var confirmations = 0
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    LogDateTheme {
                        ExportBottomSheet(
                            exportState = ExportState.Configuring(counts = ExportCounts(1, 2, 3, 4)),
                            onOptionsChanged = {},
                            onConfirm = { confirmations++ },
                            onCancel = {},
                            onRetry = {},
                            onDismiss = {},
                        )
                    }
                }
            }
            onNodeWithText("Export").performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithText("Export", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            assertEquals(
                2f,
                layouts
                    .single()
                    .layoutInput.density.fontScale,
            )
            onNodeWithText("Export").performClick()
            runOnIdle { assertEquals(1, confirmations) }
        }
}
