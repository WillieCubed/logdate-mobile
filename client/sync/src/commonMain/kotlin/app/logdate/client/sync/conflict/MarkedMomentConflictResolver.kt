package app.logdate.client.sync.conflict

import app.logdate.client.repository.moments.MarkedMoment

/** Monotonic state and attachments, with a deterministic latest scalar snapshot. */
class MarkedMomentConflictResolver {
    fun merge(
        local: MarkedMoment,
        remote: MarkedMoment,
    ): MarkedMoment {
        require(local.id == remote.id && local.capturedAt == remote.capturedAt && local.originDeviceId == remote.originDeviceId) {
            "Marked moments from different captures cannot be merged"
        }
        // Joined fields must not participate in this election: doing so makes later merges order dependent.
        val order =
            compareValuesBy(
                local,
                remote,
                { it.lastUpdated },
                { it.caption },
                { it.timeZoneId },
                { it.location?.latitude },
                { it.location?.longitude },
                { it.location?.accuracyMeters },
                { it.heartRateBpm },
            )
        val winner = if (order >= 0) local else remote
        return winner.copy(
            state = maxOf(local.state, remote.state),
            resolvedNoteIds = local.resolvedNoteIds + remote.resolvedNoteIds,
            syncVersion = maxOf(local.syncVersion, remote.syncVersion),
            // Recorded once at capture; a copy that has it keeps it, and two that differ settle the same way in either order.
            capturedDuring = listOfNotNull(local.capturedDuring, remote.capturedDuring).minOrNull(),
        )
    }
}
