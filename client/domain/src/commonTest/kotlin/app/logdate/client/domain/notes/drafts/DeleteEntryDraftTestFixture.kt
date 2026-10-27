package app.logdate.client.domain.notes.drafts

import app.logdate.client.media.MediaCleaner
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.PendingMediaRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlin.test.BeforeTest
import kotlin.time.Instant
import kotlin.uuid.Uuid

abstract class DeleteEntryDraftTestFixture {
    protected lateinit var mockRepository: DeleteDraftRepositoryFake
    protected lateinit var notesRepository: DeleteDraftNotesRepositoryFake
    protected lateinit var useCase: DeleteEntryDraftUseCase

    protected lateinit var mediaCleaner: DeleteDraftMediaCleaner
    protected var coverUris = emptyList<String?>()
    protected val operations = mutableListOf<String>()

    @BeforeTest
    fun setUp() {
        mockRepository = DeleteDraftRepositoryFake()
        notesRepository = DeleteDraftNotesRepositoryFake()
        mediaCleaner = DeleteDraftMediaCleaner()
        coverUris = emptyList()
        operations.clear()
        mockRepository.onDelete = { operations += "draft" }
        mediaCleaner.onDelete = { operations += "media" }
        useCase =
            DeleteEntryDraftUseCase(
                entryDraftRepository = mockRepository,
                journalNotesRepository = notesRepository,
                mediaCleaner = mediaCleaner,
                journalCoverUris = { coverUris },
            )
    }

    protected class DeleteDraftRepositoryFake : EntryDraftRepository {
        val deletedDraftIds = mutableListOf<Uuid>()
        var shouldThrowException = false
        var shouldThrowOnRead = false
        var onDelete: () -> Unit = {}
        private val seeded = mutableMapOf<Uuid, EntryDraft>()

        fun seedDraft(draft: EntryDraft) {
            seeded[draft.id] = draft
        }

        override suspend fun setPendingMedia(
            uid: Uuid,
            pendingMedia: List<PendingMediaRecord>,
        ) = Unit

        override suspend fun deleteDraft(uid: Uuid) {
            if (shouldThrowException) {
                throw Exception("Repository error")
            }
            deletedDraftIds.add(uid)
            seeded.remove(uid)
            onDelete()
        }

        override fun getDrafts(): Flow<List<EntryDraft>> = flowOf(seeded.values.toList())

        override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> {
            if (shouldThrowOnRead) throw IllegalStateException("draft storage is unreadable")
            val draft = seeded[uid]
            return if (draft != null) {
                flowOf(Result.success(draft))
            } else {
                flowOf(Result.failure(NoSuchElementException()))
            }
        }

        override suspend fun createDraft(notes: List<JournalNote>): Uuid = Uuid.random()

        override suspend fun updateDraft(
            uid: Uuid,
            notes: List<JournalNote>,
        ): Uuid = uid

        override suspend fun deleteAllDrafts() {}

        override suspend fun deleteExpiredDrafts(maxAge: kotlin.time.Duration): Int = 0
    }

    protected class DeleteDraftNotesRepositoryFake : JournalNotesRepository {
        private val notes = MutableStateFlow<List<JournalNote>>(emptyList())

        /** Number of times [allNotesObserved] was collected -- a full-table-scan canary. */
        var allNotesObservedCollections = 0
        var notesReferencingMediaPathsCalls = 0
        var lastQueriedPaths: Set<String>? = null

        fun seed(note: JournalNote) {
            notes.value = notes.value + note
        }

        override val allNotesObserved: Flow<List<JournalNote>> =
            notes.onEach { allNotesObservedCollections++ }

        override suspend fun notesReferencingMediaPaths(paths: Set<String>): Set<String> {
            notesReferencingMediaPathsCalls++
            lastQueriedPaths = paths
            return notes.value
                .mapNotNull { note ->
                    when (note) {
                        is JournalNote.Audio -> note.mediaRef
                        is JournalNote.Image -> note.mediaRef
                        is JournalNote.Video -> note.mediaRef
                        is JournalNote.Text, is JournalNote.Handwriting -> null
                    }
                }.filterTo(mutableSetOf()) { it in paths }
        }

        override fun observeNotesInJournal(journalId: Uuid) = flowOf(emptyList<JournalNote>())

        override fun observeNotesInRange(
            start: Instant,
            end: Instant,
        ) = flowOf(emptyList<JournalNote>())

        override fun observeNotesPage(
            pageSize: Int,
            offset: Int,
        ) = flowOf(emptyList<JournalNote>())

        override fun observeNotesStream(pageSize: Int) = flowOf(emptyList<JournalNote>())

        override fun observeRecentNotes(limit: Int) = flowOf(emptyList<JournalNote>())

        override suspend fun getNoteById(noteId: Uuid): JournalNote? = notes.value.find { it.uid == noteId }

        override suspend fun create(note: JournalNote): Uuid {
            seed(note)
            return note.uid
        }

        override suspend fun remove(note: JournalNote) {
            notes.value = notes.value.filterNot { it.uid == note.uid }
        }

        override suspend fun removeById(noteId: Uuid) {
            notes.value = notes.value.filterNot { it.uid == noteId }
        }

        override suspend fun create(
            note: JournalNote,
            journalId: Uuid,
        ) {
            seed(note)
        }

        override suspend fun removeFromJournal(
            noteId: Uuid,
            journalId: Uuid,
        ) = Unit

        override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()
    }

    protected class DeleteDraftMediaCleaner : MediaCleaner {
        val deletedPaths = mutableListOf<String>()
        var shouldThrow = false
        var cancellationToThrow: CancellationException? = null
        var onDelete: () -> Unit = {}

        override suspend fun delete(path: String) {
            cancellationToThrow?.let { throw it }
            if (shouldThrow) throw IllegalStateException("simulated cleaner failure")
            deletedPaths.add(path)
            onDelete()
        }
    }
}
