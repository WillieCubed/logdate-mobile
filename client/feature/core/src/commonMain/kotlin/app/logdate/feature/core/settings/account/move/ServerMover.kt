package app.logdate.feature.core.settings.account.move

import app.logdate.client.data.account.ServerScopedAccount
import app.logdate.client.data.account.ServerScopedAccounts
import app.logdate.client.datastore.OriginSessionVault
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.domain.account.EnqueueAllLocalDataUseCase
import app.logdate.client.networking.PasskeyApiErrorCodes
import app.logdate.client.networking.PasskeyApiException
import app.logdate.client.repository.account.AccountCreationRequest
import app.logdate.client.repository.account.NotSignedInException
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.feature.core.settings.ui.CheckedServer
import app.logdate.shared.config.LogDateConfigRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.withTimeout
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** How far the upload to the new server has got. */
data class MoveProgress(
    val remaining: Int,
    /** Items the new server refused for good since the switch. */
    val failed: Int,
    val isSyncing: Boolean,
    /**
     * Whether a sync has run to the end while the move is being watched. Right after the switch nothing is pending yet
     * either, so an empty queue only means "done" once a sync has run.
     */
    val syncedSinceSwitch: Boolean,
)

/** What happened when deleting the account on the old server. */
enum class SourceDeletion {
    DELETED,

    /** The old server's sign-in expired; signing in there again is needed first. */
    NEEDS_SIGN_IN,
    OFFLINE,
    FAILED,
}

/**
 * Moves the signed-in account from the connected server to another one.
 *
 * The move signs in or creates the account on the new server while the app is still connected to
 * the old one, so nothing changes until the new account exists. Then, with sync paused, it saves
 * the new sign-in, switches servers, waits for the app to pick up the new sign-in and queues
 * everything on this device for upload. If queueing fails it switches back. The old server keeps
 * its account and sign-in until the person deletes it.
 */
interface ServerMove {
    /** The connected server, which the account would move from. */
    fun source(): MoveEndpoint

    suspend fun survey(): MoveSurvey

    /** The move under way, if the app was closed during one. */
    suspend fun inProgress(): ServerMoveRecord?

    /** Opens the account on [server] without switching to it. */
    suspend fun openDestination(server: CheckedServer): ServerScopedAccount

    /** Whether [account] already has a working sign-in, from an earlier move to the same server. */
    suspend fun hasWorkingSignIn(account: ServerScopedAccount): Boolean

    suspend fun createAccount(
        account: ServerScopedAccount,
        username: String,
        displayName: String,
    ): Result<Unit>

    suspend fun signIn(account: ServerScopedAccount): Result<Unit>

    /** Switches to [destination] and queues this device's journal for it. */
    suspend fun commit(
        destination: CheckedServer,
        account: ServerScopedAccount,
        survey: MoveSurvey,
    ): Result<ServerMoveRecord>

    /** Finishes a switch the app was closed in the middle of. */
    suspend fun resume(record: ServerMoveRecord): Result<ServerMoveRecord>

    fun progress(record: ServerMoveRecord): Flow<MoveProgress>

    suspend fun deleteSource(record: ServerMoveRecord): SourceDeletion

    suspend fun signInToSourceAndDelete(record: ServerMoveRecord): SourceDeletion

    /** Ends the move, leaving the old server's account as it is. */
    suspend fun finish()
}

