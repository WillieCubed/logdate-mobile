package app.logdate.client.database.entities

import kotlinx.datetime.Instant

import kotlin.uuid.Uuid

abstract class GenericNoteData {
    abstract val uid: Uuid
    abstract val lastUpdated: Instant
    abstract val created: Instant
    
    abstract val syncVersion: Long
    abstract val lastSynced: Instant?
    abstract val deletedAt: Instant?
}
