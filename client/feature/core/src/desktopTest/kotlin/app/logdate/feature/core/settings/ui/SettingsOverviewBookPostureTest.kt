@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import app.logdate.ui.foldable.provideFoldableLayoutInfo
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test

private const val WINDOW_WIDTH = 1440
private const val WINDOW_HEIGHT = 500

private val bookPosture =
    FoldableLayoutInfo(
        isFoldable = true,
        posture = FoldablePosture.Book,
        hinge =
            FoldableHingeInfo(
                orientation = FoldableHingeOrientation.Vertical,
                state = FoldableHingeState.HalfOpened,
                occlusionType = FoldableOcclusionType.Full,
                bounds =
                    FoldableHingeBounds(
                        left = 708.dp,
                        top = 0.dp,
                        right = 732.dp,
                        bottom = WINDOW_HEIGHT.dp,
                        width = 24.dp,
                        height = WINDOW_HEIGHT.dp,
                    ),
                isSeparating = true,
            ),
    )

/**
 * In book posture each pane of the overview is a column hosted in a box as tall as the window, so
 * rows below the window's height have to scroll into view or they cannot be reached at all.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsOverviewBookPostureTest {
    @Test
    fun `compact overview places device cards before personal settings without a duplicate row`() =
        runDesktopComposeUiTest(width = 411, height = 1600) {
            setContent { SettingsOverviewInBookPosture(book = false) }

            val card = onNodeWithText("Test watch device").fetchSemanticsNode().boundsInRoot
            val library = onNodeWithText("Your library").fetchSemanticsNode().boundsInRoot
            kotlin.test.assertTrue(card.top < library.top)
            onNodeWithText("Watch").assertDoesNotExist()
        }

    @Test
    fun `device card replaces the watch row`() =
        runDesktopComposeUiTest(width = WINDOW_WIDTH, height = WINDOW_HEIGHT) {
            setContent { SettingsOverviewInBookPosture() }

            onNodeWithText("Test watch device").assertExists()
            onNodeWithText("Watch").assertDoesNotExist()
        }

    @Test
    fun `without device cards the existing watch row remains`() =
        runDesktopComposeUiTest(width = WINDOW_WIDTH, height = WINDOW_HEIGHT) {
            setContent { SettingsOverviewInBookPosture(withCards = false) }

            onNodeWithText("Watch").performScrollTo().assertIsDisplayed()
            onNodeWithText("Test watch device").assertDoesNotExist()
        }

    @Test
    fun `device cards are reachable in the identity pane`() =
        runDesktopComposeUiTest(width = WINDOW_WIDTH, height = WINDOW_HEIGHT) {
            setContent { SettingsOverviewInBookPosture() }

            onNodeWithText("Test watch device").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun `the last row of the start pane scrolls into view`() =
        runDesktopComposeUiTest(width = WINDOW_WIDTH, height = WINDOW_HEIGHT) {
            setContent { SettingsOverviewInBookPosture() }

            onNodeWithText("Your library").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun `the last row of the end pane scrolls into view`() =
        runDesktopComposeUiTest(width = WINDOW_WIDTH, height = WINDOW_HEIGHT) {
            setContent { SettingsOverviewInBookPosture() }

            onNodeWithText("About LogDate").performScrollTo().assertIsDisplayed()
        }
}

@Composable
private fun SettingsOverviewInBookPosture(
    withCards: Boolean = true,
    book: Boolean = true,
) {
    provideFoldableLayoutInfo(if (book) bookPosture else FoldableLayoutInfo()) {
        LogDateTheme(dynamicColor = false, darkTheme = false) {
            SettingsOverviewContent(
                onBack = {},
                onNavigateToProfile = {},
                onNavigateToAccount = {},
                onNavigateToDevices = {},
                onNavigateToWatch = {},
                onNavigateToReset = {},
                onNavigateToLocation = {},
                onNavigateToPrivacy = {},
                onNavigateToMemories = {},
                onNavigateToSync = {},
                onNavigateToExport = {},
                userProfile = UserProfile(name = "Alex Rivera", username = "alex", isAuthenticated = true),
                deviceCards =
                    if (withCards) {
                        { Text("Test watch device") }
                    } else {
                        null
                    },
            )
        }
    }
}
