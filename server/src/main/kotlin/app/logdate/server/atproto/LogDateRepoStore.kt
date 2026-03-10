@file:OptIn(ExperimentalUuidApi::class)

package app.logdate.server.atproto

import app.logdate.server.database.toJavaUUID
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.logdate.LogDateAssociation
import app.logdate.server.logdate.LogDateAssociationRef
import app.logdate.server.logdate.LogDateCollectionsRepository
import app.logdate.server.logdate.LogDateEntry
import app.logdate.server.logdate.LogDateJournal
import app.logdate.server.logdate.SyncBackedLogDateCollectionsRepository
import app.logdate.server.sync.SyncRepository
import app.logdate.shared.model.sync.DeviceId
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.DefaultRepoEngine
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
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
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Multi-collection LogDate repo store backed by the shared standalone repo engine.
 *
 * The canonical repo semantics now come from [DefaultRepoEngine]. This adapter hydrates the shared
 * engine from the existing sync repository, applies repo operations there, and then persists the
 * resulting record state back into LogDate's sync tables.
 */
class LogDateRepoStore(
    private val collectionsRepository: LogDateCollectionsRepository,
    private val identityService: AtprotoIdentityService,
    private val blockStore: RepoBlockStore = InMemoryRepoBlockStore(),
) : RepoEngine {
    constructor(
        syncRepository: SyncRepository,
        identityService: AtprotoIdentityService,
        blockStore: RepoBlockStore = InMemoryRepoBlockStore(),
    ) : this(
        collectionsRepository = SyncBackedLogDateCollectionsRepository(syncRepository),
        identityService = identityService,
        blockStore = blockStore,
    )

    suspend fun collectionsForDid(did: String): List<Nsid> {
        val account = requireNotNull(identityService.findByDid(did)) { "Unknown repo DID: $did" }
        val userId = account.id.toJavaUUID()
        return supportedCollections
            .filter { definition -> definition.loadRecords(userId, AtprotoDid.require(account.did!!), collectionsRepository).isNotEmpty() }
            .map(LogDateCollection::nsid)
    }

    override suspend fun getRecord(recordId: RepoRecordId): Result<RepoRecord?> =
        runCatching {
            synchronizeRepo(recordId.repo).engine.getRecord(recordId).getOrThrow()
        }

    override suspend fun listRecords(
        repo: AtprotoDid,
        collection: Nsid,
        limit: Int,
        cursor: String?,
        reverse: Boolean,
    ): Result<RepoListPage> =
        runCatching {
            requireSupportedCollection(collection)
            synchronizeRepo(repo)
                .engine
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
            val preview = previewRepo(repo)
            val resolvedRecordKey = recordKey ?: definition.generatedRecordKey(value)
            val provisionalWrite =
                preview.engine
                    .createRecord(
                        repo = repo,
                        collection = collection,
                        value = value,
                        recordKey = resolvedRecordKey,
                    ).getOrThrow()
            val persistedRecordId =
                RepoRecordId(
                    repo = repo,
                    collection = collection,
                    recordKey = requireNotNull(provisionalWrite.uri.recordKey),
                )
            persistRecord(
                userId = preview.userId,
                recordId = persistedRecordId,
                value = value,
            )
            val persisted = requireNotNull(synchronizeRepo(repo).engine.getRecord(persistedRecordId).getOrThrow())
            RepoWriteResult(
                uri = persisted.uri,
                cid = requireNotNull(persisted.cid),
                validationStatus = RepoValidationStatus.UNKNOWN,
            )
        }

    override suspend fun putRecord(
        recordId: RepoRecordId,
        value: JsonObject,
        swapRecord: String?,
    ): Result<RepoWriteResult> =
        runCatching {
            requireSupportedCollection(recordId.collection)
            val preview = previewRepo(recordId.repo)
            preview.engine.putRecord(recordId = recordId, value = value, swapRecord = swapRecord).getOrThrow()
            persistRecord(
                userId = preview.userId,
                recordId = recordId,
                value = value,
            )
            val persisted = requireNotNull(synchronizeRepo(recordId.repo).engine.getRecord(recordId).getOrThrow())
            RepoWriteResult(
                uri = persisted.uri,
                cid = requireNotNull(persisted.cid),
                validationStatus = RepoValidationStatus.UNKNOWN,
            )
        }

    override suspend fun deleteRecord(
        recordId: RepoRecordId,
        swapRecord: String?,
    ): Result<Boolean> =
        runCatching {
            val definition = requireSupportedCollection(recordId.collection)
            val preview = previewRepo(recordId.repo)
            val deleted =
                preview.engine
                    .deleteRecord(
                        recordId = recordId,
                        swapRecord = swapRecord,
                    ).getOrThrow()
            if (deleted) {
                definition.delete(
                    collectionsRepository = collectionsRepository,
                    userId = preview.userId,
                    recordId = recordId,
                )
            }
            synchronizeRepo(recordId.repo)
            deleted
        }

    override suspend fun loadHead(repo: AtprotoDid): Result<RepoHead?> =
        runCatching {
            synchronizeRepo(repo).engine.loadHead(repo).getOrThrow()
        }

    override suspend fun listCommits(
        repo: AtprotoDid,
        limit: Int,
    ): Result<List<SignedRepoCommit>> =
        runCatching {
            synchronizeRepo(repo).engine.listCommits(repo = repo, limit = limit).getOrThrow()
        }

    override suspend fun export(repo: AtprotoDid): Result<RepoExport> =
        runCatching {
            synchronizeRepo(repo).engine.export(repo).getOrThrow()
        }

    override suspend fun import(export: RepoExport): Result<RepoHead> =
        Result.failure(UnsupportedOperationException("LogDate sync-backed repo engine does not support imports"))

    private suspend fun previewRepo(repo: AtprotoDid): HydratedRepo {
        val account = requireNotNull(identityService.findByDid(repo.toString())) { "Unknown repo DID: $repo" }
        val userId = account.id.toJavaUUID()
        val engine = DefaultRepoEngine(InMemoryRepoBlockStore(), MIRROR_CLOCK)

        supportedCollections
            .flatMap { definition ->
                definition.loadRecords(userId = userId, repo = repo, collectionsRepository = collectionsRepository)
            }.sortedWith(
                compareBy<LogDateRepoRecord>({ it.recordId.collection.toString() }, { it.recordId.recordKey.toString() }),
            ).forEach { record ->
                engine.putRecord(record.recordId, record.value).getOrThrow()
            }

        return HydratedRepo(userId = userId, engine = engine)
    }

    private suspend fun synchronizeRepo(repo: AtprotoDid): HydratedRepo {
        val preview = previewRepo(repo)
        val mirrorEngine = DefaultRepoEngine(blockStore, MIRROR_CLOCK)
        val mirroredHead = mirrorEngine.loadHead(repo).getOrThrow()
        val previewHead = preview.engine.loadHead(repo).getOrThrow()

        when {
            previewHead == null && mirroredHead != null -> blockStore.clearRepo(repo).getOrThrow()
            previewHead != null && previewHead != mirroredHead -> {
                val export = preview.engine.export(repo).getOrThrow()
                blockStore.clearRepo(repo).getOrThrow()
                mirrorEngine.import(export).getOrThrow()
            }
        }

        return HydratedRepo(userId = preview.userId, engine = mirrorEngine)
    }

    private fun requireSupportedCollection(collection: Nsid): LogDateCollection =
        supportedCollections.firstOrNull { it.nsid == collection }
            ?: throw UnsupportedCollectionException(collection.toString())

    private fun persistRecord(
        userId: java.util.UUID,
        recordId: RepoRecordId,
        value: JsonObject,
    ) {
        requireSupportedCollection(recordId.collection).upsert(
            collectionsRepository = collectionsRepository,
            userId = userId,
            recordId = recordId,
            value = value,
        )
    }

    companion object {
        val contentCollection: Nsid = Nsid.require("studio.hypertext.logdate.content")
        val journalCollection: Nsid = Nsid.require("studio.hypertext.logdate.journal")
        val associationCollection: Nsid = Nsid.require("studio.hypertext.logdate.association")
        private val MIRROR_CLOCK: Clock =
            object : Clock {
                override fun now(): Instant = Instant.fromEpochMilliseconds(0)
            }

        private val supportedCollections: List<LogDateCollection> =
            listOf(
                ContentLogDateCollection,
                JournalLogDateCollection,
                AssociationLogDateCollection,
            )
    }
}

