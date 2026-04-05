package app.logdate.feature.journals.ui.share

import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sharing.ShareTheme
import app.logdate.client.sharing.SharingLauncher
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class ShareJournalViewModelTest {
    private val journal = Journal(id = Uuid.random(), title = "Weekend Trip")
    private val sharingLauncher = RecordingSharingLauncher()
    private val viewModel =
        ShareJournalViewModel(
            journalRepository = FakeShareJournalRepository(journal),
            sharingLauncher = sharingLauncher,
        )

    @Test
    fun `shareJournal delegates to system share launcher`() {
        viewModel.shareJournal(journal)

        assertEquals(journal.id, sharingLauncher.sharedJournalLinkId)
    }

    @Test
    fun `shareJournalQrCode delegates to QR share launcher`() {
        viewModel.shareJournalQrCode(journal)

        assertEquals(journal.id, sharingLauncher.sharedJournalQrCodeId)
    }

    private class RecordingSharingLauncher : SharingLauncher {
        var sharedJournalLinkId: Uuid? = null
        var sharedJournalQrCodeId: Uuid? = null

        override fun shareContent(
            text: String?,
            mediaUris: List<String>,
            title: String?,
            chooserTitle: String?,
        ) = Unit

        override fun shareMemoryDay(
            date: LocalDate,
            summary: String,
            mediaUris: List<String>,
        ) = Unit

        override fun shareJournalToInstagram(
            journalId: Uuid,
            theme: ShareTheme,
        ) = Unit

        override fun shareJournalLink(journalId: Uuid) {
            sharedJournalLinkId = journalId
        }

        override fun shareJournalQrCode(journalId: Uuid) {
            sharedJournalQrCodeId = journalId
        }

        override fun sharePhotoToInstagramFeed(photoId: String) = Unit

        override fun shareVideoToInstagramFeed(videoId: String) = Unit

        override fun getUriFromMedia(uid: String): Any = uid
    }

    private class FakeShareJournalRepository(
        journal: Journal,
    ) : JournalRepository {
        private val journalsFlow = MutableStateFlow(listOf(journal))

        override val allJournalsObserved: Flow<List<Journal>> = journalsFlow

        override fun observeJournalById(id: Uuid): Flow<Journal> = flowOf(journalsFlow.value.first { it.id == id })

        override suspend fun getJournalById(id: Uuid): Journal? = journalsFlow.value.firstOrNull { it.id == id }

        override suspend fun create(journal: Journal): Uuid = journal.id

        override suspend fun update(journal: Journal) = Unit

        override suspend fun delete(journalId: Uuid) = Unit

        override suspend fun saveDraft(draft: EditorDraft) = Unit

        override suspend fun getLatestDraft(): EditorDraft? = null

        override suspend fun getAllDrafts(): List<EditorDraft> = emptyList()

        override suspend fun getDraft(id: Uuid): EditorDraft? = null

        override suspend fun deleteDraft(id: Uuid) = Unit
    }
}
