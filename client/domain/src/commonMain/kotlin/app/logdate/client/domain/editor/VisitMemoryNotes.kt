package app.logdate.client.domain.editor

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import app.logdate.shared.model.location.VisitMemoryContext
import kotlin.uuid.Uuid

internal fun JournalNote.withVisitContext(context: VisitMemoryContext): JournalNote {
    val placeName = context.placeName
    val placeId = context.placeId?.let { Uuid.parseOrNull(it) }
    val location =
        NoteLocation(
            coordinates = NoteCoordinates(context.latitude, context.longitude),
            place =
                if (placeId != null && placeName != null) {
                    NotePlace(placeId, placeName, context.latitude, context.longitude)
                } else {
                    null
                },
        )
    return when (this) {
        is JournalNote.Text -> copy(location = location)
        is JournalNote.Image -> copy(location = location)
        is JournalNote.Video -> copy(location = location)
        is JournalNote.Audio -> copy(location = location)
    }
}
