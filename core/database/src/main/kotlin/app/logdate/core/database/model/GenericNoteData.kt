package app.logdate.core.database.model

import kotlinx.datetime.Instant

abstract class GenericNoteData {
    abstract val uid: Int
    abstract val lastUpdated: Instant
    abstract val created: Instant
}
