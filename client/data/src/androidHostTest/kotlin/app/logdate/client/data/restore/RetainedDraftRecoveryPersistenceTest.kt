package app.logdate.client.data.restore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.data.journals.ArchiveDraftStorageState
import app.logdate.client.data.journals.JournalDraftPersistence
import app.logdate.client.data.journals.LocalFirstDraftRepository
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.repository.journals.DraftRecoveryScope
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.model.EditorDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RetainedDraftRecoveryPersistenceTest {
    @Test
    fun `disk CAS refuses owner changes after its last preflight`() = runTest { race("identity.canonical_owner_id", OTHER) }

    @Test
    fun `disk CAS refuses backend changes after its last preflight`() = runTest { race("backend_url", "https://other.example") }

    @Test
    fun `disk CAS refuses receipt only acknowledgments after binding changes`() =
        runTest {
            race("identity.canonical_owner_bound", "true", acknowledgeOnly = true)
        }

    private suspend fun race(
        changedKey: String,
        changedValue: String,
        acknowledgeOnly: Boolean = false,
    ) {
        val root =
            kotlin.io.path
                .createTempDirectory("draft-fence-")
                .toFile()
        val fixture = Fixture(root)
        try {
            fixture.seed()
            val before = fixture.storage.getStringOrThrow(KEY)
            var commits = 0
            val intercepted =
                object : KeyValueStorage by fixture.storage {
                    override suspend fun compareAndSetStringsOrThrow(
                        expected: Map<String, String?>,
                        values: Map<String, String?>,
                    ): Boolean {
                        commits++
                        fixture.storage.putStringOrThrow(changedKey, changedValue)
                        return fixture.storage.compareAndSetStringsOrThrow(expected, values)
                    }
                }
            val repository = fixture.repository(intercepted)
            assertFailsWith<IllegalStateException> {
                repository.compareAndSetDraftForRetainedRestore(
                    fixture.draft.id,
                    fixture.draft.id,
                    if (acknowledgeOnly) null else fixture.draft,
                    fixture.draft.copy(lastModifiedAt = kotlin.time.Instant.DISTANT_FUTURE),
                    SCOPE,
                    "retained",
                )
            }
            assertEquals(1, commits)
            assertEquals(before, fixture.storage.getStringOrThrow(KEY))
            assertEquals(emptySet(), ArchiveDraftStorageState.decode(Json, fixture.storage.getStringOrThrow(KEY)).appliedRecoveries)
        } finally {
            fixture.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun `disk committed draft and receipt recover an absent outbox after restart`() =
        runTest {
            val root =
                kotlin.io.path
                    .createTempDirectory("draft-restart-")
                    .toFile()
            val first = Fixture(root)
            first.seed(empty = true)
            assertTrue(
                first.repository().compareAndSetDraftForRetainedRestore(
                    first.draft.id,
                    first.draft.id,
                    null,
                    first.draft,
                    SCOPE,
                    "retained",
                ),
            )
            val id = first.draft.id
            first.close()
            val restarted = Fixture(root)
            try {
                val repository = restarted.repository()
                assertTrue(repository.hasArchiveRecoveryDecision(id, SCOPE, "retained"))
                var queued = 0
                val metadata =
                    object : FakeSyncMetadataService() {
                        override suspend fun enqueueRepairForScopeIfAbsent(
                            entityId: String,
                            entityType: EntityType,
                            scope: UploadScope,
                        ) {
                            assertEquals(id.toString(), entityId)
                            assertEquals(UploadScope(OWNER, ORIGIN), scope)
                            queued++
                        }
                    }
                JournalDraftPersistence(repository, metadata, {
                    it
                }, {}, currentOutboxScope = { JournalMergeScope(OWNER, ORIGIN) }).ensureRecoveryQueued(id, SCOPE)
                assertEquals(1, queued)
                assertEquals(id, repository.getDraftForRestore(id)?.id)
            } finally {
                restarted.close()
                root.deleteRecursively()
            }
        }

    private class Fixture(
        root: File,
    ) {
        val job = SupervisorJob()
        val store =
            PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = {
                File(root, "draft.preferences_pb")
            })
        val storage = DataStoreKeyValueStorage(store)
        val draft = EditorDraft()
        val owner =
            object : CanonicalOwnerProvider {
                override suspend fun getCanonicalOwnerId() = requireNotNull(storage.getStringOrThrow("identity.canonical_owner_id"))

                override suspend fun hasBoundOwner() = storage.getStringOrThrow("identity.canonical_owner_bound") == "true"
            }

        suspend fun seed(empty: Boolean = false) {
            storage.updateStringsOrThrow(
                mapOf(
                    "identity.canonical_owner_id" to OWNER,
                    "identity.canonical_owner_bound" to "false",
                    "backend_url" to ORIGIN,
                    KEY to ArchiveDraftStorageState(drafts = if (empty) emptyList() else listOf(draft)).encode(Json),
                ),
            )
        }

        fun repository(selected: KeyValueStorage = storage) =
            LocalFirstDraftRepository(selected, Json, currentScope = {
                null
            }, canonicalOwner = owner)

        suspend fun close() = job.cancelAndJoin()
    }

    private companion object {
        const val OWNER = "347c6231-19f4-49fa-bd1d-58ed34b9be03"
        const val OTHER = "c4e029bc-0a62-4ec1-9387-cc0f6f1ed5b9"
        const val ORIGIN = "https://cloud.example"
        const val KEY = "editor_drafts_anonymous"
        val SCOPE = DraftRecoveryScope(null, null, OWNER, ORIGIN)
    }
}
