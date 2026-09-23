package app.logdate.feature.core.settings.account.move

import app.logdate.client.data.account.ServerScopedAccount
import app.logdate.client.data.account.ServerScopedAccounts
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.OriginSessionVault
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.domain.account.EnqueueAllLocalDataUseCase
import app.logdate.client.repository.account.AccountCreationRequest
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.feature.core.settings.ui.CheckedServer
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyInfo
import app.logdate.shared.model.ServerDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultServerMoverTest {
    private val serverA = "https://cloud.logdate.app"
    private val serverB = "https://journal.example.com"
    private val owner = Uuid.random().toString()
    private val sessionA = UserSession(accessToken = "access-a", refreshToken = "refresh-a", accountId = owner)
    private val sessionB = UserSession(accessToken = "access-b", refreshToken = "refresh-b", accountId = owner)
    private val destination = CheckedServer(origin = serverB, descriptor = descriptor(serverB), version = "1.4.0")
    private val survey = MoveSurvey(entries = 3, journals = 1, media = 1, drafts = 1, remoteOnlyMedia = 0)

    @Test
    fun `moving switches servers and queues everything while sync is paused`() =
        runTest {
            val world = World(backgroundScope)

            val record = world.mover.commit(destination, world.accountOn(serverB, sessionB), survey).getOrThrow()

            assertEquals(serverB, world.config.getCurrentBackendUrl())
            assertEquals(sessionB, world.sessions.getSession())
            assertEquals(sessionB, world.vault.sessions[serverB])
            assertEquals(sessionA, world.vault.sessions[serverA], "The old server's sign-in stays until its account is deleted")
            assertEquals(listOf(true), world.queuedWhilePaused)
            assertEquals(ServerMoveRecord.Phase.UPLOADING, record.phase)
            assertEquals(6, record.uploadTotal)
            assertEquals(record, world.store.load())
            assertTrue(world.sync.syncRequested)
        }

    @Test
    fun `media references are tied to the old server before switching`() =
        runTest {
            val world = World(backgroundScope)

            world.mover.commit(destination, world.accountOn(serverB, sessionB), survey).getOrThrow()

            assertEquals(listOf(serverA), world.media.claimedFor)
        }

    @Test
    fun `a move that cannot queue the journal switches back and forgets the move`() =
        runTest {
            val world = World(backgroundScope, enqueueResult = Result.failure(IllegalStateException("Database locked")))

            val result = world.mover.commit(destination, world.accountOn(serverB, sessionB), survey)

            assertTrue(result.isFailure)
            assertEquals(serverA, world.config.getCurrentBackendUrl())
            assertEquals(sessionA, world.sessions.getSession())
            assertNull(world.store.load())
            assertFalse(world.sync.syncRequested)
        }

    @Test
    fun `a move interrupted while switching is finished on resume`() =
        runTest {
            val world = World(backgroundScope)
            world.vault.sessions[serverB] = sessionB
            val interrupted =
                ServerMoveRecord(
                    from = MoveEndpoint(serverA, descriptor(serverA)),
                    to = MoveEndpoint(serverB, descriptor(serverB)),
                    phase = ServerMoveRecord.Phase.SWITCHING,
                )

            val record = world.mover.resume(interrupted).getOrThrow()

            assertEquals(serverB, world.config.getCurrentBackendUrl())
            assertEquals(ServerMoveRecord.Phase.UPLOADING, record.phase)
        }

    @Test
    fun `deleting the old account uses its own sign-in and then forgets it`() =
        runTest {
            val world = World(backgroundScope)
            val record = world.mover.commit(destination, world.accountOn(serverB, sessionB), survey).getOrThrow()

            val outcome = world.mover.deleteSource(record)

            assertEquals(SourceDeletion.DELETED, outcome)
            assertEquals(listOf(serverA), world.accounts.deletedOn)
            assertNull(world.vault.sessions[serverA])
            assertEquals(sessionB, world.vault.sessions[serverB])
            assertNull(world.store.load())
        }

    @Test
    fun `an old account without a sign-in asks to sign in there first`() =
        runTest {
            val world = World(backgroundScope)
            val record = world.mover.commit(destination, world.accountOn(serverB, sessionB), survey).getOrThrow()
            world.vault.sessions.remove(serverA)

            assertEquals(SourceDeletion.NEEDS_SIGN_IN, world.mover.deleteSource(record))
            assertTrue(world.accounts.deletedOn.isEmpty())
        }

    private inner class World(
        scope: CoroutineScope,
        enqueueResult: Result<EnqueueAllLocalDataUseCase.Counts> =
            Result.success(EnqueueAllLocalDataUseCase.Counts(journals = 1, notes = 3, associations = 1, drafts = 1)),
    ) {
        val config = DefaultLogDateConfigRepository(initialBackendUrl = serverA)
        val vault = MapVault().apply { sessions[serverA] = sessionA }
        val sessions = ConfigBoundSessions(config, vault, scope)
        val sync = PausingSyncManager()
        val media = RecordingMediaRefs()
        val store = ServerMoveStore(StringStorage())
        val accounts = FakeScopedAccounts(vault)
        val queuedWhilePaused = mutableListOf<Boolean>()
        val mover =
            DefaultServerMover(
                configRepository = config,
                scopedAccounts = accounts,
                vault = vault,
                sessionStorage = sessions,
                accountRepository = FakeAccountRepository(),
                syncManager = sync,
                mediaSyncRefStore = media,
                enqueueAllLocalData = {
                    queuedWhilePaused += sync.paused
                    enqueueResult
                },
                localDataSurvey = { survey },
                moveStore = store,
            )

        suspend fun accountOn(
            origin: String,
            session: UserSession,
        ): ServerScopedAccount {
            vault.sessions[origin] = session
            return accounts.open(origin, descriptor(origin))
        }
    }

    private fun descriptor(origin: String) =
        ServerDescriptor(
            serverOrigin = origin,
            apiBaseUrl = "$origin/api/v1",
            deploymentKind = DeploymentKind.SELF_HOSTED,
            displayName = origin.substringAfter("://"),
        )

    private class MapVault : OriginSessionVault {
        val sessions = mutableMapOf<String, UserSession>()

        override suspend fun read(origin: String): UserSession? = sessions[origin]

        override suspend fun write(
            origin: String,
            session: UserSession,
        ) {
            sessions[origin] = session
        }

        override suspend fun clear(origin: String) {
            sessions.remove(origin)
        }
    }

    /** Reloads the saved sign-in whenever the connected server changes, as the app's storage does. */
    private class ConfigBoundSessions(
        config: DefaultLogDateConfigRepository,
        vault: MapVault,
        scope: CoroutineScope,
    ) : SessionStorage {
        private val state = MutableStateFlow<UserSession?>(null)

        init {
            scope.launch(UnconfinedTestDispatcher()) { config.backendUrl.collect { state.value = vault.read(it) } }
        }

        override fun getSession(): UserSession? = state.value

        override fun getSessionFlow(): StateFlow<UserSession?> = state

        override suspend fun hasValidSession(): Boolean = state.value != null

        override fun saveSession(session: UserSession) {
            state.value = session
        }

        override fun clearSession() {
            state.value = null
        }
    }

    private class PausingSyncManager : SyncManager {
        var paused = false
        var syncRequested = false

        override suspend fun <T> whilePaused(block: suspend () -> T): T {
            paused = true
            return try {
                block()
            } finally {
                paused = false
            }
        }

        override fun sync(startNow: Boolean) {
            syncRequested = true
        }

        override val syncStatusFlow: StateFlow<SyncStatus> =
            MutableStateFlow(SyncStatus(isEnabled = true, lastSyncTime = null, pendingUploads = 0, isSyncing = false, hasErrors = false))

        override suspend fun uploadPendingChanges(): SyncResult = SyncResult(success = true)

        override suspend fun downloadRemoteChanges(): SyncResult = SyncResult(success = true)

        override suspend fun syncContent(): SyncResult = SyncResult(success = true)

        override suspend fun syncJournals(): SyncResult = SyncResult(success = true)

        override suspend fun syncAssociations(): SyncResult = SyncResult(success = true)

        override suspend fun syncDrafts(): SyncResult = SyncResult(success = true)

        override suspend fun fullSync(): SyncResult = SyncResult(success = true)

        override suspend fun getSyncStatus(): SyncStatus = syncStatusFlow.value

        override fun observeDeadLetters(): Flow<List<SyncDeadLetterRecord>> = flowOf(emptyList())

        override suspend fun retryDeadLetter(id: String) {}

        override suspend fun discardDeadLetter(id: String) {}
    }

    private class RecordingMediaRefs : MediaSyncRefStore {
        val claimedFor = mutableListOf<String>()

        override suspend fun get(noteId: Uuid): MediaSyncRef? = null

        override suspend fun upsert(ref: MediaSyncRef) {}

        override suspend fun delete(noteId: Uuid) {}

        override suspend fun claimUnscopedRefs(origin: String) {
            claimedFor += origin
        }
    }

    private class FakeScopedAccounts(
        private val vault: MapVault,
    ) : ServerScopedAccounts {
        val deletedOn = mutableListOf<String>()

        override suspend fun open(
            origin: String,
            descriptor: ServerDescriptor,
        ): ServerScopedAccount {
            val repository =
                object : FakeAccountRepository() {
                    override suspend fun deleteAccount(): Result<Unit> {
                        deletedOn += origin
                        return Result.success(Unit)
                    }
                }
            return object : ServerScopedAccount {
                override val origin: String = origin
                override val repository: PasskeyAccountRepository = repository

                override fun session(): UserSession? = vault.sessions[origin]

                override fun close() = Unit
            }
        }
    }

    private open class FakeAccountRepository : PasskeyAccountRepository {
        override val currentAccount: StateFlow<LogDateAccount?> = MutableStateFlow(null)
        override val isAuthenticated: StateFlow<Boolean> = MutableStateFlow(true)

        override suspend fun createAccountWithPasskey(request: AccountCreationRequest): Result<LogDateAccount> =
            Result.failure(NotImplementedError())

        override suspend fun authenticateWithPasskey(
            username: String?,
            adoptLocalData: Boolean,
        ): Result<LogDateAccount> = Result.failure(NotImplementedError())

        override suspend fun checkUsernameAvailability(username: String): Result<Boolean> = Result.success(true)

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)

        override suspend fun getCurrentAccount(): LogDateAccount? = null

        override suspend fun getAccountInfo(): Result<LogDateAccount> = Result.failure(NotImplementedError())

        override suspend fun refreshAuthentication(): Result<Unit> = Result.success(Unit)

        override suspend fun listPasskeys(): Result<List<PasskeyInfo>> = Result.success(emptyList())

        override suspend fun deletePasskey(credentialId: String): Result<Unit> = Result.success(Unit)

        override suspend fun createRestoreKey(): Result<Unit> = Result.success(Unit)

        override suspend fun signInWithRestoreKey(): Result<LogDateAccount> = Result.failure(NotImplementedError())

        override suspend fun deleteRestoreKey(): Result<Unit> = Result.success(Unit)
    }

    private class StringStorage : KeyValueStorage {
        private val strings = mutableMapOf<String, String>()

        override suspend fun getString(key: String): String? = strings[key]

        override fun getStringSync(key: String): String? = strings[key]

        override suspend fun putString(
            key: String,
            value: String,
        ) {
            strings[key] = value
        }

        override suspend fun remove(key: String) {
            strings.remove(key)
        }

        override suspend fun contains(key: String): Boolean = key in strings

        override suspend fun clear() = strings.clear()

        override fun observeString(key: String): Flow<String?> = MutableStateFlow(strings[key])

        override suspend fun getBoolean(
            key: String,
            defaultValue: Boolean,
        ): Boolean = error("unused")

        override suspend fun putBoolean(
            key: String,
            value: Boolean,
        ) = error("unused")

        override suspend fun getInt(
            key: String,
            defaultValue: Int,
        ): Int = error("unused")

        override suspend fun putInt(
            key: String,
            value: Int,
        ) = error("unused")

        override suspend fun getLong(
            key: String,
            defaultValue: Long,
        ): Long = error("unused")

        override suspend fun putLong(
            key: String,
            value: Long,
        ) = error("unused")

        override suspend fun getFloat(
            key: String,
            defaultValue: Float,
        ): Float = error("unused")

        override suspend fun putFloat(
            key: String,
            value: Float,
        ) = error("unused")

        override fun observeBoolean(
            key: String,
            defaultValue: Boolean,
        ): Flow<Boolean> = error("unused")

        override fun observeInt(
            key: String,
            defaultValue: Int,
        ): Flow<Int> = error("unused")

        override fun observeLong(
            key: String,
            defaultValue: Long,
        ): Flow<Long> = error("unused")

        override fun observeFloat(
            key: String,
            defaultValue: Float,
        ): Flow<Float> = error("unused")
    }
}
