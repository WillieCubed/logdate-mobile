package app.logdate.client.sync.conflict

import kotlinx.datetime.Instant

/**
 * Strategy for resolving conflicts between local and remote versions of entities.
 */
interface ConflictResolver<T> {
    /**
     * Resolves a conflict between a local and remote version of an entity.
     *
     * @param local The local version of the entity
     * @param remote The remote version of the entity
     * @param localTimestamp When the local version was last modified
     * @param remoteTimestamp When the remote version was last modified
     * @return The resolution strategy to apply
     */
    fun resolve(
        local: T,
        remote: T,
        localTimestamp: Instant,
        remoteTimestamp: Instant
    ): ConflictResolution<T>
}

/**
 * Result of a conflict resolution operation.
 */
sealed class ConflictResolution<T> {
    /**
     * Keep the local version, discard the remote.
     */
    data class KeepLocal<T>(val value: T) : ConflictResolution<T>()

    /**
     * Keep the remote version, discard the local.
     */
    data class KeepRemote<T>(val value: T) : ConflictResolution<T>()

    /**
     * Merge both versions into a new version.
     */
    data class Merge<T>(val merged: T) : ConflictResolution<T>()

    /**
     * Conflict requires manual user intervention.
     */
    data class RequiresManualResolution<T>(
        val local: T,
        val remote: T,
        val reason: String
    ) : ConflictResolution<T>()
}

/**
 * Simple last-write-wins conflict resolution strategy.
 * Uses timestamps to determine which version to keep.
 */
class LastWriteWinsResolver<T> : ConflictResolver<T> {
    override fun resolve(
        local: T,
        remote: T,
        localTimestamp: Instant,
        remoteTimestamp: Instant
    ): ConflictResolution<T> {
        return when {
            remoteTimestamp > localTimestamp -> ConflictResolution.KeepRemote(remote)
            localTimestamp > remoteTimestamp -> ConflictResolution.KeepLocal(local)
            else -> ConflictResolution.KeepLocal(local) // Equal timestamps, prefer local
        }
    }
}