private data class HydratedRepo(
    val userId: java.util.UUID,
    val engine: DefaultRepoEngine,
)

private data class LogDateRepoRecord(
    val recordId: RepoRecordId,
    val value: JsonObject,
)

private sealed class LogDateCollection(
    val nsid: Nsid,
) {
    abstract fun generatedRecordKey(value: JsonObject): RecordKey

    abstract fun loadRecords(
        userId: java.util.UUID,
        repo: AtprotoDid,
        collectionsRepository: LogDateCollectionsRepository,
    ): List<LogDateRepoRecord>

    abstract fun upsert(
        collectionsRepository: LogDateCollectionsRepository,
        userId: java.util.UUID,
        recordId: RepoRecordId,
        value: JsonObject,
    )

    abstract fun delete(
        collectionsRepository: LogDateCollectionsRepository,
        userId: java.util.UUID,
        recordId: RepoRecordId,
    )
}

private object ContentLogDateCollection : LogDateCollection(LogDateRepoStore.contentCollection) {
    override fun generatedRecordKey(value: JsonObject): RecordKey = RecordKey.require(value.stringValue("id") ?: "content-${Uuid.random()}")

    override fun loadRecords(
        userId: java.util.UUID,
        repo: AtprotoDid,
        collectionsRepository: LogDateCollectionsRepository,
    ): List<LogDateRepoRecord> =
        collectionsRepository.listEntries(userId = userId).map { record ->
            val recordId =
                RepoRecordId(
                    repo = repo,
                    collection = nsid,
                    recordKey = RecordKey.require(record.id),
                )
            LogDateRepoRecord(recordId = recordId, value = record.toJson())
        }

