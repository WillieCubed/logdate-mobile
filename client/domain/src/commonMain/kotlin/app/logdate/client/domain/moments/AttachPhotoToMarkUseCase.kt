package app.logdate.client.domain.moments

import app.logdate.client.media.ManagedMediaImporter
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMomentRepository
import app.logdate.client.sync.SyncTransactionManager
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AttachPhotoToMarkUseCase(
    private val marks: MarkedMomentRepository,
    private val notes: JournalNotesRepository,
    private val importer: ManagedMediaImporter,
    private val transactions: SyncTransactionManager,
) {
    suspend operator fun invoke(
        id: Uuid,
        sourceUri: String,
        caption: String? = null,
    ): Boolean {
        if (marks.getMark(id)?.state != MarkState.OPEN) return false
        val managed = importer.import(sourceUri)
        return transactions.withTransaction {
            val mark = marks.getMark(id) ?: return@withTransaction false
            if (mark.state != MarkState.OPEN) return@withTransaction false
            val now = Clock.System.now()
            val note =
                JournalNote.Image(
                    creationTimestamp = mark.capturedAt,
                    lastUpdated = now,
                    mediaRef = managed,
                    timeZoneId = mark.timeZoneId,
                    location =
                        mark.location?.let {
                            app.logdate.client.repository.journals.NoteLocation(
                                coordinates =
                                    app.logdate.client.repository.journals
                                        .NoteCoordinates(it.latitude, it.longitude),
                            )
                        },
                )
            val text = (caption ?: mark.caption).trim()
            val savedIds = mutableSetOf(notes.create(note))
            if (text.isNotEmpty()) {
                savedIds +=
                    notes.create(
                        JournalNote.Text(
                            creationTimestamp = mark.capturedAt,
                            lastUpdated = now,
                            content = text,
                            timeZoneId = mark.timeZoneId,
                            location = note.location,
                        ),
                    )
            }
            check(marks.fill(id, text, savedIds, now)) { "Marked moment could not be filled" }
            true
        }
    }
}
