package app.logdate.client.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.data.notes.drafts.AndroidLocalEntryDraftStore
import app.logdate.client.data.notes.drafts.OfflineFirstEntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Verifies complete draft snapshots survive production Android store/repository recreation. */
@RunWith(AndroidJUnit4::class)
class AndroidDraftPersistenceE2ETest {
    @Test
    fun completeSnapshotSurvivesStoreAndRepositoryRecreation() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val firstStore = AndroidLocalEntryDraftStore(context)
            val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            var secondScope: CoroutineScope? = null
            firstStore.clearAllDrafts()

            try {
                val draftId = Uuid.random()
                val timestamp = Clock.System.now()
                val notes =
                    listOf(
                        JournalNote.Text(
                            uid = Uuid.random(),
                            content = "Android process-restart draft",
                            creationTimestamp = timestamp,
                            lastUpdated = timestamp,
                        ),
                    )
                val pendingMedia =
                    listOf(
                        PendingMediaRecord(
                            blockId = Uuid.random(),
                            mediaType = PendingMediaType.AUDIO,
                            createdAt = timestamp,
                            filePath = "/recordings/android-pending.m4a",
                        ),
                    )
                val selectedJournalIds = listOf(Uuid.random(), Uuid.random())
                val firstRepository =
                    OfflineFirstEntryDraftRepository(
                        draftStore = firstStore,
                        coroutineScope = firstScope,
                    )

                assertEquals(
                    draftId,
                    firstRepository.createDraft(
                        uid = draftId,
                        notes = notes,
                        pendingMedia = pendingMedia,
                        selectedJournalIds = selectedJournalIds,
                    ),
                )
                firstScope.cancel()

                val recreatedStore = AndroidLocalEntryDraftStore(context)
                secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val recreatedRepository =
                    OfflineFirstEntryDraftRepository(
                        draftStore = recreatedStore,
                        coroutineScope = secondScope,
                    )

                val restored = recreatedRepository.getDraft(draftId).first().getOrThrow()
                assertEquals(draftId, restored.id)
                assertEquals(notes, restored.notes)
                assertEquals(pendingMedia, restored.pendingMedia)
                assertEquals(selectedJournalIds, restored.selectedJournalIds)
                recreatedRepository.deleteAllDrafts()
            } finally {
                firstScope.cancel()
                secondScope?.cancel()
                firstStore.clearAllDrafts()
            }
        }
}