    override fun upsert(
        collectionsRepository: LogDateCollectionsRepository,
        userId: java.util.UUID,
        recordId: RepoRecordId,
        value: JsonObject,
    ) {
        requireMatchingType(value = value, collection = nsid)
        val existing = collectionsRepository.getEntry(userId, recordId.recordKey.toString())
        val now = System.currentTimeMillis()
        val record =
            LogDateEntry(
                id = requireMatchingId(recordId = recordId, value = value, fieldName = "id"),
                type = value.stringValue("type") ?: existing?.type ?: DEFAULT_CONTENT_TYPE,
                content = if (value.containsKey("content")) value.nullableStringValue("content") else existing?.content,
                mediaUri = if (value.containsKey("mediaUri")) value.nullableStringValue("mediaUri") else existing?.mediaUri,
                durationMs = value.longValue("durationMs") ?: existing?.durationMs ?: DEFAULT_DURATION_MS,
                createdAt = value.longValue("createdAt") ?: existing?.createdAt ?: now,
                lastUpdated = value.longValue("lastUpdated") ?: now,
                version = existing?.version ?: 0L,
                deviceId =
                    value.deviceIdOrDefault(
                        if (value.containsKey("deviceId")) {
                            null
                        } else {
                            existing?.deviceId
                        },
                    ),
            )
        collectionsRepository.upsertEntry(userId = userId, entry = record)
    }

    override fun delete(
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

    private fun LogDateEntry.toJson(): JsonObject =
        buildJsonObject {
            put(TYPE_FIELD_NAME, nsid.toString())
            put("id", id)
            put("type", type)
            if (content != null) {
                put("content", content)
            } else {
                put("content", JsonNull)
            }
            if (mediaUri != null) {
                put("mediaUri", mediaUri)
            } else {
                put("mediaUri", JsonNull)
            }
            put("durationMs", durationMs ?: DEFAULT_DURATION_MS)
            put("createdAt", createdAt)
            put("lastUpdated", lastUpdated)
            put("deviceId", deviceId.value)
        }
}

private object JournalLogDateCollection : LogDateCollection(LogDateRepoStore.journalCollection) {
    override fun generatedRecordKey(value: JsonObject): RecordKey = RecordKey.require(value.stringValue("id") ?: "journal-${Uuid.random()}")

    override fun loadRecords(
        userId: java.util.UUID,
        repo: AtprotoDid,
        collectionsRepository: LogDateCollectionsRepository,
    ): List<LogDateRepoRecord> =
        collectionsRepository.listJournals(userId = userId).map { record ->
            val recordId =
                RepoRecordId(
                    repo = repo,
                    collection = nsid,
                    recordKey = RecordKey.require(record.id),
                )
            LogDateRepoRecord(recordId = recordId, value = record.toJson())
        }

    override fun upsert(
        collectionsRepository: LogDateCollectionsRepository,
        userId: java.util.UUID,
        recordId: RepoRecordId,
        value: JsonObject,
    ) {
        requireMatchingType(value = value, collection = nsid)
        val existing = collectionsRepository.getJournal(userId, recordId.recordKey.toString())
        val now = System.currentTimeMillis()
        val record =
            LogDateJournal(
                id = requireMatchingId(recordId = recordId, value = value, fieldName = "id"),
                title = value.stringValue("title") ?: existing?.title ?: "",
                description = value.stringValue("description") ?: existing?.description ?: "",
                createdAt = value.longValue("createdAt") ?: existing?.createdAt ?: now,
                lastUpdated = value.longValue("lastUpdated") ?: now,
                version = existing?.version ?: 0L,
                deviceId =
                    value.deviceIdOrDefault(
                        if (value.containsKey("deviceId")) {
                            null
                        } else {
                            existing?.deviceId
                        },
                    ),
            )
        collectionsRepository.upsertJournal(userId = userId, journal = record)
    }

