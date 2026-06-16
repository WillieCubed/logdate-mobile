package app.logdate.client.data.events

import app.logdate.client.database.entities.EventEntity
import app.logdate.shared.model.Event
import app.logdate.shared.model.ExternalCalendarSource

internal fun EventEntity.toModel(): Event =
    Event(
        id = id,
        title = title,
        description = description,
        startTime = startTime,
        endTime = endTime,
        isAllDay = isAllDay,
        placeId = placeId,
        coverImageUri = coverImageUri,
        externalCalendarId = externalCalendarId,
        externalCalendarSource = externalCalendarSource?.let(::parseSource),
        created = created,
        lastUpdated = lastUpdated,
    )

internal fun Event.toEntity(): EventEntity =
    EventEntity(
        id = id,
        title = title,
        description = description,
        startTime = startTime,
        endTime = endTime,
        isAllDay = isAllDay,
        placeId = placeId,
        coverImageUri = coverImageUri,
        externalCalendarId = externalCalendarId,
        externalCalendarSource = externalCalendarSource?.name,
        created = created,
        lastUpdated = lastUpdated,
    )

private fun parseSource(raw: String): ExternalCalendarSource? = runCatching { ExternalCalendarSource.valueOf(raw) }.getOrNull()
