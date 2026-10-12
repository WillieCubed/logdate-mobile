package app.logdate.feature.onboarding.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReminderOnboardingContentTest {
    @Test fun grantedPermissionDoesNotReplaceExplicitReminderConsent() =
        runComposeUiTest {
            var selected = 0
            setContent {
                OnboardingNotificationsContent(
                    onBack = {},
                    onPrimaryAction = { selected++ },
                    onSkip = {},
                    recommendationsEnabled = true,
                    hasDecision = true,
                    remindersEnabled = false,
                )
            }
            onNodeWithText("Turn on reminders").assertIsEnabled()
            onNodeWithText("At most one reminder a day, on your phone or watch. You can change this anytime in Settings.").assertExists()
            assertEquals(0, selected)
            onNodeWithText("Turn on reminders").performClick()
            assertEquals(1, selected)
        }

    @Test fun savedReminderChoiceCanContinueAfterPermissionDecision() =
        runComposeUiTest {
            setContent {
                OnboardingNotificationsContent(
                    onBack = {},
                    onPrimaryAction = {},
                    onSkip = {},
                    recommendationsEnabled = false,
                    hasDecision = true,
                    remindersEnabled = true,
                )
            }
            onNodeWithText("Reminders").assertExists()
            onNodeWithText("Continue").assertIsEnabled()
            onNodeWithText("Not now").assertIsEnabled()
        }

    @Test fun savingAndErrorsUseTheExistingOnboardingControls() =
        runComposeUiTest {
            setContent {
                OnboardingNotificationsContent(
                    onBack = {},
                    onPrimaryAction = {},
                    onSkip = {},
                    recommendationsEnabled = true,
                    hasDecision = false,
                    remindersEnabled = false,
                    isSaving = true,
                    errorMessage = "Try saving again",
                )
            }
            onNodeWithTag(ONBOARDING_NOTIFICATIONS_PRIMARY_TAG).assertIsNotEnabled()
            onNodeWithTag(ONBOARDING_NOTIFICATIONS_SKIP_TAG).assertIsNotEnabled()
            onNodeWithText("Try saving again").assertExists()
        }

    @Test fun flagOffKeepsLegacyNotificationPresentation() =
        runComposeUiTest {
            setContent {
                OnboardingNotificationsContent(
                    onBack = {},
                    onPrimaryAction = {},
                    onSkip = {},
                    recommendationsEnabled = true,
                    hasDecision = false,
                )
            }
            onNodeWithText("Journal notifications").assertExists()
            onNodeWithText("Enable").assertIsEnabled()
            onNodeWithText("Turn on reminders").assertDoesNotExist()
        }
}
