package app.logdate.client.sync

import app.logdate.client.media.storage.UnavailableMediaReferences
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.mediaRefOrNull
import app.logdate.client.sync.space.SpaceMediaReference

/** Working text stays on this device and belongs to the original recording, not its mutable caption. */
internal fun JournalNote.withLocalAudioWorkingText(
    local: JournalNote?,
    localSourceReference: String? = local?.mediaRefOrNull(),
): JournalNote {
    if (this !is JournalNote.Audio ||
        local !is JournalNote.Audio ||
        uid != local.uid ||
        transcription.isNotEmpty() ||
        local.transcription.isEmpty()
    ) {
        return this
    }
    val source = localSourceReference ?: return this
    val previous = SpaceMediaReference.parse(source)
    val incoming = SpaceMediaReference.parse(mediaRef)
    val sameOriginal =
        if (previous != null || incoming != null) {
            previous != null &&
                incoming != null &&
                previous.contentId == uid &&
                incoming.contentId == uid &&
                previous.cid == incoming.cid
        } else {
            source.isNotBlank() &&
                !UnavailableMediaReferences.isUnavailable(source) &&
                source == mediaRef &&
                local.durationMs == durationMs
        }
    return if (sameOriginal) copy(transcription = local.transcription) else this
}
