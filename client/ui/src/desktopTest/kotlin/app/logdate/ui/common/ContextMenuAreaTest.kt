package app.logdate.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ContextMenuAreaTest {
    private var clicks = 0

    @Test fun longPressOverClickableContentOpensTheMenuWithoutClicking() =
        runDesktopComposeUiTest {
            setCard(items = listOf(ContextMenuItem("Open in new window") {}))

            onNodeWithTag(CARD).performTouchInput { longClick() }

            onNodeWithText("Open in new window").assertExists()
            assertEquals(0, clicks)
        }

    @Test fun tapStillClicksAndLeavesTheMenuClosed() =
        runDesktopComposeUiTest {
            setCard(items = listOf(ContextMenuItem("Open in new window") {}))

            onNodeWithTag(CARD).performTouchInput { click() }

            onNodeWithText("Open in new window").assertDoesNotExist()
            assertEquals(1, clicks)
        }

    @Test fun rightClickOpensTheMenu() =
        runDesktopComposeUiTest {
            setCard(items = listOf(ContextMenuItem("Open in new window") {}))

            onNodeWithTag(CARD).performMouseInput { rightClick() }

            onNodeWithText("Open in new window").assertExists()
            assertEquals(0, clicks)
        }

    @Test fun withoutItemsAPressIsLeftToTheContent() =
        runDesktopComposeUiTest {
            setCard(items = emptyList())

            onNodeWithTag(CARD).performTouchInput { longClick() }

            assertEquals(1, clicks)
        }

    @Test fun longPressCanBeLeftToTheContent() =
        runDesktopComposeUiTest {
            setCard(items = listOf(ContextMenuItem("Open in new window") {}), openOnLongPress = false)

            onNodeWithTag(CARD).performTouchInput { longClick() }

            onNodeWithText("Open in new window").assertDoesNotExist()
            assertEquals(1, clicks)
        }

    private fun ComposeUiTest.setCard(
        items: List<ContextMenuItem>,
        openOnLongPress: Boolean = true,
    ) {
        setContent {
            LogDateTheme {
                ContextMenuArea(items = items, openOnLongPress = openOnLongPress) {
                    Box(Modifier.size(200.dp).testTag(CARD).clickable { clicks++ })
                }
            }
        }
    }

    private companion object {
        const val CARD = "card"
    }
}
