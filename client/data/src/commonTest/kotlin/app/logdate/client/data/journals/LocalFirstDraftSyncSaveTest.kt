package app.logdate.client.data.journals

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.client.device.storage.SecureStorage
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableTextBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class LocalFirstDraftSyncSaveTest {
    @Test
    fun `sync read reports scoped storage failure instead of an empty draft list`() =
        runTest {
            val storage = StringStorage()
            val scope = DraftStorageScope("owner-a", "https://server.example")
            val repository = LocalFirstDraftRepository(storage, Json, currentScope = { scope })
            val draft = EditorDraft()
            repository.saveDraftFromSync(draft)

            storage.failNextScopedRead = true
            assertFailsWith<IllegalStateException> { repository.getAllDraftsForSync() }
            assertEquals(listOf(draft), repository.getAllDraftsForSync())
        }

    @Test
    fun `sync read reports failed legacy migration and resumes for its owner`() =
        runTest {
            val storage = StringStorage()
            val draft = EditorDraft()
            storage.values["editor_drafts"] = Json.encodeToString(listOf(draft))
            val scope = DraftStorageScope("owner-a", "https://server.example")
            val repository = LocalFirstDraftRepository(storage, Json, currentScope = { scope })

            storage.failNextScopedWrite = true
            assertFailsWith<IllegalStateException> { repository.getAllDraftsForSync() }
            assertEquals(listOf(draft), repository.getAllDraftsForSync())
        }

    @Test
    fun `sync update preserves remote block and draft timestamps`() =
        runTest {
            val repository = LocalFirstDraftRepository(StringStorage(), Json { ignoreUnknownKeys = true })
            val id = Uuid.random()
            val first = EditorDraft(id = id, lastModifiedAt = Instant.parse("2026-01-01T00:00:00Z"))
            repository.saveDraft(first)
            val remoteTime = Instant.parse("2026-01-02T00:00:00Z")
            val remote =
                first.copy(
                    blocks = listOf(SerializableTextBlock(Uuid.random(), remoteTime, content = "remote edit")),
                    lastModifiedAt = remoteTime,
                )

            repository.saveDraftFromSync(remote)

            assertEquals(remote, repository.getDraft(id))
        }

    @Test
    fun `legacy drafts bind once to current owner and do not appear in another account`() =
        runTest {
            val storage = StringStorage()
            val legacy = EditorDraft()
            storage.values["editor_drafts"] = Json.encodeToString(listOf(legacy))
            val first = DraftStorageScope("owner-a", "https://server.example")
            var selected = first
            val repository = LocalFirstDraftRepository(storage, Json, currentScope = { selected })

            assertEquals(listOf(legacy), repository.getAllDrafts())
            selected = DraftStorageScope("owner-b", "https://server.example")
            assertTrue(repository.getAllDrafts().isEmpty())
            selected = first
            assertEquals(listOf(legacy), LocalFirstDraftRepository(storage, Json, currentScope = { selected }).getAllDrafts())
        }

    @Test
    fun `failed legacy copy can resume only for its original owner`() =
        runTest {
            val storage = StringStorage()
            val legacy = EditorDraft()
            storage.values["editor_drafts"] = Json.encodeToString(listOf(legacy))
            val first = DraftStorageScope("owner-a", "https://server.example")
            var selected = first
            storage.failNextScopedWrite = true
            LocalFirstDraftRepository(storage, Json, currentScope = { selected }).getAllDrafts()

            selected = DraftStorageScope("owner-b", "https://server.example")
            assertTrue(LocalFirstDraftRepository(storage, Json, currentScope = { selected }).getAllDrafts().isEmpty())
            selected = first
            assertEquals(listOf(legacy), LocalFirstDraftRepository(storage, Json, currentScope = { selected }).getAllDrafts())
        }

    @Test
    fun `observed drafts switch with account scope`() =
        runTest {
            val storage = StringStorage()
            val first = DraftStorageScope("owner-a", "https://server.example")
            val scope = MutableStateFlow<DraftStorageScope?>(first)
            val repository = LocalFirstDraftRepository(storage, Json, currentScope = { scope.value }, scopeChanges = scope)
            val seen = mutableListOf<List<EditorDraft>>()
            backgroundScope.launch { repository.allDrafts.collect { seen += it } }
            runCurrent()
            val draft = EditorDraft()
            repository.saveDraftFromSync(draft)
            runCurrent()
            assertEquals(listOf(draft), seen.last())

            scope.value = DraftStorageScope("owner-b", "https://server.example")
            runCurrent()
            assertTrue(seen.last().isEmpty())
        }

    @Test
    fun `account switch during legacy claim cannot return previous owner's draft`() =
        runTest {
            val storage = StringStorage()
            val legacy = EditorDraft()
            storage.values["editor_drafts"] = Json.encodeToString(listOf(legacy))
            val first = DraftStorageScope("owner-a", "https://server.example")
            var selected = first
            storage.onPut = { key ->
                if (key == "editor_drafts_legacy_owner") selected = DraftStorageScope("owner-b", "https://server.example")
            }
            val repository = LocalFirstDraftRepository(storage, Json, currentScope = { selected })

            assertTrue(repository.getAllDrafts().isEmpty())
            assertTrue(repository.getAllDrafts().isEmpty())
            selected = first
            assertEquals(listOf(legacy), repository.getAllDrafts())
        }

    @Test
    fun `production repository cannot claim legacy drafts with cached session from previous server`() =
        runTest {
            val firstOrigin = "https://first.example"
            val secondOrigin = "https://second.example"
            val config = DefaultLogDateConfigRepository(initialBackendUrl = firstOrigin)
            val session = SecureSessionStorage(StringSecureStorage(), config, backgroundScope)
            session.saveSession(UserSession("access", "refresh", "owner-a"))
            runCurrent()
            val storage = StringStorage()
            val legacy = EditorDraft()
            storage.values["editor_drafts"] = Json.encodeToString(listOf(legacy))
            val repository = LocalFirstDraftRepository(storage, Json, session, config)

            config.updateBackendUrl(secondOrigin)
            assertTrue(repository.getAllDrafts().isEmpty())
            assertEquals(null, storage.values["editor_drafts_legacy_owner"])

            config.updateBackendUrl(firstOrigin)
            runCurrent()
            assertEquals(listOf(legacy), repository.getAllDrafts())
        }
}

