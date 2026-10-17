package app.logdate.wear.reminders

import androidx.annotation.StringRes
import app.logdate.client.domain.reminders.ReminderTrigger
import app.logdate.wear.R

/** The reminders the watch can post, and what each says. */
enum class WatchReminder(
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
) {
    DRAFT_RESCUE(R.string.wear_reminder_draft_title, R.string.wear_reminder_draft_body),
    MEMORY_RECALL(R.string.wear_reminder_memory_title, R.string.wear_reminder_memory_body),
    EVENING(R.string.wear_reminder_evening_title, R.string.wear_reminder_evening_body),
    ARRIVED_SOMEWHERE_NEW(R.string.wear_reminder_arrival_title, R.string.wear_reminder_arrival_body),
    CAMPFIRE_EMBERS(R.string.wear_reminder_campfire_title, R.string.wear_reminder_campfire_body),
    WIND_DOWN(R.string.wear_reminder_wind_down_title, R.string.wear_reminder_evening_body),
    WORKOUT_ENDED(R.string.wear_reminder_workout_title, R.string.wear_reminder_workout_body),
    MOVING_ENDED(R.string.wear_reminder_moving_title, R.string.wear_reminder_moving_body),
    TRIP_ENDED(R.string.wear_reminder_trip_title, R.string.wear_reminder_trip_body),
}

fun reminderForTrigger(trigger: ReminderTrigger): WatchReminder =
    when (trigger) {
        ReminderTrigger.MOVING_ENDED -> WatchReminder.MOVING_ENDED
        ReminderTrigger.TRIP_ENDED -> WatchReminder.TRIP_ENDED
        ReminderTrigger.WORKOUT_ENDED -> WatchReminder.WORKOUT_ENDED
        ReminderTrigger.ARRIVED_SOMEWHERE_NEW -> WatchReminder.ARRIVED_SOMEWHERE_NEW
        ReminderTrigger.CAMPFIRE_EMBERS -> WatchReminder.CAMPFIRE_EMBERS
        ReminderTrigger.WIND_DOWN -> WatchReminder.WIND_DOWN
        ReminderTrigger.DRAFT_RESCUE -> WatchReminder.DRAFT_RESCUE
        ReminderTrigger.MEMORY_RECALL -> WatchReminder.MEMORY_RECALL
    }