class DefaultServerMover(
    private val configRepository: LogDateConfigRepository,
    private val scopedAccounts: ServerScopedAccounts,
    private val vault: OriginSessionVault,
    private val sessionStorage: SessionStorage,
    private val accountRepository: PasskeyAccountRepository,
    private val syncManager: SyncManager,
    private val mediaSyncRefStore: MediaSyncRefStore,
    /** Queues everything on this device for the connected server; see [EnqueueAllLocalDataUseCase]. */
    private val enqueueAllLocalData: suspend () -> Result<EnqueueAllLocalDataUseCase.Counts>,
    private val localDataSurvey: suspend () -> MoveSurvey,
    private val moveStore: ServerMoveStore,
    private val clock: Clock = Clock.System,
) : ServerMove {
    override fun source(): MoveEndpoint =
        MoveEndpoint(configRepository.getCurrentBackendUrl(), configRepository.getCurrentServerDescriptor())

    override suspend fun survey(): MoveSurvey = localDataSurvey()

    override suspend fun inProgress(): ServerMoveRecord? = moveStore.load()

    override suspend fun openDestination(server: CheckedServer): ServerScopedAccount = scopedAccounts.open(server.origin, server.descriptor)

    override suspend fun hasWorkingSignIn(account: ServerScopedAccount): Boolean =
        account.session() != null && account.repository.getAccountInfo().isSuccess

    override suspend fun createAccount(
        account: ServerScopedAccount,
        username: String,
        displayName: String,
    ): Result<Unit> =
        account.repository.createAccountWithPasskey(AccountCreationRequest(username = username, displayName = displayName)).map {}

    override suspend fun signIn(account: ServerScopedAccount): Result<Unit> =
        account.repository.authenticateWithPasskey(adoptLocalData = true).map {}

    override suspend fun commit(
        destination: CheckedServer,
        account: ServerScopedAccount,
        survey: MoveSurvey,
    ): Result<ServerMoveRecord> {
        val session = account.session() ?: return Result.failure(NotSignedInException())
        val record =
            ServerMoveRecord(
                from = source(),
                to = MoveEndpoint(destination.origin, destination.descriptor),
                phase = ServerMoveRecord.Phase.SWITCHING,
                remoteOnlyMedia = survey.remoteOnlyMedia,
            )
        vault.write(destination.origin, session)
        moveStore.save(record)
        return switchAndQueue(record)
    }

    /**
     * An upload is only trusted when the app is on the new server. The server address is saved in
     * the background, so an app closed right after the switch can come back on the old server with
     * the move already marked as uploading; that switch is redone.
     */
    override suspend fun resume(record: ServerMoveRecord): Result<ServerMoveRecord> =
        if (record.phase == ServerMoveRecord.Phase.UPLOADING && isConnectedTo(record.to)) {
            Result.success(record)
        } else {
            switchAndQueue(record)
        }

    private fun isConnectedTo(endpoint: MoveEndpoint): Boolean =
        configRepository.getCurrentBackendUrl().trimEnd('/') == endpoint.origin.trimEnd('/')

    private suspend fun switchAndQueue(record: ServerMoveRecord): Result<ServerMoveRecord> {
        val result =
            syncManager.whilePaused {
                runCatching {
                    mediaSyncRefStore.claimUnscopedRefs(record.from.origin)
                    switchTo(record.to)
                    val counts = enqueueAllLocalData().getOrThrow()
                    record
                        .copy(
                            phase = ServerMoveRecord.Phase.UPLOADING,
                            uploadTotal = counts.journals + counts.notes + counts.associations + counts.drafts,
                            committedAtMillis = clock.now().toEpochMilliseconds(),
                        ).also { moveStore.save(it) }
                }.onFailure { error ->
                    Napier.e("Moving to ${record.to.origin} failed; switching back to ${record.from.origin}", error)
                    runCatching { switchTo(record.from) }.onFailure { Napier.e("Could not switch back to ${record.from.origin}", it) }
                    moveStore.clear()
                }
            }
        if (result.isSuccess) {
            accountRepository.getAccountInfo().onFailure { Napier.w("Could not load the account after moving", it) }
            accountRepository.createRestoreKey().onFailure { Napier.w("Could not create a restore key after moving", it) }
            syncManager.sync(startNow = true)
        }
        return result
    }

    /**
     * Points the app at [endpoint] and waits until its sign-in is the one saved for that server, so
     * nothing runs with one server's sign-in against the other.
     */
    private suspend fun switchTo(endpoint: MoveEndpoint) {
        val expected = vault.read(endpoint.origin)
        configRepository.updateBackendUrl(endpoint.origin)
        configRepository.updateServerDescriptor(endpoint.descriptor)
        withTimeout(SESSION_SWITCH_TIMEOUT) { sessionStorage.getSessionFlow().first { it == expected } }
    }

    /**
     * A sync counts as finished once one has been seen running and then stopping. The server's
     * last-sync time can't answer that: it comes from the server's clock and doesn't move when
     * there's nothing to download.
     */
    override fun progress(record: ServerMoveRecord): Flow<MoveProgress> {
        val syncFinished =
            syncManager.syncStatusFlow
                .runningFold(SyncRun()) { run, status ->
                    SyncRun(started = run.started || status.isSyncing, finished = run.finished || (run.started && !status.isSyncing))
                }.onStart { syncManager.sync(startNow = true) }
        return combine(syncManager.syncStatusFlow, syncManager.observeDeadLetters(), syncFinished) { status, deadLetters, run ->
            MoveProgress(
                remaining = status.pendingUploads,
                failed = deadLetters.count { it.failedAt >= record.committedAtMillis },
                isSyncing = status.isSyncing,
                syncedSinceSwitch = run.finished,
            )
        }
    }

    private data class SyncRun(
        val started: Boolean = false,
        val finished: Boolean = false,
    )

    override suspend fun deleteSource(record: ServerMoveRecord): SourceDeletion =
        withSourceAccount(record) { account ->
            if (account.session() == null) return@withSourceAccount SourceDeletion.NEEDS_SIGN_IN
            // Loads the account so its platform entry can be removed along with it.
            account.repository.getAccountInfo()
            delete(record, account)
        }

    override suspend fun signInToSourceAndDelete(record: ServerMoveRecord): SourceDeletion =
        withSourceAccount(record) { account ->
            account.repository
                .authenticateWithPasskey(adoptLocalData = true)
                .fold(onSuccess = { delete(record, account) }, onFailure = { deletionFailure(it) })
        }

    override suspend fun finish() = moveStore.clear()

    private suspend fun delete(
        record: ServerMoveRecord,
        account: ServerScopedAccount,
    ): SourceDeletion =
        account.repository.deleteAccount().fold(
            onSuccess = {
                vault.clear(record.from.origin)
                moveStore.clear()
                SourceDeletion.DELETED
            },
            onFailure = { deletionFailure(it) },
        )

    private fun deletionFailure(error: Throwable): SourceDeletion {
        Napier.w("Deleting the account on the old server failed", error)
        return when {
            error is NotSignedInException -> SourceDeletion.NEEDS_SIGN_IN
            (error as? PasskeyApiException)?.errorCode == PasskeyApiErrorCodes.NETWORK_ERROR -> SourceDeletion.OFFLINE
            else -> SourceDeletion.FAILED
        }
    }

    private suspend fun <T> withSourceAccount(
        record: ServerMoveRecord,
        block: suspend (ServerScopedAccount) -> T,
    ): T {
        val account = scopedAccounts.open(record.from.origin, record.from.descriptor)
        return try {
            block(account)
        } finally {
            account.close()
        }
    }

    private companion object {
        val SESSION_SWITCH_TIMEOUT = 10.seconds
    }
}
