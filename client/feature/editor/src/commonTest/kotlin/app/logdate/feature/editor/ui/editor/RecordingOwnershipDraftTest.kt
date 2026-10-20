package app.logdate.feature.editor.ui.editor

import app.logdate.client.domain.notes.drafts.CleanupExpiredDraftsUseCase
import app.logdate.client.domain.notes.drafts.CreateEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.DeleteAllDraftsUseCase
import app.logdate.client.domain.notes.drafts.DeleteEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.FetchEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.UpdateEntryDraftUseCase
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import app.logdate.feature.editor.ui.editor.delegate.DraftManager
import app.logdate.feature.editor.ui.editor.fakes.FakeEntryDraftRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeJournalNotesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class RecordingOwnershipDraftTest {
    @Test
    fun `reopening a pending recording requires the account that started it`() =
        runTest {
            val repository = FakeEntryDraftRepository()
            val draft = repository.createDraft(emptyList())
            val pending =
                PendingMediaRecord(
                    Uuid.random(),
                    PendingMediaType.AUDIO,
                    Clock.System.now(),
                    filePath = "/recording.m4a",
                    recordingOwnerId = "original",
                )
            repository.setPendingMedia(draft, listOf(pending))
            var owner = "other"
            val manager =
                DraftManager(
                    UpdateEntryDraftUseCase(repository),
                    CreateEntryDraftUseCase(repository),
                    FetchEntryDraftUseCase(repository),
                    DeleteEntryDraftUseCase(repository, FakeJournalNotesRepository()),
                    DeleteAllDraftsUseCase(repository),
                    CleanupExpiredDraftsUseCase(repository),
                    recordingOwner = { owner },
                )
            assertTrue(manager.loadDraft(draft).isFailure)
            assertEquals(
                pending,
                repository
                    .getDraft(draft)
                    .first()
                    .getOrThrow()
                    .pendingMedia
                    .single(),
            )
            owner = "original"
            assertTrue(manager.loadDraft(draft).isSuccess)
        }
}
