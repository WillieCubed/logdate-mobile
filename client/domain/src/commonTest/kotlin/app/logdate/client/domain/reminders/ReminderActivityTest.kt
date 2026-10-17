package app.logdate.client.domain.reminders

import app.logdate.client.domain.activity.Activity
import app.logdate.client.domain.activity.ActivityKind
import app.logdate.client.domain.activity.AgreedActivity
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** How what the person is doing changes [ReminderOrchestrator]'s decision. */
class ReminderActivityTest {
    private val evening = at(21, 30)

    /** Nothing written today, late enough for the wind-down: a reminder is due unless an activity says otherwise. */
    private val windingDown = quiet.copy(writtenToday = false)

    @Test
    fun `reminders wait while the person is moving or in a vehicle`() =
        assertTable(
            listOf(
                ReminderCase(
                    name = "in a vehicle",
                    clock = clock(evening),
                    signals =
                        windingDown.copy(
                            activity = AgreedActivity(current = Activity(ActivityKind.IN_VEHICLE, evening - 20.minutes, null)),
                        ),
                    expected = skip(SkipReason.ACTIVITY_UNDER_WAY),
                ),
                ReminderCase(
                    name = "on a walk",
                    clock = clock(evening),
                    signals =
                        windingDown.copy(
                            activity = AgreedActivity(current = Activity(ActivityKind.MOVING, evening - 5.minutes, null)),
                        ),
                    expected = skip(SkipReason.ACTIVITY_UNDER_WAY),
                ),
                ReminderCase(
                    name = "asleep does not hold the wind-down",
                    clock = clock(evening),
                    signals =
                        windingDown.copy(
                            activity = AgreedActivity(current = Activity(ActivityKind.SLEEP, evening - 5.minutes, null)),
                        ),
                    expected = ReminderDecision.Deliver(ReminderTrigger.WIND_DOWN, openMarks = 0),
                ),
                ReminderCase(
                    name = "a walk does not hide that today's reminder already went out",
                    clock = clock(evening, lastReminderAt = at(12, 0)),
                    signals =
                        windingDown.copy(
                            activity = AgreedActivity(current = Activity(ActivityKind.MOVING, evening - 5.minutes, null)),
                        ),
                    expected = skip(SkipReason.ALREADY_REMINDED_TODAY),
                ),
            ),
        )

    @Test
    fun `the end of a walk or trip can be the day's reminder`() {
        val now = at(15, 0)
        assertTable(
            listOf(
                ReminderCase(
                    name = "a walk that just ended",
                    clock = clock(now),
                    signals =
                        quiet.copy(
                            activity =
                                AgreedActivity(
                                    lastEnded =
                                        Activity(
                                            ActivityKind.MOVING,
                                            now - 50.minutes,
                                            now - 10.minutes,
                                        ),
                                ),
                        ),
                    expected = ReminderDecision.Deliver(ReminderTrigger.MOVING_ENDED, openMarks = 0),
                ),
                ReminderCase(
                    name = "a trip that just ended",
                    clock = clock(now),
                    signals =
                        quiet.copy(
                            activity =
                                AgreedActivity(
                                    lastEnded =
                                        Activity(
                                            ActivityKind.IN_VEHICLE,
                                            now - 40.minutes,
                                            now - 5.minutes,
                                        ),
                                ),
                        ),
                    expected = ReminderDecision.Deliver(ReminderTrigger.TRIP_ENDED, openMarks = 0),
                ),
                ReminderCase(
                    name = "a trip that ended an hour ago",
                    clock = clock(now),
                    signals =
                        quiet.copy(
                            activity =
                                AgreedActivity(
                                    lastEnded =
                                        Activity(
                                            ActivityKind.IN_VEHICLE,
                                            now - 90.minutes,
                                            now - 1.hours,
                                        ),
                                ),
                        ),
                    expected = skip(SkipReason.NOTHING_TO_REMIND),
                ),
                ReminderCase(
                    name = "waking up",
                    clock = clock(now),
                    signals =
                        quiet.copy(
                            activity = AgreedActivity(lastEnded = Activity(ActivityKind.SLEEP, now - 8.hours, now - 5.minutes)),
                        ),
                    expected = skip(SkipReason.NOTHING_TO_REMIND),
                ),
                ReminderCase(
                    name = "a walk that ended outside waking hours",
                    clock = clock(at(23, 30)),
                    signals =
                        quiet.copy(
                            activity = AgreedActivity(lastEnded = Activity(ActivityKind.MOVING, at(22, 30), at(23, 20))),
                        ),
                    expected = skip(SkipReason.OUTSIDE_WAKING_HOURS),
                ),
            ),
        )
    }

    @Test
    fun `an activity ending comes before arriving somewhere new`() {
        val now = at(15, 0)
        val signals =
            quiet.copy(
                newPlaceArrivedAt = now - 20.minutes,
                activity = AgreedActivity(lastEnded = Activity(ActivityKind.IN_VEHICLE, now - 60.minutes, now - 20.minutes)),
            )

        assertTable(listOf(ReminderCase("trip then arrival", clock(now), signals, ReminderDecision.Deliver(ReminderTrigger.TRIP_ENDED, 0))))
    }
}
