package app.logdate.client.sync.conflict

import app.logdate.shared.model.Journal
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Conflict resolver for journals that favors safe field merges over LWW.
 * Falls back to manual resolution when key text fields diverge.
 */
class JournalConflictResolver : ConflictResolver<Journal> {
    override fun resolve(
        local: Journal,
        remote: Journal,
        localTimestamp: Instant,
        remoteTimestamp: Instant,
    ): ConflictResolution<Journal> {
        if (local == remote) {
            return ConflictResolution.KeepRemote(remote)
        }

        val titleConflict =
            local.title.isNotBlank() &&
                remote.title.isNotBlank() &&
                local.title != remote.title
        val descriptionConflict =
            local.description.isNotBlank() &&
                remote.description.isNotBlank() &&
                local.description != remote.description

        if (titleConflict || descriptionConflict) {
            val reason =
                buildString {
                    append("Conflicting journal fields: ")
                    if (titleConflict) {
                        append("title")
                    }
                    if (titleConflict && descriptionConflict) {
                        append(", ")
                    }
                    if (descriptionConflict) {
                        append("description")
                    }
                }
            return ConflictResolution.RequiresManualResolution(local, remote, reason)
        }

        val merged =
            local.copy(
                title =
                    when {
                        local.title.isBlank() -> remote.title
                        remote.title.isBlank() -> local.title
                        else -> remote.title
                    },
                description =
                    when {
                        local.description.isBlank() -> remote.description
                        remote.description.isBlank() -> local.description
                        else -> remote.description
                    },
                isFavorited = local.isFavorited || remote.isFavorited,
                created = minOf(local.created, remote.created),
                lastUpdated = maxOf(local.lastUpdated, remote.lastUpdated, Clock.System.now()),
                syncVersion = maxOf(local.syncVersion, remote.syncVersion),
            )

        return ConflictResolution.Merge(merged)
    }
}
