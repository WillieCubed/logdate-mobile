package app.logdate.wear.v2.today

import app.logdate.client.domain.activity.ActivityUiState
import app.logdate.client.domain.today.TodaySummary
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.wear.capture.Feeling
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** What the Today page shows. */
sealed interface TodayUiState {
    data object Loading : TodayUiState

    /**
     * @property campfire The journaling fire, or null while campfire streaks are off.
     * @property feeling The newest mood check-in today, or null.
     * @property activity The suggestion after an activity and the morning start, while activity awareness is on.
     */
    data class Ready(
        val date: LocalDate,
        val timeZone: TimeZone,
        val summary: TodaySummary,
        val feeling: Feeling?,
        val campfire: CampfirePresentation?,
        val phoneCard: PhoneCard,
        val activity: ActivityUiState = ActivityUiState(),
    ) : TodayUiState

    /** Today's notes could not be read. */
    data object Failed : TodayUiState
}

/** The card Today shows about the phone. */
sealed interface PhoneCard {
    data object None : PhoneCard

    /** The paired Android phone lacks LogDate; the card opens its Play listing on [nodeId]. */
    data class GetApp(
        val nodeId: String,
    ) : PhoneCard

    /** LogDate is on the phone, but this watch still needs account setup. */
    data class SetUp(
        val nodeId: String,
    ) : PhoneCard
}

/** Where a request to open something on the phone stands. */
enum class HandoffState {
    IDLE,
    SENDING,
    SENT,
    FAILED,
}
