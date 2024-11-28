package app.logdate.client.data.journals

import app.logdate.client.database.entities.JournalEntity
import app.logdate.shared.model.Journal

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