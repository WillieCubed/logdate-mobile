package app.logdate.client.domain.activity

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** The activity under way, and when its signal last stopped, if it did. */
@Serializable
data class OpenActivity(
    val kind: ActivityKind,
    val startedAt: Instant,
    val leftAt: Instant? = null,
)

/**
 * One device's activities: the one under way and the last one that ended and counted.
 *
 * Pure, so every rule is tested without a clock or storage. An activity starts at its first signal
 * and ends where its signal stopped, once the kind's end grace has passed without it coming back. It
 * counts only if it lasted the kind's minimum; a shorter one is forgotten.
 */
@Serializable
data class ActivityLog(
    val open: OpenActivity? = null,
    val lastEnded: Activity? = null,
) {
    /** The activity under way, counted from its first signal so reminders wait at once. */
    val current: Activity?
        get() = open?.let { Activity(it.kind, it.startedAt, endedAt = null) }

    fun accept(transition: ActivityTransition): ActivityLog {
        val open = open
        if (!transition.entered) {
            if (open?.kind != transition.kind || open.leftAt != null) return this
            return copy(open = open.copy(leftAt = transition.at))
        }
        if (open == null) return copy(open = OpenActivity(transition.kind, transition.at))
        if (open.kind == transition.kind) return copy(open = open.copy(leftAt = null))
        return end(open, open.leftAt ?: transition.at).copy(open = OpenActivity(transition.kind, transition.at))
    }

    /** When the open activity counts as ended, if its signal has stopped. */
    fun settlesAt(): Instant? {
        val open = open ?: return null
        return open.leftAt?.plus(open.kind.endGrace)
    }

    /** Ends the open activity once its end grace has passed by [now]. */
    fun settle(now: Instant): ActivityLog {
        val open = open ?: return this
        val leftAt = open.leftAt ?: return this
        if (now < leftAt + open.kind.endGrace) return this
        return end(open, leftAt)
    }

    private fun end(
        open: OpenActivity,
        endedAt: Instant,
    ): ActivityLog {
        val counted = endedAt - open.startedAt >= open.kind.minimum
        return ActivityLog(open = null, lastEnded = if (counted) Activity(open.kind, open.startedAt, endedAt) else lastEnded)
    }
}
