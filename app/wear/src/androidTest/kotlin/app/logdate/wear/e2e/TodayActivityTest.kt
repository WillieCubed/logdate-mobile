package app.logdate.wear.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.AppScaffold
import app.logdate.client.domain.activity.Activity
import app.logdate.client.domain.activity.ActivityKind
import app.logdate.client.domain.activity.ActivitySuggestion
import app.logdate.client.domain.activity.ActivityUiState
import app.logdate.client.domain.today.TodaySummary
import app.logdate.wear.v2.theme.LogDateWearTheme
import app.logdate.wear.v2.today.HandoffState
import app.logdate.wear.v2.today.PhoneCard
import app.logdate.wear.v2.today.TodayPage
import app.logdate.wear.v2.today.TodayTags
import app.logdate.wear.v2.today.TodayUiState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Today on a Wear OS emulator: the offer to record after an activity, and the morning start. */
@RunWith(AndroidJUnit4::class)
class TodayActivityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val started = Instant.parse("2026-10-10T08:00:00Z")

    @Test
    fun a_walk_that_ended_offers_to_record_and_can_be_put_off() {
        val walk = Activity(ActivityKind.MOVING, started, started + 40.minutes)
        val used = mutableListOf<String>()
        show(ActivityUiState(suggestion = ActivitySuggestion(walk, minutes = 40)), onUse = { used += "use" }, onDismiss = {
            used +=
                "dismiss"
        })

        scrollTo(TodayTags.ACTIVITY_SUGGESTION)
        composeRule.onNodeWithText("Record a moment").assertIsDisplayed()
        composeRule.onNodeWithText("From the last 40 minutes").assertIsDisplayed()
        composeRule.onNodeWithTag(TodayTags.ACTIVITY_SUGGESTION).performClick()
        scrollTo(TodayTags.ACTIVITY_DISMISS)
        composeRule.onNodeWithText("Not now").performClick()

        assertEquals(listOf("use", "dismiss"), used)
    }

    @Test
    fun a_trip_names_its_length() {
        val trip = Activity(ActivityKind.IN_VEHICLE, started, started + 25.minutes)
        show(ActivityUiState(suggestion = ActivitySuggestion(trip, minutes = 25)))

        scrollTo(TodayTags.ACTIVITY_SUGGESTION)
        composeRule.onNodeWithText("From your 25-minute trip").assertIsDisplayed()
    }

    @Test
    fun waking_up_shows_the_morning_start() {
        val night = Activity(ActivityKind.SLEEP, started - 480.minutes, started)
        show(ActivityUiState(morningStart = night))

        scrollTo(TodayTags.MORNING_START)
        composeRule.onNodeWithText("Good morning").assertIsDisplayed()
        composeRule.onNodeWithText("Start the day with a voice note").assertIsDisplayed()
    }

    @Test
    fun nothing_is_offered_without_an_activity() {
        show(ActivityUiState())

        composeRule.onNodeWithTag(TodayTags.SUMMARY).assertIsDisplayed()
        composeRule.onNodeWithTag(TodayTags.ACTIVITY_SUGGESTION).assertDoesNotExist()
        composeRule.onNodeWithTag(TodayTags.MORNING_START).assertDoesNotExist()
    }

    private fun scrollTo(tag: String) {
        composeRule.onNodeWithTag(TodayTags.PAGE).performScrollToNode(hasTestTag(tag))
    }

    private fun show(
        activity: ActivityUiState,
        onUse: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        composeRule.setContent {
            LogDateWearTheme(dynamicColor = false) {
                AppScaffold {
                    TodayPage(
                        state =
                            TodayUiState.Ready(
                                date = LocalDate(2026, 10, 10),
                                timeZone = TimeZone.UTC,
                                summary = TodaySummary.Empty,
                                feeling = null,
                                campfire = null,
                                phoneCard = PhoneCard.None,
                                activity = activity,
                            ),
                        handoff = HandoffState.IDLE,
                        onGetPhoneApp = {},
                        onSetUpPhone = {},
                        onHandoffDismissed = {},
                        onUseActivity = onUse,
                        onDismissActivity = onDismiss,
                    )
                }
            }
        }
    }
}
