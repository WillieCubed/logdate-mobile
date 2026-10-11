package app.logdate.client.sync

import app.logdate.client.sync.metadata.EntityType

/** Tracks consecutive missing-file failures; a restart permits another read rather than prematurely setting a note aside. */
internal class MissingMediaFailureHistory {
    private val entries = mutableSetOf<Pair<EntityType, String>>()

    fun contains(
        entityType: EntityType,
        entityId: String,
    ): Boolean = entityType to entityId in entries

    fun record(
        entityType: EntityType,
        entityId: String,
        error: Throwable,
    ) {
        if (error is MissingMediaException) entries.add(entityType to entityId) else clear(entityType, entityId)
    }

    fun clear(
        entityType: EntityType,
        entityId: String,
    ) {
        entries.remove(entityType to entityId)
    }
}
