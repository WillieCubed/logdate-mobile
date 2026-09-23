package app.logdate.feature.core.settings.account.move

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import kotlinx.coroutines.flow.first

/**
 * What a move would carry: counts shown before anything happens.
 *
 * @property remoteOnlyMedia photos, videos and voice notes whose file never downloaded to this
 *   device, so it exists only on the current server
 */
data class MoveSurvey(
    val entries: Int,
    val journals: Int,
    val media: Int,
    val drafts: Int,
    val remoteOnlyMedia: Int,
)

class LocalDataSurvey(
    private val journalRepository: JournalRepository,
    private val journalNotesRepository: JournalNotesRepository,
) {
    suspend operator fun invoke(): MoveSurvey {
        val notes = journalNotesRepository.allNotesObserved.first()
        val mediaRefs = notes.mapNotNull { it.mediaRef() }
        return MoveSurvey(
            entries = notes.size,
            journals = journalRepository.allJournalsObserved.first().size,
            media = mediaRefs.size,
            drafts = journalRepository.getAllDrafts().size,
            remoteOnlyMedia = mediaRefs.count { it.startsWith("http://") || it.startsWith("https://") },
        )
    }
}

private fun JournalNote.mediaRef(): String? =
    when (this) {
        is JournalNote.Image -> mediaRef
        is JournalNote.Video -> mediaRef
        is JournalNote.Audio -> mediaRef
        else -> null
    }
