package app.logdate.wear.sync

import kotlin.uuid.Uuid

/** Tells the phone a note the watch created is gone, for a removal the sync outbox would otherwise swallow. */
fun interface WearNoteRemovalNotifier {
    suspend fun notifyRemoved(noteId: Uuid)
}
