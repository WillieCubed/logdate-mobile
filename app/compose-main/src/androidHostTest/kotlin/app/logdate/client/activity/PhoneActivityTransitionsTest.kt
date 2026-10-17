package app.logdate.client.activity

import app.logdate.client.domain.activity.ActivityKind
import app.logdate.client.domain.activity.ActivityTransition
import com.google.android.gms.location.DetectedActivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import com.google.android.gms.location.ActivityTransition as PlayTransition

class PhoneActivityTransitionsTest {
    private val now = Instant.fromEpochSeconds(1_760_000_000)
    private val elapsedNow = 500_000_000_000L

    @Test
    fun `walking running and cycling are all moving`() {
        listOf(DetectedActivity.WALKING, DetectedActivity.RUNNING, DetectedActivity.ON_FOOT, DetectedActivity.ON_BICYCLE).forEach {
            assertEquals(ActivityKind.MOVING, activityKindOf(it))
        }
    }

    @Test
    fun `standing still is not an activity`() {
        assertNull(activityKindOf(DetectedActivity.STILL))
        assertNull(activityKindOf(DetectedActivity.TILTING))
    }

    @Test
    fun `a transition is placed by how long ago it happened`() {
        val thirtySecondsAgo = elapsedNow - 30.seconds.inWholeNanoseconds

        assertEquals(
            ActivityTransition(ActivityKind.IN_VEHICLE, entered = true, at = now - 30.seconds),
            activityTransitionOf(DetectedActivity.IN_VEHICLE, PlayTransition.ACTIVITY_TRANSITION_ENTER, thirtySecondsAgo, now, elapsedNow),
        )
        assertEquals(
            ActivityTransition(ActivityKind.MOVING, entered = false, at = now),
            activityTransitionOf(DetectedActivity.WALKING, PlayTransition.ACTIVITY_TRANSITION_EXIT, elapsedNow, now, elapsedNow),
        )
    }

    @Test
    fun `a transition stamped after now is placed at now`() {
        assertEquals(
            now,
            activityTransitionOf(
                DetectedActivity.WALKING,
                PlayTransition.ACTIVITY_TRANSITION_ENTER,
                elapsedNow + 1_000,
                now,
                elapsedNow,
            )?.at,
        )
    }
}
