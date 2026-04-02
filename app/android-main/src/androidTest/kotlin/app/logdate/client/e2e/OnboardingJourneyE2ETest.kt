package app.logdate.client.e2e

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.MainActivityUiRoot
import app.logdate.client.sharing.NoOpSharingLauncher
import app.logdate.feature.core.AppAuthState
import app.logdate.feature.core.GlobalAppUiLoadedState
import app.logdate.feature.onboarding.ui.CLOUD_ACCOUNT_SETUP_ROOT_TAG
import app.logdate.feature.onboarding.ui.CLOUD_ACCOUNT_SETUP_SKIP_ACTION_TAG
import app.logdate.feature.onboarding.ui.CLOUD_ACCOUNT_SETUP_SKIP_OPTION_TAG
import app.logdate.feature.onboarding.ui.MEMORIES_IMPORT_INFO_CONTINUE_TAG
import app.logdate.feature.onboarding.ui.MEMORIES_IMPORT_INFO_ROOT_TAG
import app.logdate.feature.onboarding.ui.MEMORY_SELECTION_CONTINUE_TAG
import app.logdate.feature.onboarding.ui.MEMORY_SELECTION_ROOT_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_BIRTHDAY_CONFIRM_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_BIRTHDAY_ROOT_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_BIRTHDAY_SET_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_COMPLETION_CONTINUE_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_COMPLETION_FINAL_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_COMPLETION_ROOT_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_DAY_BOUNDARIES_ROOT_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_LOCATION_ROOT_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_LOCATION_SKIP_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_NOTIFICATIONS_ROOT_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_NOTIFICATIONS_SKIP_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_OVERVIEW_CONTINUE_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_OVERVIEW_ROOT_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_RECOMMENDATIONS_KEEP_ON_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_RECOMMENDATIONS_ROOT_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_RECOMMENDATIONS_TURN_OFF_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_START_FROM_BACKUP_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_START_GET_STARTED_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_START_ROOT_TAG
import app.logdate.feature.onboarding.ui.PERSONAL_INTRO_BIO_CONTINUE_TAG
import app.logdate.feature.onboarding.ui.PERSONAL_INTRO_BIO_FIELD_TAG
import app.logdate.feature.onboarding.ui.PERSONAL_INTRO_NAME_CONTINUE_TAG
import app.logdate.feature.onboarding.ui.PERSONAL_INTRO_NAME_FIELD_TAG
import app.logdate.feature.onboarding.ui.PERSONAL_INTRO_ROOT_TAG
import app.logdate.feature.onboarding.ui.WELCOME_BACK_ROOT_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class OnboardingJourneyE2ETest {
    private val environment = OnboardingJourneyEnvironment()
    private val koinRule = OnboardingKoinModuleOverrideRule(environment.module)
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(koinRule).around(composeRule)

    @Test
    fun freshOnboardingJourney_reachesCompletionScreen_andCapturesScreens() {
        environment.seedFreshFlow(healthAvailable = false)
        launchHarness()

        advanceToStartActions()
        capture(ONBOARDING_START_ROOT_TAG, "fresh", "01_start.png")
        composeRule.onNodeWithTag(ONBOARDING_START_GET_STARTED_TAG).performClick()

        composeRule.onNodeWithTag(PERSONAL_INTRO_ROOT_TAG).assertIsDisplayed()
        capture(PERSONAL_INTRO_ROOT_TAG, "fresh", "02_personal_intro.png")
        composeRule.onNodeWithTag(PERSONAL_INTRO_NAME_FIELD_TAG).performTextInput("Willie")
        composeRule.onNodeWithTag(PERSONAL_INTRO_NAME_CONTINUE_TAG).performClick()
        composeRule.onNodeWithTag(PERSONAL_INTRO_BIO_FIELD_TAG).performTextInput("I like journaling and long walks.")
        composeRule.onNodeWithTag(PERSONAL_INTRO_BIO_CONTINUE_TAG).performClick()
        composeRule.mainClock.advanceTimeBy(2_200)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ONBOARDING_OVERVIEW_ROOT_TAG).assertIsDisplayed()
        capture(ONBOARDING_OVERVIEW_ROOT_TAG, "fresh", "03_overview.png")
        composeRule.onNodeWithTag(ONBOARDING_OVERVIEW_CONTINUE_TAG).performClick()

        composeRule.onNodeWithTag(MEMORIES_IMPORT_INFO_ROOT_TAG).assertIsDisplayed()
        capture(MEMORIES_IMPORT_INFO_ROOT_TAG, "fresh", "04_memory_import.png")
        composeRule.onNodeWithTag(MEMORIES_IMPORT_INFO_CONTINUE_TAG).performClick()

        composeRule.onNodeWithTag(MEMORY_SELECTION_ROOT_TAG).assertIsDisplayed()
        capture(MEMORY_SELECTION_ROOT_TAG, "fresh", "05_memory_selection.png")
        composeRule.onNodeWithTag(MEMORY_SELECTION_CONTINUE_TAG).performClick()

        composeRule.onNodeWithTag(CLOUD_ACCOUNT_SETUP_ROOT_TAG).assertIsDisplayed()
        capture(CLOUD_ACCOUNT_SETUP_ROOT_TAG, "fresh", "06_backup_sync.png")
        composeRule.onNodeWithTag(CLOUD_ACCOUNT_SETUP_SKIP_OPTION_TAG).performClick()
        composeRule.onNodeWithTag(CLOUD_ACCOUNT_SETUP_SKIP_ACTION_TAG).performClick()

        composeRule.onNodeWithTag(ONBOARDING_BIRTHDAY_ROOT_TAG).assertIsDisplayed()
        capture(ONBOARDING_BIRTHDAY_ROOT_TAG, "fresh", "07_birthday.png")
        selectBirthday()

        composeRule.onNodeWithTag(ONBOARDING_RECOMMENDATIONS_ROOT_TAG).assertIsDisplayed()
        capture(ONBOARDING_RECOMMENDATIONS_ROOT_TAG, "fresh", "08_recommendations.png")
        composeRule.onNodeWithTag(ONBOARDING_RECOMMENDATIONS_TURN_OFF_TAG).performClick()

        composeRule.onNodeWithTag(ONBOARDING_LOCATION_ROOT_TAG).assertIsDisplayed()
        capture(ONBOARDING_LOCATION_ROOT_TAG, "fresh", "09_location.png")
        composeRule.onNodeWithTag(ONBOARDING_LOCATION_SKIP_TAG).performClick()

        composeRule.onNodeWithTag(ONBOARDING_NOTIFICATIONS_ROOT_TAG).assertIsDisplayed()
        capture(ONBOARDING_NOTIFICATIONS_ROOT_TAG, "fresh", "10_notifications.png")
        composeRule.onNodeWithTag(ONBOARDING_NOTIFICATIONS_SKIP_TAG).performClick()

        composeRule.onNodeWithTag(ONBOARDING_COMPLETION_ROOT_TAG).assertIsDisplayed()
        capture(ONBOARDING_COMPLETION_ROOT_TAG, "fresh", "11_completion_streak.png")
        composeRule.onNodeWithTag(ONBOARDING_COMPLETION_CONTINUE_TAG).performClick()

        composeRule.onNodeWithTag(ONBOARDING_COMPLETION_FINAL_TAG).assertIsDisplayed()
        capture(ONBOARDING_COMPLETION_FINAL_TAG, "fresh", "12_completion_final.png")
    }

    @Test
    fun personalIntroState_andCurrentStep_surviveActivityRecreation() {
        environment.seedFreshFlow(healthAvailable = false)
        launchHarness()

        advanceToStartActions()
        composeRule.onNodeWithTag(ONBOARDING_START_GET_STARTED_TAG).performClick()

        composeRule.onNodeWithTag(PERSONAL_INTRO_ROOT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(PERSONAL_INTRO_NAME_FIELD_TAG).performTextInput("Willie")
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PERSONAL_INTRO_ROOT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(PERSONAL_INTRO_NAME_FIELD_TAG).assertTextContains("Willie")

        composeRule.onNodeWithTag(PERSONAL_INTRO_NAME_CONTINUE_TAG).performClick()
        composeRule.onNodeWithTag(PERSONAL_INTRO_BIO_FIELD_TAG).performTextInput("I write every day.")
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PERSONAL_INTRO_ROOT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(PERSONAL_INTRO_BIO_FIELD_TAG).assertTextContains("I write every day.")
        capture(PERSONAL_INTRO_ROOT_TAG, "rotation", "01_personal_intro_bio_landscape.png")
    }

    @Test
    fun birthdayStep_survivesActivityRecreation_andContinuesForward() {
        environment.seedFreshFlow(healthAvailable = false)
        launchHarness()

        advanceToStartActions()
        composeRule.onNodeWithTag(ONBOARDING_START_GET_STARTED_TAG).performClick()
        composeRule.onNodeWithTag(PERSONAL_INTRO_NAME_FIELD_TAG).performTextInput("Willie")
        composeRule.onNodeWithTag(PERSONAL_INTRO_NAME_CONTINUE_TAG).performClick()
        composeRule.onNodeWithTag(PERSONAL_INTRO_BIO_FIELD_TAG).performTextInput("I want onboarding to survive rotation.")
        composeRule.onNodeWithTag(PERSONAL_INTRO_BIO_CONTINUE_TAG).performClick()
        composeRule.mainClock.advanceTimeBy(2_200)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ONBOARDING_OVERVIEW_CONTINUE_TAG).performClick()
        composeRule.onNodeWithTag(MEMORIES_IMPORT_INFO_CONTINUE_TAG).performClick()
        composeRule.onNodeWithTag(MEMORY_SELECTION_CONTINUE_TAG).performClick()
        composeRule.onNodeWithTag(CLOUD_ACCOUNT_SETUP_SKIP_OPTION_TAG).performClick()
        composeRule.onNodeWithTag(CLOUD_ACCOUNT_SETUP_SKIP_ACTION_TAG).performClick()

        composeRule.onNodeWithTag(ONBOARDING_BIRTHDAY_ROOT_TAG).assertIsDisplayed()
        capture(ONBOARDING_BIRTHDAY_ROOT_TAG, "rotation", "02_birthday_before_recreate.png")
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ONBOARDING_BIRTHDAY_ROOT_TAG).assertIsDisplayed()
        capture(ONBOARDING_BIRTHDAY_ROOT_TAG, "rotation", "03_birthday_after_recreate.png")
        selectBirthday()

        composeRule.onNodeWithTag(ONBOARDING_RECOMMENDATIONS_ROOT_TAG).assertIsDisplayed()
        capture(ONBOARDING_RECOMMENDATIONS_ROOT_TAG, "rotation", "04_recommendations_after_birthday.png")
    }

    @Test
    fun continueSetupFlow_startsAtAccount_and_reachesWelcomeBack() {
        environment.seedFreshFlow(healthAvailable = false)
        launchHarness()

        advanceToStartActions()
        composeRule.onNodeWithTag(ONBOARDING_START_FROM_BACKUP_TAG).performClick()

        composeRule.onNodeWithTag(CLOUD_ACCOUNT_SETUP_ROOT_TAG).assertIsDisplayed()
        capture(CLOUD_ACCOUNT_SETUP_ROOT_TAG, "continue_setup", "01_account.png")
        composeRule.onNodeWithTag(CLOUD_ACCOUNT_SETUP_SKIP_OPTION_TAG).performClick()
        composeRule.onNodeWithTag(CLOUD_ACCOUNT_SETUP_SKIP_ACTION_TAG).performClick()

        composeRule.onNodeWithTag(PERSONAL_INTRO_ROOT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(PERSONAL_INTRO_NAME_FIELD_TAG).performTextInput("Willie")
        composeRule.onNodeWithTag(PERSONAL_INTRO_NAME_CONTINUE_TAG).performClick()
        composeRule.onNodeWithTag(PERSONAL_INTRO_BIO_FIELD_TAG).performTextInput("I restore my apps carefully.")
        composeRule.onNodeWithTag(PERSONAL_INTRO_BIO_CONTINUE_TAG).performClick()
        composeRule.mainClock.advanceTimeBy(2_200)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ONBOARDING_BIRTHDAY_ROOT_TAG).assertIsDisplayed()
        capture(ONBOARDING_BIRTHDAY_ROOT_TAG, "continue_setup", "02_birthday.png")
        selectBirthday()

        composeRule.onNodeWithTag(ONBOARDING_NOTIFICATIONS_ROOT_TAG).assertIsDisplayed()
        capture(ONBOARDING_NOTIFICATIONS_ROOT_TAG, "continue_setup", "03_notifications.png")
        composeRule.onNodeWithTag(ONBOARDING_NOTIFICATIONS_SKIP_TAG).performClick()

        composeRule.onNodeWithTag(WELCOME_BACK_ROOT_TAG).assertIsDisplayed()
        capture(WELCOME_BACK_ROOT_TAG, "continue_setup", "04_welcome_back.png")
    }

    @Test
    fun freshFlow_showsDayBoundariesWhenHealthConnectIsAvailable() {
        environment.seedFreshFlow(
            hasIntro = true,
            hasBirthday = true,
            hasCloudAccount = true,
            healthAvailable = true,
            healthPermissionsGranted = false,
        )
        launchHarness()

        advanceToStartActions()
        composeRule.onNodeWithTag(ONBOARDING_START_GET_STARTED_TAG).performClick()

        composeRule.onNodeWithTag(ONBOARDING_OVERVIEW_ROOT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_OVERVIEW_CONTINUE_TAG).performClick()
        composeRule.onNodeWithTag(MEMORIES_IMPORT_INFO_CONTINUE_TAG).performClick()
        composeRule.onNodeWithTag(MEMORY_SELECTION_CONTINUE_TAG).performClick()

        composeRule.onNodeWithTag(ONBOARDING_RECOMMENDATIONS_ROOT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_RECOMMENDATIONS_KEEP_ON_TAG).performClick()

        composeRule.onNodeWithTag(ONBOARDING_DAY_BOUNDARIES_ROOT_TAG).assertIsDisplayed()
        capture(ONBOARDING_DAY_BOUNDARIES_ROOT_TAG, "health_connect", "01_day_boundaries.png")
    }

    private fun launchHarness() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MainActivityUiRoot(
                appUiState =
                    GlobalAppUiLoadedState(
                        isLoaded = true,
                        isOnline = true,
                        authState = AppAuthState.NO_PROMPT_NEEDED,
                        isOnboarded = false,
                        displayName = "",
                    ),
                onShowUnlockPrompt = {},
                sharingLauncher = NoOpSharingLauncher,
            )
        }
        composeRule.waitForIdle()
    }

    private fun advanceToStartActions() {
        composeRule.onNodeWithTag(ONBOARDING_START_ROOT_TAG).assertExists()
        composeRule.mainClock.advanceTimeBy(3_200)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ONBOARDING_START_GET_STARTED_TAG).assertExists()
    }

    private fun selectBirthday() {
        composeRule.onNodeWithTag(ONBOARDING_BIRTHDAY_SET_TAG).performClick()
        composeRule.waitForIdle()
        val dayOfMonth = LocalDate.now().dayOfMonth.toString()
        composeRule.onAllNodesWithText(dayOfMonth, useUnmergedTree = true)[0].performClick()
        composeRule.onNodeWithTag(ONBOARDING_BIRTHDAY_CONFIRM_TAG).performClick()
        composeRule.waitForIdle()
    }

    private fun capture(
        rootTag: String,
        testName: String,
        fileName: String,
    ) {
        composeRule.onNodeWithTag(rootTag).captureStepScreenshot(composeRule, testName, fileName)
    }
}
