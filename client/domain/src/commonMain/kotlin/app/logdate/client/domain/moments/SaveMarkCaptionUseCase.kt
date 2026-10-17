package app.logdate.client.domain.moments

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.moments.MarkedMomentRepository
import app.logdate.client.sync.SyncTransactionManager
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Publishes the caption as an ordinary timeline note and resolves its mark in one local transaction. */
class SaveMarkCaptionUseCase(
    private val marks: MarkedMomentRepository,
    private val notes: JournalNotesRepository,
    private val transactions: SyncTransactionManager,
) {
    suspend operator fun invoke(
        id: Uuid,
        caption: String,
    ): Boolean {
        val text = caption.trim()
        if (text.isEmpty()) return false
        return transactions.withTransaction {
            val mark = marks.getMark(id) ?: return@withTransaction false
            if (mark.caption == text && mark.resolvedNoteIds.isNotEmpty()) return@withTransaction true
            val now = Clock.System.now()
            val note =
                JournalNote.Text(
                    creationTimestamp = mark.capturedAt,
                    lastUpdated = now,
                    content = text,
                    timeZoneId = mark.timeZoneId,
                    capturedDuring = mark.capturedDuring,
                    location =
                        mark.location?.let {
                            app.logdate.client.repository.journals.NoteLocation(
                                coordinates =
                                    app.logdate.client.repository.journals.NoteCoordinates(
                                        it.latitude,
                                        it.longitude,
                                        accuracy = it.accuracyMeters,
                                    ),
                            )
                        },
                )
            val noteId = notes.create(note)
            check(marks.fill(id, text, setOf(noteId), now)) { "Marked moment could not be filled" }
            true
        }
    }
}
