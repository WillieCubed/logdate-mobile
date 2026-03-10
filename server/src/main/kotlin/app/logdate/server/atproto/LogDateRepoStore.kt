@file:OptIn(ExperimentalUuidApi::class)

package app.logdate.server.atproto

import app.logdate.server.database.toJavaUUID
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.logdate.DEFAULT_CONTENT_TYPE
import app.logdate.server.logdate.DEFAULT_DURATION_MS
import app.logdate.server.logdate.LogDateAssociation
import app.logdate.server.logdate.LogDateAssociationRef
import app.logdate.server.logdate.LogDateCollectionKind
import app.logdate.server.logdate.LogDateCollectionsRepository
import app.logdate.server.logdate.LogDateEntry
import app.logdate.server.logdate.LogDateJournal
import app.logdate.server.logdate.associationRecordKey
import app.logdate.server.logdate.deviceIdOrDefault
import app.logdate.server.logdate.entryIdFromAssociationRecordKey
import app.logdate.server.logdate.journalIdFromAssociationRecordKey
import app.logdate.server.logdate.longValue
import app.logdate.server.logdate.nullableStringValue
import app.logdate.server.logdate.requireMatchingId
import app.logdate.server.logdate.requireMatchingType
import app.logdate.server.logdate.stringValue
import kotlinx.serialization.json.JsonObject
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.DefaultRepoEngine
import studio.hypertext.atproto.repo.InvalidSwapException
import studio.hypertext.atproto.repo.RepoBlockStore
import studio.hypertext.atproto.repo.RepoEngine
import studio.hypertext.atproto.repo.RepoExport
import studio.hypertext.atproto.repo.RepoHead
import studio.hypertext.atproto.repo.RepoListPage
import studio.hypertext.atproto.repo.RepoRecord
import studio.hypertext.atproto.repo.RepoRecordId
import studio.hypertext.atproto.repo.RepoValidationStatus
import studio.hypertext.atproto.repo.RepoWriteResult
import studio.hypertext.atproto.repo.SignedRepoCommit
import studio.hypertext.atproto.repo.UnsupportedCollectionException
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Multi-collection LogDate repo store backed by the shared standalone repo engine.
 *
 * LogDate collections remain the internal language of the server. This adapter translates between
 * those collection models and the canonical repo maintained behind [collectionsRepository].
 */