private class StringStorage : KeyValueStorage {
    val values = mutableMapOf<String, String>()
    private val stringFlows = mutableMapOf<String, MutableStateFlow<String?>>()
    var failNextScopedWrite = false
    var failNextScopedRead = false
    var onPut: ((String) -> Unit)? = null

    override suspend fun getString(key: String): String? {
        if (failNextScopedRead && key.startsWith("editor_drafts_scope_")) {
            failNextScopedRead = false
            error("Simulated storage read failure")
        }
        return values[key]
    }

    override fun getStringSync(key: String): String? = values[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        if (failNextScopedWrite && key.startsWith("editor_drafts_scope_")) {
            failNextScopedWrite = false
            error("Simulated storage write failure")
        }
        values[key] = value
        onPut?.invoke(key)
        stringFlows[key]?.value = value
    }

    override suspend fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = defaultValue

    override suspend fun putBoolean(
        key: String,
        value: Boolean,
    ) = Unit

    override suspend fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = defaultValue

    override suspend fun putInt(
        key: String,
        value: Int,
    ) = Unit

    override suspend fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = defaultValue

    override suspend fun putLong(
        key: String,
        value: Long,
    ) = Unit

    override suspend fun getFloat(
        key: String,
        defaultValue: Float,
    ): Float = defaultValue

    override suspend fun putFloat(
        key: String,
        value: Float,
    ) = Unit

    override suspend fun remove(key: String) {
        values.remove(key)
        stringFlows[key]?.value = null
    }

    override suspend fun contains(key: String): Boolean = key in values

    override suspend fun clear() {
        values.clear()
        stringFlows.values.forEach { it.value = null }
    }

    override fun observeString(key: String): Flow<String?> = stringFlows.getOrPut(key) { MutableStateFlow(values[key]) }

    override fun observeBoolean(
        key: String,
        defaultValue: Boolean,
    ): Flow<Boolean> = flowOf(defaultValue)

    override fun observeInt(
        key: String,
        defaultValue: Int,
    ): Flow<Int> = flowOf(defaultValue)

    override fun observeLong(
        key: String,
        defaultValue: Long,
    ): Flow<Long> = flowOf(defaultValue)

    override fun observeFloat(
        key: String,
        defaultValue: Float,
    ): Flow<Float> = flowOf(defaultValue)
}

private class StringSecureStorage : SecureStorage {
    private val values = mutableMapOf<String, String>()

    override suspend fun getString(key: String): String? = values[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }

    override suspend fun clear() {
        values.clear()
    }

    override fun observeString(key: String): Flow<String?> = flowOf(values[key])

    override fun observeAll(): Flow<Map<String, String>> = flowOf(values.toMap())

    override suspend fun encrypt(data: ByteArray): ByteArray = data

    override suspend fun decrypt(data: ByteArray): ByteArray = data
}
