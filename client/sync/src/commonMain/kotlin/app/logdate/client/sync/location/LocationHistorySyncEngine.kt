package app.logdate.client.sync.location

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.networking.LocationHistoryApiClientContract
import app.logdate.client.networking.LocationHistoryTransportException
import app.logdate.client.repository.location.HistoryRecord
import app.logdate.client.repository.location.HistoryRecordStore
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.space.SpaceSync
import app.logdate.client.sync.space.mappers.HistoryScope
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.config.PinnedLogDateConfigRepository
import app.logdate.shared.model.sync.LocationHistoryUpload
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Each run pins its account and server; no page cursor advances until the entire page decrypts. */
class LocationHistorySyncEngine(
    private val apiFactory: (LogDateConfigRepository) -> LocationHistoryApiClientContract,
    private val store: HistoryRecordStore,
    private val cipher: SyncPayloadCipher,
    private val sessions: SessionStorage,
    private val config: LogDateConfigRepository,
    private val enabled: suspend () -> Boolean,
    private val prepare: suspend (ownerId: String, origin: String) -> Boolean = { _, _ -> false },
    private val savedPlaceIds: suspend () -> List<String> = { emptyList() },
    private val canonicalOwner: CanonicalOwnerProvider? = null,
) {
    private val localScopes = canonicalOwner?.let { CanonicalHistoryScopeProvider(sessions, config, it) }

    /** The part of this engine that syncs through the personal space, for the manager that owns [space]. */
    internal fun forSpace(
        space: SpaceSync,
        metadata: SyncMetadataService,
        transactions: SyncTransactionManager,
        inbox: DownloadInbox?,
        uploadsUpdates: Boolean,
    ): LocationHistorySpaceSync = LocationHistorySpaceSync(space, store, metadata, transactions, inbox, uploadsUpdates, savedPlaceIds)

    /** Stages the samples captured here, then uploads what changed to the personal space instead of the location history endpoints. */
    internal suspend fun uploadToSpace(
        accessToken: String,
        spaceSync: LocationHistorySpaceSync,
    ): SyncResult =
        runPhase(accessToken) { namespace, _ ->
            val moreToStage = prepare(namespace.owner, namespace.origin)
            ensureCurrent(namespace)
            spaceSync.upload(namespace.owner, namespace.origin, moreToStage).also { ensureCurrent(namespace) }
        }

    internal suspend fun downloadFromSpace(
        accessToken: String,
        spaceSync: LocationHistorySpaceSync,
    ): SyncResult =
        runPhase(accessToken) { namespace, _ ->
            ensureCurrent(namespace)
            spaceSync.download().also { ensureCurrent(namespace) }
        }

    suspend fun upload(accessToken: String): SyncResult =
        runPhase(accessToken) { namespace, api ->
            val moreToStage = prepare(namespace.owner, namespace.origin)
            var uploaded = 0
            repeat(MAX_PAGES_PER_RUN) {
                ensureCurrent(namespace)
                val pending = store.pending(namespace.owner, namespace.origin, PAGE_SIZE)
                if (pending.isEmpty()) return@runPhase SyncResult(success = true, uploadedItems = uploaded, hasMorePending = moreToStage)
                val uploads =
                    pending.map { record ->
                        LocationHistoryUpload(
                            id = record.id,
                            recordType = record.recordType,
                            payload =
                                if (record.deleted) {
                                    null
                                } else {
                                    cipher.encryptString(
                                        namespace.fieldId(record.id, record.recordType),
                                        requireNotNull(record.payload),
                                    )
                                },
                            deviceId = record.deviceId,
                            deviceVersion = record.deviceVersion,
                            expectedServerVersion = record.serverVersion,
                            deleted = record.deleted,
                        )
                    }
                ensureCurrent(namespace)
                val accepted = api.upload(accessToken, uploads)
                ensureCurrent(namespace)
                check(accepted.size == uploads.size && accepted.map { it.id }.toSet() == uploads.map { it.id }.toSet())
                accepted.forEach { record ->
                    val sent = uploads.single { it.id == record.id }
                    check(
                        record.deviceId == sent.deviceId &&
                            record.deviceVersion == sent.deviceVersion &&
                            record.deleted == sent.deleted &&
                            record.recordType == sent.recordType &&
                            record.serverVersion > 0,
                    )
                    store.acknowledge(namespace.owner, namespace.origin, record.id, sent.deviceVersion, record.serverVersion)
                }
                uploaded += accepted.size
            }
            SyncResult(
                success = true,
                uploadedItems = uploaded,
                hasMorePending =
                    moreToStage ||
                        store.pending(namespace.owner, namespace.origin, 1).isNotEmpty(),
            )
        }

    suspend fun download(accessToken: String): SyncResult =
        runPhase(accessToken) { namespace, api ->
            var downloaded = 0
            var cursor = store.cursor(namespace.owner, namespace.origin)
            repeat(MAX_PAGES_PER_RUN) {
                ensureCurrent(namespace)
                val page = api.changes(accessToken, cursor, PAGE_SIZE)
                ensureCurrent(namespace)
                check(page.nextCursor >= cursor && page.records.size <= PAGE_SIZE)
                check(!page.hasMore || page.nextCursor > cursor)
                val decoded =
                    page.records.map { record ->
                        check(record.payloadSchemaVersion == 1 && record.serverVersion > cursor && record.serverVersion <= page.nextCursor)
                        val payload =
                            if (record.deleted) {
                                check(record.payload == null)
                                null
                            } else {
                                val ciphertext = requireNotNull(record.payload)
                                check(ciphertext.startsWith("LDSE2:"))
                                cipher.decryptString(namespace.fieldId(record.id, record.recordType), ciphertext)
                            }
                        HistoryRecord(
                            record.id,
                            record.recordType,
                            payload,
                            record.deviceId,
                            record.deviceVersion,
                            record.serverVersion,
                            record.deleted,
                            dirty = false,
                        )
                    }
                ensureCurrent(namespace)
                store.applyPage(namespace.owner, namespace.origin, decoded, page.nextCursor)
                cursor = page.nextCursor
                downloaded += decoded.size
                if (!page.hasMore) return@runPhase SyncResult(success = true, downloadedItems = downloaded)
            }
            SyncResult(success = true, downloadedItems = downloaded, hasMorePending = true)
        }

    suspend fun isEnabled(): Boolean = enabled()

    /**
     * Every location history record this device keeps for the signed-in account, deleted ones
     * included, and the saved places, for moving the journal into its personal space. Null while
     * location history doesn't sync on this device.
     */
    internal suspend fun recordsForMove(): MovedHistory? {
        if (!enabled()) return null
        val session = sessions.getSession() ?: return null
        val namespace = captureNamespace(session.accessToken)
        return MovedHistory(
            HistoryScope(namespace.owner, namespace.origin),
            store.records(namespace.owner, namespace.origin),
            savedPlaceIds(),
        )
    }

    /**
     * Gives every record this device keeps the version legacy storage holds it at, or 0 when legacy
     * storage doesn't have it, after the account moved back to legacy storage. Legacy storage refuses a
     * write whose expected version isn't the one it holds, and the personal space numbered versions
     * differently.
     */
    internal suspend fun adoptLegacyVersions(accessToken: String): SyncResult =
        runPhase(accessToken) { namespace, api ->
            val versions = mutableMapOf<String, Long>()
            var cursor = 0L
            do {
                ensureCurrent(namespace)
                val page = api.changes(accessToken, cursor, PAGE_SIZE)
                page.records.forEach { versions[it.id] = it.serverVersion }
                check(!page.hasMore || page.nextCursor > cursor)
                cursor = page.nextCursor
            } while (page.hasMore)
            val adjusted =
                store.records(namespace.owner, namespace.origin).mapNotNull { record ->
                    val version = versions[record.id] ?: 0L
                    record.takeIf { it.serverVersion != version }?.copy(serverVersion = version)
                }
            if (adjusted.isNotEmpty()) store.putBatch(namespace.owner, namespace.origin, adjusted)
            SyncResult(success = true)
        }

    /**
     * Queues every record this device keeps, deletions included, for the location history endpoints
     * again, after the journal was switched back to legacy storage.
     */
    internal suspend fun requeueForLegacy() {
        val moved = recordsForMove() ?: return
        if (moved.records.isEmpty()) return
        val requeued = moved.records.map { it.copy(deviceVersion = it.deviceVersion + 1, dirty = true) }
        store.putBatch(moved.scope.owner, moved.scope.origin, requeued)
    }

    /** Whether local location records are waiting to upload for the signed-in account. */
    suspend fun hasPendingUploads(accessToken: String): Boolean {
        if (!isEnabled()) return false
        val namespace = captureNamespace(accessToken)
        return store.pending(namespace.owner, namespace.origin, 1).isNotEmpty()
    }

    /** Checked before minting an identity key on a fresh device, including location-only accounts. */
    suspend fun hasRemoteRecords(accessToken: String): Boolean {
        val namespace = captureNamespace(accessToken)
        // Recovery must protect existing ciphertext even when this device has not enabled history.
        val hasRecords =
            try {
                pinnedApi(namespace).changes(accessToken, 0, 1).records.isNotEmpty()
            } catch (error: LocationHistoryTransportException) {
                // Older servers cannot contain this record type. Other failures leave the account unknown.
                if (error.statusCode !in setOf(404, 405, 501)) throw error
                false
            }
        ensureCurrent(namespace)
        return hasRecords
    }

    private suspend fun runPhase(
        accessToken: String,
        block: suspend (Namespace, LocationHistoryApiClientContract) -> SyncResult,
    ): SyncResult {
        if (!enabled()) return SyncResult(success = true)
        return try {
            val namespace = captureNamespace(accessToken)
            withContext(HistorySyncBinding(HistoryScope(namespace.owner, namespace.origin), namespace.session)) {
                block(namespace, pinnedApi(namespace))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            app.logdate.client.sync
                .throwIfDeviceBlocked(error)
            val type =
                when ((error as? LocationHistoryTransportException)?.statusCode) {
                    401, 403 -> SyncErrorType.AUTHENTICATION_ERROR
                    409 -> SyncErrorType.CONFLICT_ERROR
                    else -> SyncErrorType.UNKNOWN_ERROR
                }
            // Do not include ciphertext or decoded records in error messages or logs.
            Napier.e("Location history could not sync: ${error::class.simpleName}")
            SyncResult(
                success = false,
                errors =
                    listOf(
                        SyncError(
                            type,
                            "Location history could not sync. Retry after checking your account and connection.",
                            cause = error,
                        ),
                    ),
            )
        }
    }

    private suspend fun captureNamespace(accessToken: String): Namespace {
        val session = requireNotNull(sessions.getSession())
        check(session.accessToken == accessToken)
        val origin = config.getCurrentBackendUrl().trimEnd('/')
        val local = localScopes?.let { requireNotNull(it.current()) { "The retained history owner is unavailable" } }
        check(sessions.getSession() == session && config.getCurrentBackendUrl().trimEnd('/') == origin)
        return Namespace(local?.owner ?: session.accountId, origin, session)
    }

    private suspend fun pinnedApi(namespace: Namespace): LocationHistoryApiClientContract {
        ensureCurrent(namespace)
        return apiFactory(PinnedLogDateConfigRepository(namespace.origin, config.getCurrentServerDescriptor()))
    }

    private suspend fun ensureCurrent(namespace: Namespace) {
        check(sessions.getSession() == namespace.session && config.getCurrentBackendUrl().trimEnd('/') == namespace.origin)
        localScopes?.let { check(it.current() == HistoryScope(namespace.owner, namespace.origin)) }
    }

    private data class Namespace(
        val owner: String,
        val origin: String,
        val session: UserSession,
    ) {
        fun fieldId(
            id: String,
            type: String,
        ): String = "location-history:$origin:${session.accountId}:$type:$id:v1"
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES_PER_RUN = 10
    }
}

/** The location history a device keeps for one account, as the move into a personal space reads it. */
internal class MovedHistory(
    val scope: HistoryScope,
    val records: List<HistoryRecord>,
    val savedPlaceIds: List<String>,
)
