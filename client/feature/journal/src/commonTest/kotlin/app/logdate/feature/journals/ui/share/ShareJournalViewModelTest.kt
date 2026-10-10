package app.logdate.feature.journals.ui.share

import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sharing.ShareTheme
import app.logdate.client.sharing.SharingLauncher
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tests for [ShareJournalViewModel], which manages the user interactions for sharing
 * entire journals with others.
 *
 * A journal link only reaches someone else when the connected server hosts shared journals, so
 * link and QR code sharing are offered only when the server advertises that support.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShareJournalViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val journal = Journal(id = Uuid.random(), title = "Weekend Trip")
    private val sharingLauncher = RecordingSharingLauncher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `offers link sharing when the server hosts shared journals`() =
        runTest(dispatcher) {
            val viewModel = viewModel(linkSharingAvailable = true)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = assertIs<ShareJournalUiState.Success>(viewModel.uiState.value)
            assertTrue(state.linkSharingAvailable)
        }

    @Test
    fun `withholds link sharing when the server does not host shared journals`() =
        runTest(dispatcher) {
            val viewModel = viewModel(linkSharingAvailable = false)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = assertIs<ShareJournalUiState.Success>(viewModel.uiState.value)
            assertFalse(state.linkSharingAvailable)
        }

    @Test
    fun `shareJournal delegates to system share launcher`() {
        viewModel(linkSharingAvailable = true).shareJournal(journal)

        assertEquals(journal.id, sharingLauncher.sharedJournalLinkId)
    }

    @Test
    fun `shareJournalQrCode delegates to QR share launcher`() {
        viewModel(linkSharingAvailable = true).shareJournalQrCode(journal)

        assertEquals(journal.id, sharingLauncher.sharedJournalQrCodeId)
    }

    @Test
    fun `link and QR sharing do nothing when the server does not host shared journals`() {
        val viewModel = viewModel(linkSharingAvailable = false)

        viewModel.shareJournal(journal)
        viewModel.shareJournalQrCode(journal)

        assertNull(sharingLauncher.sharedJournalLinkId)
        assertNull(sharingLauncher.sharedJournalQrCodeId)
    }

    private fun viewModel(linkSharingAvailable: Boolean) =
        ShareJournalViewModel(
            journalRepository = FakeShareJournalRepository(journal),
            sharingLauncher = sharingLauncher,
            linkSharingAvailability = { linkSharingAvailable },
        ).apply { setJournalId(journal.id) }

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