class LogDateRepoStore(
    private val collectionsRepository: LogDateCollectionsRepository,
    private val identityService: AtprotoIdentityService,
    blockStore: RepoBlockStore,
) : RepoEngine {
    private val canonicalEngine = DefaultRepoEngine(blockStore)

    suspend fun collectionsForDid(did: String): List<Nsid> {
        val userId = resolveUserId(AtprotoDid.require(did))
        return supportedCollections
            .filter { definition -> definition.hasRecords(userId, collectionsRepository) }
            .map(LogDateCollectionAdapter::nsid)
    }

    override suspend fun getRecord(recordId: RepoRecordId): Result<RepoRecord?> =
        runCatching {
            requireKnownRepo(recordId.repo)
            canonicalEngine.getRecord(recordId).getOrThrow()
        }

    override suspend fun listRecords(
        repo: AtprotoDid,
        collection: Nsid,
        limit: Int,
        cursor: String?,
        reverse: Boolean,
    ): Result<RepoListPage> =
        runCatching {
            requireKnownRepo(repo)
            requireSupportedCollection(collection)
            canonicalEngine
                .listRecords(
                    repo = repo,
                    collection = collection,
                    limit = limit,
                    cursor = cursor,
                    reverse = reverse,
                ).getOrThrow()
        }

    override suspend fun createRecord(
        repo: AtprotoDid,
        collection: Nsid,
        value: JsonObject,
        recordKey: RecordKey?,
    ): Result<RepoWriteResult> =
        runCatching {
            val definition = requireSupportedCollection(collection)
            val userId = resolveUserId(repo)
            val resolvedRecordKey = recordKey ?: definition.generatedRecordKey(value)
            val recordId = RepoRecordId(repo = repo, collection = collection, recordKey = resolvedRecordKey)
            definition.upsert(collectionsRepository = collectionsRepository, userId = userId, recordId = recordId, value = value)
            persistedWrite(recordId)
        }

    override suspend fun putRecord(
        recordId: RepoRecordId,
        value: JsonObject,
        swapRecord: String?,
    ): Result<RepoWriteResult> =
        runCatching {
            val definition = requireSupportedCollection(recordId.collection)
            val existingCid = canonicalEngine.getRecord(recordId).getOrThrow()?.cid
            if (swapRecord != null && existingCid != swapRecord) {
                throw InvalidSwapException(expectedCid = existingCid, providedCid = swapRecord)
            }
            definition.upsert(
                collectionsRepository = collectionsRepository,
                userId = resolveUserId(recordId.repo),
                recordId = recordId,
                value = value,
            )
            persistedWrite(recordId)
        }

    override suspend fun deleteRecord(
        recordId: RepoRecordId,
        swapRecord: String?,
    ): Result<Boolean> =
        runCatching {
            val userId = resolveUserId(recordId.repo)
            val definition = requireSupportedCollection(recordId.collection)
            val existingCid = canonicalEngine.getRecord(recordId).getOrThrow()?.cid ?: return@runCatching false
            if (swapRecord != null && existingCid != swapRecord) {
                throw InvalidSwapException(expectedCid = existingCid, providedCid = swapRecord)
            }
            definition.delete(
                collectionsRepository = collectionsRepository,
                userId = userId,
                recordId = recordId,
            )
            true
        }

    override suspend fun loadHead(repo: AtprotoDid): Result<RepoHead?> =
        runCatching {
            requireKnownRepo(repo)
            canonicalEngine.loadHead(repo).getOrThrow()
        }

    override suspend fun listCommits(
        repo: AtprotoDid,
        limit: Int,
    ): Result<List<SignedRepoCommit>> =
        runCatching {
            requireKnownRepo(repo)
            canonicalEngine.listCommits(repo, limit).getOrThrow()
        }

    override suspend fun export(repo: AtprotoDid): Result<RepoExport> =
        runCatching {
            requireKnownRepo(repo)
            canonicalEngine.export(repo).getOrThrow()
        }

    override suspend fun import(export: RepoExport): Result<RepoHead> =
        Result.failure(UnsupportedOperationException("LogDate repo adapter does not support repo imports yet"))

    private suspend fun resolveUserId(repo: AtprotoDid): java.util.UUID =
        requireNotNull(identityService.findByDid(repo.toString())) { "Unknown repo DID: $repo" }
            .id
            .toJavaUUID()

    private suspend fun requireKnownRepo(repo: AtprotoDid) {
        resolveUserId(repo)
    }

    private fun requireSupportedCollection(collection: Nsid): LogDateCollectionAdapter =
        supportedCollections.firstOrNull { it.nsid == collection }
            ?: throw UnsupportedCollectionException(collection.toString())

    private suspend fun persistedWrite(recordId: RepoRecordId): RepoWriteResult {
        val persisted = requireNotNull(canonicalEngine.getRecord(recordId).getOrThrow())
        return RepoWriteResult(
            uri = persisted.uri,
            cid = requireNotNull(persisted.cid),
            validationStatus = RepoValidationStatus.UNKNOWN,
        )
    }

    companion object {
        val contentCollection: Nsid = LogDateCollectionKind.ENTRY.nsid
        val journalCollection: Nsid = LogDateCollectionKind.JOURNAL.nsid
        val associationCollection: Nsid = LogDateCollectionKind.ASSOCIATION.nsid

        private val supportedCollections: List<LogDateCollectionAdapter> =
            listOf(
                ContentCollectionAdapter,
                JournalCollectionAdapter,
                AssociationCollectionAdapter,
            )
    }
}

private sealed class LogDateCollectionAdapter(
    val nsid: Nsid,
) {
    abstract fun generatedRecordKey(value: JsonObject): RecordKey

    abstract suspend fun hasRecords(
        userId: java.util.UUID,
        collectionsRepository: LogDateCollectionsRepository,
    ): Boolean

    abstract suspend fun upsert(
        collectionsRepository: LogDateCollectionsRepository,
        userId: java.util.UUID,
        recordId: RepoRecordId,
        value: JsonObject,
    )

    abstract suspend fun delete(
        collectionsRepository: LogDateCollectionsRepository,
        userId: java.util.UUID,
        recordId: RepoRecordId,
    )

    protected fun generatedRecordKey(prefix: String): RecordKey = RecordKey.require("$prefix-${Uuid.random()}")
}

private object ContentCollectionAdapter : LogDateCollectionAdapter(LogDateRepoStore.contentCollection) {
    override fun generatedRecordKey(value: JsonObject): RecordKey =
        value.stringValue("id")?.let(RecordKey::require) ?: generatedRecordKey("content")

    override suspend fun hasRecords(
        userId: java.util.UUID,
        collectionsRepository: LogDateCollectionsRepository,
    ): Boolean = collectionsRepository.listEntries(userId).isNotEmpty()

    override suspend fun upsert(
        collectionsRepository: LogDateCollectionsRepository,
        userId: java.util.UUID,
        recordId: RepoRecordId,
        value: JsonObject,
    ) {
        requireMatchingType(value = value, collection = nsid)
        val existing = collectionsRepository.getEntry(userId, recordId.recordKey.toString())
        collectionsRepository.upsertEntry(
            userId = userId,
            entry =
                LogDateEntry(
                    id = requireMatchingId(recordId = recordId, value = value, fieldName = "id"),
                    type = value.stringValue("type") ?: existing?.type ?: DEFAULT_CONTENT_TYPE,
                    content = if (value.containsKey("content")) value.nullableStringValue("content") else existing?.content,
                    mediaUri = if (value.containsKey("mediaUri")) value.nullableStringValue("mediaUri") else existing?.mediaUri,
                    durationMs = value.longValue("durationMs") ?: existing?.durationMs ?: DEFAULT_DURATION_MS,
                    createdAt = value.longValue("createdAt") ?: existing?.createdAt ?: System.currentTimeMillis(),
                    lastUpdated = value.longValue("lastUpdated") ?: System.currentTimeMillis(),
                    version = existing?.version ?: 0L,
                    deviceId =
                        value.deviceIdOrDefault(
                            if (value.containsKey("deviceId")) {
                                null
                            } else {
                                existing?.deviceId
                            },
                        ),
                ),
        )
    }