    override fun delete(
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

    private fun LogDateJournal.toJson(): JsonObject =
        buildJsonObject {
            put(TYPE_FIELD_NAME, nsid.toString())
            put("id", id)
            put("title", title)
            put("description", description)
            put("createdAt", createdAt)
            put("lastUpdated", lastUpdated)
            put("deviceId", deviceId.value)
        }
}

private object AssociationLogDateCollection : LogDateCollection(LogDateRepoStore.associationCollection) {
    override fun generatedRecordKey(value: JsonObject): RecordKey = associationRecordKey(value = value)

    override fun loadRecords(
        userId: java.util.UUID,
        repo: AtprotoDid,
        collectionsRepository: LogDateCollectionsRepository,
    ): List<LogDateRepoRecord> =
        collectionsRepository.listAssociations(userId = userId).map { record ->
            val recordId =
                RepoRecordId(
                    repo = repo,
                    collection = nsid,
                    recordKey = associationRecordKey(record.journalId, record.entryId),
                )
            LogDateRepoRecord(recordId = recordId, value = record.toJson())
        }

    override fun upsert(
        collectionsRepository: LogDateCollectionsRepository,
        userId: java.util.UUID,
        recordId: RepoRecordId,
        value: JsonObject,
    ) {
        requireMatchingType(value = value, collection = nsid)
        val journalId = value.stringValue("journalId") ?: journalIdFromRecordKey(recordId.recordKey)
        val contentId = value.stringValue("contentId") ?: contentIdFromRecordKey(recordId.recordKey)
        val expectedKey = associationRecordKey(journalId, contentId)
        require(expectedKey == recordId.recordKey) { "Association record key must match journalId/contentId" }

        val existing =
            collectionsRepository.listAssociations(userId = userId).firstOrNull {
                it.journalId == journalId && it.entryId == contentId
            }
        val record =
            LogDateAssociation(
                journalId = journalId,
                entryId = contentId,
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
            )
        collectionsRepository.upsertAssociations(userId = userId, associations = listOf(record))
    }

    override fun delete(
        collectionsRepository: LogDateCollectionsRepository,
        userId: java.util.UUID,
        recordId: RepoRecordId,
    ) {
        collectionsRepository.deleteAssociations(
            userId = userId,
            associations =
                listOf(
                    LogDateAssociationRef(
                        journalId = journalIdFromRecordKey(recordId.recordKey),
                        entryId = contentIdFromRecordKey(recordId.recordKey),
                    ),
                ),
            deletedAt = System.currentTimeMillis(),
        )
    }

    private fun LogDateAssociation.toJson(): JsonObject =
        buildJsonObject {
            put(TYPE_FIELD_NAME, nsid.toString())
            put("journalId", journalId)
            put("contentId", entryId)
            put("createdAt", createdAt)
            put("deviceId", deviceId.value)
        }
}

private fun requireMatchingType(
    value: JsonObject,
    collection: Nsid,
) {
    val typeField = value[TYPE_FIELD_NAME]?.jsonPrimitive?.contentOrNull
    if (typeField != null && typeField != collection.toString()) {
        throw UnsupportedCollectionException(typeField)
    }
}

private fun requireMatchingId(
    recordId: RepoRecordId,
    value: JsonObject,
    fieldName: String,
): String {
    val explicitId = value.stringValue(fieldName)
    require(explicitId == null || explicitId == recordId.recordKey.toString()) {
        "$fieldName must match the record key ${recordId.recordKey}"
    }
    return explicitId ?: recordId.recordKey.toString()
}

private fun associationRecordKey(value: JsonObject): RecordKey =
    associationRecordKey(
        journalId = requireNotNull(value.stringValue("journalId")) { "journalId is required" },
        contentId = requireNotNull(value.stringValue("contentId")) { "contentId is required" },
    )

private fun associationRecordKey(
    journalId: String,
    contentId: String,
): RecordKey = RecordKey.require("$journalId~$contentId")

private fun journalIdFromRecordKey(recordKey: RecordKey): String = recordKey.toString().substringBefore('~')

private fun contentIdFromRecordKey(recordKey: RecordKey): String = recordKey.toString().substringAfter('~')

private fun JsonObject.stringValue(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.nullableStringValue(key: String): String? =
    when (val element = this[key]) {
        null, JsonNull -> null
        else -> element.jsonPrimitive.contentOrNull
    }

private fun JsonObject.longValue(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

private fun JsonObject.deviceIdOrDefault(default: DeviceId?): DeviceId =
    if (containsKey("deviceId")) {
        DeviceId(nullableStringValue("deviceId") ?: DeviceId.UNKNOWN.value)
    } else {
        default ?: DeviceId.UNKNOWN
    }

private const val DEFAULT_CONTENT_TYPE = "TEXT"
private const val DEFAULT_DURATION_MS = 0L
private const val TYPE_FIELD_NAME = "\$type"
