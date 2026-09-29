package app.logdate.feature.library.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LibraryTopBarTest {
    @Test
    fun `search action opens the actual search screen`() =
        runComposeUiTest {
            var opened = false
            setContent {
                LogDateTheme {
                    LibraryTopBar(onOpenSearch = { opened = true })
                }
            }

            onNodeWithTag("library_search_action").performClick()

            assertTrue(opened)
        }
}
