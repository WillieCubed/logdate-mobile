package app.logdate.client.domain.reminders

import app.logdate.client.sync.peer.state.Presence
import app.logdate.client.sync.peer.state.PresenceState

enum class NudgeTarget { PHONE, WATCH }

/** Selects one destination from a fresh, requested reading of the paired watch. */
object NudgeTargetPolicy {
    fun choose(
        watchReachable: Boolean,
        supportsReminders: Boolean,
        presence: Presence,
        now: Long,
    ): NudgeTarget {
        if (!watchReachable || !supportsReminders || presence.state != PresenceState.ON_WRIST) return NudgeTarget.PHONE
        if (presence.observedAt <= 0 || presence.observedAt > now) return NudgeTarget.PHONE
        return if (now - presence.observedAt <= 2_000) NudgeTarget.WATCH else NudgeTarget.PHONE
    }
}
