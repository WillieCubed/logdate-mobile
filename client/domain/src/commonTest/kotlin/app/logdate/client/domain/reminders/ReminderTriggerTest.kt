package app.logdate.client.domain.reminders

import app.logdate.client.domain.reminders.ReminderTrigger.ARRIVED_SOMEWHERE_NEW
import app.logdate.client.domain.reminders.ReminderTrigger.CAMPFIRE_EMBERS
import app.logdate.client.domain.reminders.ReminderTrigger.WIND_DOWN
import app.logdate.client.domain.reminders.ReminderTrigger.WORKOUT_ENDED
import app.logdate.client.domain.streak.FirePhase
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Tests what a reminder rings for, inside waking hours on a day without an earlier reminder: a workout
 * that just ended, arriving somewhere new, a campfire about to go out, or the evening wind-down,
 * preferred in that order.
 */
class ReminderTriggerTest {
    private val noon = at(12, 0)
    private val nothing = skip(SkipReason.NOTHING_TO_REMIND)
    private val nothingWritten = quiet.copy(writtenToday = false)
    private val embers = nothingWritten.copy(campfire = FirePhase.EMBERS)

    private fun deliver(
        trigger: ReminderTrigger,
        openMarks: Int = 0,
    ) = ReminderDecision.Deliver(trigger, openMarks)

    private fun case(
        name: String,
        now: Instant,
        signals: ReminderSignals,
        expected: ReminderDecision,
        wakingHours: WakingHours = WakingHours.Fallback,
        dayStartHour: Int = 4,
    ) = ReminderCase(name, clock(now, wakingHours, dayStartHour), signals, expected)

    private val momentTable =
        listOf(
            case("a workout that ended 30 minutes ago", noon, quiet.copy(workoutEndedAt = noon - 30.minutes), deliver(WORKOUT_ENDED)),
            case("a workout that ended 31 minutes ago", noon, quiet.copy(workoutEndedAt = noon - 31.minutes), nothing),
            case("a workout that has not ended yet", noon, quiet.copy(workoutEndedAt = noon + 5.minutes), nothing),
            case("nine minutes at a new place", noon, quiet.copy(newPlaceArrivedAt = noon - 9.minutes), nothing),
            case("ten minutes at a new place", noon, quiet.copy(newPlaceArrivedAt = noon - 10.minutes), deliver(ARRIVED_SOMEWHERE_NEW)),
            case("an arrival time in the future", noon, quiet.copy(newPlaceArrivedAt = noon + 1.minutes), nothing),
        )

    private val eveningTable =
        listOf(
            case("embers with three hours to bedtime", at(19, 30), embers, deliver(CAMPFIRE_EMBERS)),
            case("embers with three hours and a minute to bedtime", at(19, 29), embers, nothing),
            case("embers after something was written", at(21, 15), quiet.copy(campfire = FirePhase.EMBERS), nothing),
            case("embers end with the streak day before bedtime", at(18, 0), embers, deliver(CAMPFIRE_EMBERS), dayStartHour = 21),
            case("embers with a bedtime after midnight", at(22, 30), embers, deliver(CAMPFIRE_EMBERS), hours(10, 0, 1, 30)),
            case("a fire that went out has no campfire reminder", at(20, 0), nothingWritten.copy(campfire = FirePhase.OUT), nothing),
            case("wind down starts 90 minutes before bedtime", at(21, 0), nothingWritten, deliver(WIND_DOWN)),
            case("a minute before the wind down", at(20, 59), nothingWritten, nothing),
            case("streak tracking off still winds down", at(21, 15), nothingWritten.copy(campfire = null), deliver(WIND_DOWN)),
            case("nothing to wind down after writing", at(21, 30), quiet, nothing),
            case("open marks wind down even after writing", at(21, 30), quiet.copy(openMarks = 2), deliver(WIND_DOWN, openMarks = 2)),
            case("a later bedtime moves the wind down", at(21, 45), nothingWritten, nothing, hours(6, 0, 23, 30)),
            case("the wind down before a later bedtime", at(22, 0), nothingWritten, deliver(WIND_DOWN), hours(6, 0, 23, 30)),
        )

    private val everything = embers.copy(openMarks = 1, workoutEndedAt = at(21, 25), newPlaceArrivedAt = at(21, 15))
    private val noWorkout = everything.copy(workoutEndedAt = null)
    private val onlyEvening = noWorkout.copy(newPlaceArrivedAt = null)
    private val onlyWindDown = onlyEvening.copy(campfire = FirePhase.BURNING)

    private val priorityTable =
        listOf(
            case("a workout comes first", at(21, 30), everything, deliver(WORKOUT_ENDED, openMarks = 1)),
            case("an arrival comes before the campfire", at(21, 30), noWorkout, deliver(ARRIVED_SOMEWHERE_NEW, openMarks = 1)),
            case("the campfire comes before the wind down", at(21, 30), onlyEvening, deliver(CAMPFIRE_EMBERS, openMarks = 1)),
            case("the wind down when nothing else applies", at(21, 30), onlyWindDown, deliver(WIND_DOWN, openMarks = 1)),
        )

    @Test
    fun `a moment rings within its own time limits`() {
        assertTable(momentTable)
    }

    @Test
    fun `the evening rings for a fading campfire or the wind down`() {
        assertTable(eveningTable)
    }

    @Test
    fun `one trigger wins in a fixed order and the reminder says how many marks wait`() {
        assertTable(priorityTable)
    }
}
