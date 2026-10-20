package app.logdate.client.data.journals

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableTextBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class LocalDraftCustodyTest {
    @Test
    fun `sign out and same-owner sign in retain the latest offline edit`() =
        runTest {
            val f = CustodyFixture()
            f.signIn()
            val original = custodyDraft("Before sign out")
            f.repository().saveDraftFromSync(original)
            f.session.value = null
            val offline = original.copy(blocks = listOf(custodyBlock("Written while signed out")))
            f.repository().saveDraftFromSync(offline)
            f.signIn()
            assertEquals(listOf(offline), f.repository().getAllDraftsForSync())
        }

    @Test
    fun `restored anonymous draft with matching queued custody resumes without a user edit`() =
        runTest {
            val f = CustodyFixture()
            val draft = custodyDraft("Retained restore")
            f.seedAnonymous(draft)
            f.metadata.pending += PendingUpload(draft.id.toString(), PendingOperation.UPDATE, scope = f.scope)
            f.signIn()
            assertEquals(listOf(draft), f.repository().getAllDraftsForSync())
            assertEquals(listOf(draft), f.repository().getAllDraftsForSync())
            assertEquals(1, f.metadata.pending.size)
        }

    @Test
    fun `already stranded anonymous work stays visible but needs a normal save for server custody`() =
        runTest {
            val f = CustodyFixture()
            val draft = custodyDraft("Previously settled as missing")
            f.seedAnonymous(draft)
            f.signIn()
            assertEquals(listOf(draft), f.repository().getAllDrafts())
            assertTrue(f.repository().getAllDraftsForSync().isEmpty())
            f.repository().saveDraft(draft)
            assertEquals(listOf(draft), f.repository().getAllDraftsForSync())
            assertEquals(listOf(draft), f.repository().getAllDrafts())
        }

    @Test
    fun `a different authenticated owner cannot see or adopt anonymous local work`() =
        runTest {
            val f = CustodyFixture()
            val draft = custodyDraft("Owner A only")
            f.seedAnonymous(draft)
            f.metadata.pending += PendingUpload(draft.id.toString(), PendingOperation.UPDATE, scope = f.scope)
            f.signIn("owner-b")
            assertTrue(f.repository().getAllDrafts().isEmpty())
            assertTrue(f.repository().getAllDraftsForSync().isEmpty())
            f.signIn()
            assertEquals(listOf(draft), f.repository().getAllDraftsForSync())
        }

    @Test
    fun `queue custody for another origin does not authorize an upload`() =
        runTest {
            val f = CustodyFixture()
            val draft = custodyDraft("Accepted on server A")
            f.seedAnonymous(draft)
            f.metadata.pending += PendingUpload(draft.id.toString(), PendingOperation.UPDATE, scope = f.scope)
            f.config.updateBackendUrl("https://server-b.example")
            f.signIn()
            assertTrue(f.repository().getAllDraftsForSync().isEmpty())
            assertEquals(listOf(draft), f.repository().getAllDrafts())
            assertEquals(1, f.metadata.pending.size)
        }

    @Test
    fun `failed destination persistence leaves source and accepted queue for recreated retry`() =
        runTest {
            val f = CustodyFixture()
            val draft = custodyDraft("Durable accepted draft")
            f.seedAnonymous(draft)
            f.metadata.pending += PendingUpload(draft.id.toString(), PendingOperation.UPDATE, scope = f.scope)
            f.signIn()
            f.storage.failPrefix = "editor_drafts_scope_"
            assertFailsWith<IllegalStateException> { f.repository().getAllDraftsForSync() }
            assertTrue(f.storage.getStringOrThrow("editor_drafts_anonymous")!!.contains(draft.id.toString()))
            assertEquals(listOf(draft), f.repository().getAllDraftsForSync())
            assertEquals(1, f.metadata.pending.size)
        }

    @Test
    fun `divergent same-ID snapshots survive as stable siblings after interrupted cleanup`() =
        runTest {
            val f = CustodyFixture()
            val scoped = custodyDraft("Already in this account")
            f.signIn()
            f.repository().saveDraftFromSync(scoped)
            val anonymous = scoped.copy(blocks = listOf(custodyBlock("Accepted anonymous edit")))
            f.seedAnonymous(anonymous)
            f.metadata.pending += PendingUpload(scoped.id.toString(), PendingOperation.UPDATE, scope = f.scope)
            f.storage.failPrefix = "editor_drafts_anonymous"
            assertEquals(2, f.repository().getAllDraftsForSync().size)
            assertTrue(
                f.storage
                    .getStringOrThrow("editor_drafts_anonymous")
                    .orEmpty()
                    .contains(anonymous.id.toString()),
            )
            val recovered = f.repository().getAllDraftsForSync()
            assertEquals(2, recovered.size)
            assertEquals(scoped, recovered.single { it.id == scoped.id })
            val sibling = recovered.single { it.id != scoped.id }
            assertNotEquals(scoped.id, sibling.id)
            assertEquals(anonymous.blocks, sibling.blocks)
            assertEquals(anonymous.createdAt, sibling.createdAt)
            assertEquals(recovered, f.repository().getAllDraftsForSync())
            assertTrue(f.metadata.pending.any { it.entityId == sibling.id.toString() && it.scope == f.scope })
        }
}