    override suspend fun delete(
        collectionsRepository: LogDateCollectionsRepository,
        userId: java.util.UUID,
        recordId: RepoRecordId,
    ) {
        collectionsRepository.deleteEntry(
            userId = userId,
            id = recordId.recordKey.toString(),
            deletedAt = System.currentTimeMillis(),
        )
    }
}

private object JournalCollectionAdapter : LogDateCollectionAdapter(LogDateRepoStore.journalCollection) {
    override fun generatedRecordKey(value: JsonObject): RecordKey =
        value.stringValue("id")?.let(RecordKey::require) ?: generatedRecordKey("journal")

    override suspend fun hasRecords(
        userId: java.util.UUID,
        collectionsRepository: LogDateCollectionsRepository,
    ): Boolean = collectionsRepository.listJournals(userId).isNotEmpty()

    override suspend fun upsert(
        collectionsRepository: LogDateCollectionsRepository,
        userId: java.util.UUID,
        recordId: RepoRecordId,
        value: JsonObject,
    ) {
        requireMatchingType(value = value, collection = nsid)
        val existing = collectionsRepository.getJournal(userId, recordId.recordKey.toString())
        collectionsRepository.upsertJournal(
            userId = userId,
            journal =
                LogDateJournal(
                    id = requireMatchingId(recordId = recordId, value = value, fieldName = "id"),
                    title = value.stringValue("title") ?: existing?.title.orEmpty(),
                    description = value.stringValue("description") ?: existing?.description.orEmpty(),
                    createdAt = value.longValue("createdAt") ?: existing?.createdAt ?: System.currentTimeMillis(),
                    lastUpdated = value.longValue("lastUpdated") ?: System.currentTimeMillis(),
                    version = existing?.version ?: 0L,
                    deviceId =
                        value.deviceIdOrDefault(
                            if (value.containsKey("deviceId")) {
                                null
                            } else {
                                existing?.deviceId
                            },
                        ),
                ),
        )
    }

    override suspend fun delete(
        collectionsRepository: LogDateCollectionsRepository,
        userId: java.util.UUID,
        recordId: RepoRecordId,
    ) {
        collectionsRepository.deleteJournal(
            userId = userId,
            id = recordId.recordKey.toString(),
            deletedAt = System.currentTimeMillis(),
        )
    }
}

private object AssociationCollectionAdapter : LogDateCollectionAdapter(LogDateRepoStore.associationCollection) {
    override fun generatedRecordKey(value: JsonObject): RecordKey = associationRecordKey(value)

    override suspend fun hasRecords(
        userId: java.util.UUID,
        collectionsRepository: LogDateCollectionsRepository,
    ): Boolean = collectionsRepository.listAssociations(userId).isNotEmpty()

    override suspend fun upsert(
        collectionsRepository: LogDateCollectionsRepository,
        userId: java.util.UUID,
        recordId: RepoRecordId,
        value: JsonObject,
    ) {
        requireMatchingType(value = value, collection = nsid)
        val journalId = value.stringValue("journalId") ?: journalIdFromAssociationRecordKey(recordId.recordKey)
        val entryId = value.stringValue("contentId") ?: entryIdFromAssociationRecordKey(recordId.recordKey)
        require(associationRecordKey(journalId, entryId) == recordId.recordKey) {
            "Association record key must match journalId/contentId"
        }
        val existing =
            collectionsRepository.listAssociations(userId).firstOrNull {
                it.journalId == journalId && it.entryId == entryId
            }
        collectionsRepository.upsertAssociations(
            userId = userId,
            associations =
                listOf(
                    LogDateAssociation(
                        journalId = journalId,
                        entryId = entryId,
                        createdAt = value.longValue("createdAt") ?: existing?.createdAt ?: System.currentTimeMillis(),
                        version = existing?.version ?: 0L,
                        deviceId =
                            value.deviceIdOrDefault(
                                if (value.containsKey("deviceId")) {
                                    null
                                } else {
                                    existing?.deviceId
                                },
                            ),
                    ),
                ),
        )
    }

    override suspend fun delete(
        collectionsRepository: LogDateCollectionsRepository,
        userId: java.util.UUID,
        recordId: RepoRecordId,
    ) {
        collectionsRepository.deleteAssociations(
            userId = userId,
            associations =
                listOf(
                    LogDateAssociationRef(
                        journalId = journalIdFromAssociationRecordKey(recordId.recordKey),
                        entryId = entryIdFromAssociationRecordKey(recordId.recordKey),
                    ),
                ),
            deletedAt = System.currentTimeMillis(),
        )
    }
}
