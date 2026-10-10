package app.logdate.client.data.notes

import app.logdate.client.repository.journals.JournalNote
import kotlin.uuid.Uuid

/**
 * The media this note points at, or `null` for a note that has none.
 */
internal fun JournalNote.mediaRefOrNull(): String? =
    when (this) {
        is JournalNote.Image -> mediaRef
        is JournalNote.Video -> mediaRef
        is JournalNote.Audio -> mediaRef
        is JournalNote.Text -> null
    }

internal fun JournalNote.withCaption(captionMap: Map<Uuid, String>): JournalNote =
    when (this) {
        is JournalNote.Image -> copy(caption = captionMap[uid] ?: caption)
        is JournalNote.Video -> copy(caption = captionMap[uid] ?: caption)
        is JournalNote.Audio -> copy(caption = captionMap[uid] ?: caption)
        else -> this
    }

internal fun JournalNote.hasSamePersistedContent(other: JournalNote): Boolean {
    if (creationTimestamp.toEpochMilliseconds() != other.creationTimestamp.toEpochMilliseconds() ||
        timeZoneId != other.timeZoneId ||
        location?.coordinates != other.location?.coordinates ||
        location?.place?.id != other.location?.place?.id
    ) {
        return false
    }
    return when (this) {
        is JournalNote.Text -> other is JournalNote.Text && content == other.content
        is JournalNote.Image ->
            other is JournalNote.Image && mediaRef == other.mediaRef && caption == other.caption && presentation == other.presentation
        is JournalNote.Audio ->
            other is JournalNote.Audio && mediaRef == other.mediaRef && durationMs == other.durationMs && caption == other.caption
        is JournalNote.Video -> other is JournalNote.Video && mediaRef == other.mediaRef && caption == other.caption
    }
}