internal class CustodyFixture {
    val storage = CustodyStorage()
    val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://server-a.example")
    val scope = UploadScope("owner-a", "https://server-a.example")
    val metadata = CustodyMetadata()
    val session = MutableStateFlow<UserSession?>(null)
    private val sessions =
        object : SessionStorage {
            override fun getSession() = session.value

            override fun getSessionFlow() = session

            override fun getOriginBoundSession() = session.value?.let { OriginBoundSession(config.getCurrentBackendUrl(), it) }

            override suspend fun hasValidSession() = session.value != null

            override suspend fun saveSession(session: UserSession) {
                this@CustodyFixture.session.value = session
            }

            override suspend fun clearSession() {
                session.value = null
            }
        }
    private val owner =
        object : CanonicalOwnerProvider {
            override suspend fun getCanonicalOwnerId() = "owner-a"

            override suspend fun hasBoundOwner() = true
        }

    fun signIn(account: String = "owner-a") {
        session.value = UserSession("test-access", "test-refresh", account)
    }

    fun repository(
        configurationReady: suspend () -> Unit = {},
        transactions: app.logdate.client.sync.SyncTransactionManager? = null,
    ) = LocalFirstDraftRepository(storage, Json, sessions, config, owner, metadata, configurationReady, transactions)

    suspend fun seedAnonymous(draft: EditorDraft) {
        storage.putStringOrThrow("editor_drafts_anonymous", Json.encodeToString(listOf(draft)))
    }
}

internal class CustodyMetadata : FakeSyncMetadataService() {
    val pending = mutableListOf<PendingUpload>()
    var failNextEnqueue = false

    override suspend fun getPendingUploads(entityType: EntityType) = if (entityType == EntityType.DRAFT) pending.toList() else emptyList()

    override suspend fun enqueuePendingForScope(
        entityId: String,
        entityType: EntityType,
        operation: PendingOperation,
        scope: UploadScope,
    ) {
        if (failNextEnqueue) {
            failNextEnqueue = false
            error("Synthetic scoped SQL enqueue interruption")
        }
        pending.removeAll { it.entityId == entityId && it.scope == scope }
        pending += PendingUpload(entityId, operation, scope = scope)
    }
}

internal class CustodyPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow<Preferences>(emptyPreferences())

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(data.value).also {
            data.value =
                it
        }
}

internal class CustodyStorage(
    private val delegate: KeyValueStorage = DataStoreKeyValueStorage(CustodyPreferences()),
) : KeyValueStorage by delegate {
    var failPrefix: String? = null
    var afterWrite: suspend (String) -> Unit = {}

    override suspend fun putStringOrThrow(
        key: String,
        value: String,
    ) {
        if (failPrefix?.let(key::startsWith) == true) {
            failPrefix = null
            error("Interrupted fixture persistence")
        }
        delegate.putStringOrThrow(key, value)
        afterWrite(key)
    }
}

internal fun custodyBlock(content: String) = SerializableTextBlock(Uuid.random(), Instant.fromEpochMilliseconds(1), content = content)

internal fun custodyDraft(content: String) = EditorDraft(blocks = listOf(custodyBlock(content)))
