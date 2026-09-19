package app.logdate.feature.onboarding.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * A birthday is required to finish onboarding, so the birthday step must not offer a way past it
 * without one. Skipping it sent the completion screen straight back here, looping forever.
 */
@OptIn(ExperimentalTestApi::class)
class OnboardingBirthdayScreenTest {
    @Test
    fun `the birthday step cannot be skipped`() =
        runComposeUiTest {
            setContent {
                OnboardingBirthdayScreen(
                    onBack = {},
                    onNext = {},
                    persistBirthday = { Result.success(Unit) },
                )
            }

            onNodeWithText("Set my birthday").assertExists()
            onNodeWithText("Skip for now").assertDoesNotExist()
        }
}
