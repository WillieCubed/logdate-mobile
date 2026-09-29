package app.logdate.feature.onboarding.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CloudAccountSetupContentTest {
    @Test
    fun `free launch does not offer an unverified paid plan`() =
        runComposeUiTest {
            setContent {
                LogDateTheme {
                    CloudAccountSetupContent(onBack = {}, onContinue = {}, onSkip = {})
                }
            }

            assertTrue(onAllNodesWithText("$5/month").fetchSemanticsNodes().isEmpty())
        }
}
