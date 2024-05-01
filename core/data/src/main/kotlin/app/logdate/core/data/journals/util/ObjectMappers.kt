package app.logdate.core.data.journals.util

import app.logdate.core.database.model.JournalEntity
import app.logdate.model.Journal

fun JournalEntity.toModel() = Journal(
    id = id.toString(),
    title = title,
    description = description,
    created = created,
    lastUpdated = lastUpdated,
    isFavorited = false, // TODO: Move this to separate user data source
)

fun Journal.toEntity() = JournalEntity(
    id = if (id.isEmpty()) 0 else id.toInt(),
    title = title,
    description = description,
    created = created,
    lastUpdated = lastUpdated,
)