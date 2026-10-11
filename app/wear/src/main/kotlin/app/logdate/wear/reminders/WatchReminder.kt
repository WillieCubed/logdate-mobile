package app.logdate.wear.reminders

import androidx.annotation.StringRes
import app.logdate.wear.R

/** The reminders the watch can post, and what each says. */
enum class WatchReminder(
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
) {
    WORKOUT_ENDED(R.string.wear_reminder_workout_title, R.string.wear_reminder_workout_body),
}
