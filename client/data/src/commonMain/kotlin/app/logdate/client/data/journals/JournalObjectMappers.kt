package app.logdate.client.data.journals

import app.logdate.client.database.entities.JournalEntity
import app.logdate.shared.model.Journal

/**
 * Convert a database entity to a domain model.
 * 
 * The id is already a string in the entity, so no conversion is needed.
 */
fun JournalEntity.toModel() = Journal(
    id = id,
    title = title,
    description = description,
    created = created,
    lastUpdated = lastUpdated,
    isFavorited = false, // TODO: Move this to separate user data source
)

/**
 * Convert a domain model to a database entity.
 * 
 * If the ID is empty, a new UUID will be generated.
 */
fun Journal.toEntity() = JournalEntity(
    id = id,
    title = title,
    description = description,
    created = created,
    lastUpdated = lastUpdated,
)