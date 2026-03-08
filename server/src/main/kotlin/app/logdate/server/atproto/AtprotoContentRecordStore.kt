package app.logdate.server.atproto

import app.logdate.server.database.toJavaUUID
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.sync.ContentRecord
import app.logdate.server.sync.SyncRepository
import app.logdate.shared.model.sync.DeviceId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import studio.hypertext.atproto.repo.InvalidRepoCursorException
import studio.hypertext.atproto.repo.RepoListPage
import studio.hypertext.atproto.repo.RepoRecord
import studio.hypertext.atproto.repo.RepoRecordId
import studio.hypertext.atproto.repo.RepoRecordStore
import studio.hypertext.atproto.repo.RepoValidationStatus
import studio.hypertext.atproto.repo.RepoWriteResult
import studio.hypertext.atproto.repo.UnsupportedCollectionException
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import java.security.MessageDigest
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Narrow AT Protocol repository adapter backed by LogDate content records.
 *
 * This gives the server a standalone repo-style XRPC surface for a single
 * collection while the broader repo implementation continues to evolve.
 */
@OptIn(ExperimentalUuidApi::class)
class AtprotoContentRecordStore(
    private val syncRepository: SyncRepository,
    private val identityService: AtprotoIdentityService,
) : RepoRecordStore {
    suspend fun collectionsForDid(did: String): List<Nsid> {
        val account = requireNotNull(identityService.findByDid(did)) { "Unknown repo DID: $did" }
        return if (loadAllContent(account.id.toJavaUUID()).isEmpty()) emptyList() else listOf(contentCollection)
    }

    override suspend fun getRecord(recordId: RepoRecordId): Result<RepoRecord?> =
        runCatching {
            requireSupportedCollection(recordId.collection)
            val account = requireNotNull(identityService.findByDid(recordId.repo.toString())) { "Unknown repo DID" }
            syncRepository
                .getContent(account.id.toJavaUUID(), recordId.recordKey.toString())
                ?.toRepoRecord(recordId)
        }

    override suspend fun listRecords(
        repo: studio.hypertext.atproto.identity.AtprotoDid,
        collection: Nsid,
        limit: Int,
        cursor: String?,
        reverse: Boolean,
    ): Result<RepoListPage> =
        runCatching {
            requireSupportedCollection(collection)
            val safeLimit = limit.coerceIn(1, MAX_PAGE_SIZE)
            val account = requireNotNull(identityService.findByDid(repo.toString())) { "Unknown repo DID" }
            val allRecords = loadAllContent(account.id.toJavaUUID())
            val cursorVersion = cursor?.toLongOrNull() ?: cursor?.let { throw InvalidRepoCursorException(it) }
            val filtered =
                if (reverse) {
                    allRecords
                        .sortedByDescending(ContentRecord::serverVersion)
                        .filter { cursorVersion == null || it.serverVersion < cursorVersion }
                } else {
                    allRecords
                        .sortedBy(ContentRecord::serverVersion)
                        .filter { cursorVersion == null || it.serverVersion > cursorVersion }
                }
            val page = filtered.take(safeLimit)
            val nextCursor =
                page
                    .lastOrNull()
                    ?.serverVersion
                    ?.toString()
                    ?.takeIf { filtered.size > page.size }

            RepoListPage(
                records =
                    page.map { record ->
                        record.toRepoRecord(
                            RepoRecordId(
                                repo = repo,
                                collection = collection,
                                recordKey = RecordKey.require(record.id),
                            ),
                        )
                    },
                cursor = nextCursor,
            )
        }

    override suspend fun createRecord(
        repo: studio.hypertext.atproto.identity.AtprotoDid,
        collection: Nsid,
        value: JsonObject,
        recordKey: RecordKey?,
    ): Result<RepoWriteResult> =
        runCatching {
            val resolvedRecordKey = recordKey ?: RecordKey.require("content-${Uuid.random()}")
            putRecord(
                recordId =
                    RepoRecordId(
                        repo = repo,
                        collection = collection,
                        recordKey = resolvedRecordKey,
                    ),
                value = value,
            ).getOrThrow()
        }

    override suspend fun putRecord(
        recordId: RepoRecordId,
        value: JsonObject,
        swapRecord: String?,
    ): Result<RepoWriteResult> =
        runCatching {
            requireSupportedCollection(recordId.collection)
            val account = requireNotNull(identityService.findByDid(recordId.repo.toString())) { "Unknown repo DID" }
            val userId = account.id.toJavaUUID()
            val existing = syncRepository.getContent(userId, recordId.recordKey.toString())
            val existingCid =
                existing?.toRepoRecord(recordId)?.cid
            if (swapRecord != null && existingCid != swapRecord) {
                throw InvalidSwapException(expectedCid = existingCid, providedCid = swapRecord)
            }

            val now = System.currentTimeMillis()
            val inputRecord =
                contentRecordFromJson(
                    recordId = recordId,
                    value = value,
                    existing = existing,
                    now = now,
                )
            syncRepository.upsertContent(userId, inputRecord)
            val persisted = requireNotNull(syncRepository.getContent(userId, recordId.recordKey.toString()))
            val repoRecord = persisted.toRepoRecord(recordId)

            RepoWriteResult(
                uri = repoRecord.uri,
                cid = requireNotNull(repoRecord.cid),
                validationStatus = RepoValidationStatus.UNKNOWN,
            )
        }

    override suspend fun deleteRecord(
        recordId: RepoRecordId,
        swapRecord: String?,
    ): Result<Boolean> =
        runCatching {
            requireSupportedCollection(recordId.collection)
            val account = requireNotNull(identityService.findByDid(recordId.repo.toString())) { "Unknown repo DID" }
            val userId = account.id.toJavaUUID()
            val existing = syncRepository.getContent(userId, recordId.recordKey.toString())
            val existingCid =
                existing?.toRepoRecord(recordId)?.cid
            if (swapRecord != null && existingCid != swapRecord) {
                throw InvalidSwapException(expectedCid = existingCid, providedCid = swapRecord)
            }

            if (existing != null) {
                syncRepository.deleteContent(
                    userId = userId,
                    id = recordId.recordKey.toString(),
                    deletedAt = System.currentTimeMillis(),
                )
            }
            existing != null
        }

    private fun requireSupportedCollection(collection: Nsid) {
        if (collection != contentCollection) {
            throw UnsupportedCollectionException(collection.toString())
        }
    }

    private suspend fun loadAllContent(userId: java.util.UUID): List<ContentRecord> {
        val records = linkedMapOf<String, ContentRecord>()
        var since = 0L
        do {
            val changeSet = syncRepository.contentChanges(userId = userId, since = since, limit = CHANGE_PAGE_SIZE)
            changeSet.changes.forEach { records[it.id] = it }
            changeSet.deletions.forEach { records.remove(it.id) }
            if (changeSet.lastTimestamp <= since) {
                break
            }
            since = changeSet.lastTimestamp
        } while (changeSet.hasMore)
        return records.values.toList()
    }

    private fun contentRecordFromJson(
        recordId: RepoRecordId,
        value: JsonObject,
        existing: ContentRecord?,
        now: Long,
    ): ContentRecord {
        val typeField = value[TYPE_FIELD_NAME]?.jsonPrimitive?.contentOrNull
        if (typeField != null && typeField != contentCollection.toString()) {
            throw UnsupportedCollectionException(typeField)
        }

        return ContentRecord(
            id = recordId.recordKey.toString(),
            type = value.stringValue("type") ?: existing?.type ?: DEFAULT_CONTENT_TYPE,
            content = if (value.containsKey("content")) value.nullableStringValue("content") else existing?.content,
            mediaUri = if (value.containsKey("mediaUri")) value.nullableStringValue("mediaUri") else existing?.mediaUri,
            durationMs = value.longValue("durationMs") ?: existing?.durationMs ?: DEFAULT_DURATION_MS,
            createdAt = value.longValue("createdAt") ?: existing?.createdAt ?: now,
            lastUpdated = value.longValue("lastUpdated") ?: now,
            serverVersion = existing?.serverVersion ?: 0L,
            deviceId =
                DeviceId(
                    if (value.containsKey("deviceId")) {
                        value.nullableStringValue("deviceId") ?: DeviceId.UNKNOWN.value
                    } else {
                        existing?.deviceId?.value
                            ?: DeviceId.UNKNOWN.value
                    },
                ),
        )
    }

    private fun ContentRecord.toRepoRecord(recordId: RepoRecordId): RepoRecord {
        val value = toJsonValue()
        return RepoRecord(
            uri = recordId.uri,
            cid = SyntheticCid.fromRecord(recordId.uri.toString(), value),
            value = value,
        )
    }

    private fun ContentRecord.toJsonValue(): JsonObject =
        buildJsonObject {
            put(TYPE_FIELD_NAME, contentCollection.toString())
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

    private fun JsonObject.stringValue(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.nullableStringValue(key: String): String? =
        when (val element = this[key]) {
            null, JsonNull -> null
            else -> element.jsonPrimitive.contentOrNull
        }

    private fun JsonObject.longValue(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

    companion object {
        val contentCollection: Nsid = Nsid.require("studio.hypertext.logdate.content")

        private const val CHANGE_PAGE_SIZE = 100
        private const val MAX_PAGE_SIZE = 100
        private const val DEFAULT_CONTENT_TYPE = "TEXT"
        private const val DEFAULT_DURATION_MS = 0L
        private const val TYPE_FIELD_NAME = "\$type"
    }
}

/**
 * Raised when compare-and-swap metadata does not match the current record state.
 */
class InvalidSwapException(
    val expectedCid: String?,
    val providedCid: String,
) : IllegalStateException("Invalid swapRecord")

private object SyntheticCid {
    private val json = Json { explicitNulls = true }
    private val base32Alphabet = "abcdefghijklmnopqrstuvwxyz234567".toCharArray()

    fun fromRecord(
        uri: String,
        value: JsonObject,
    ): String {
        val payload = "$uri\n${json.encodeToString(JsonObject.serializer(), value)}".encodeToByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        val cidBytes =
            encodeVarint(CID_VERSION) + encodeVarint(RAW_CODEC) + byteArrayOf(SHA256_CODE.toByte(), SHA256_SIZE.toByte()) + digest
        return "b${encodeBase32(cidBytes)}"
    }

    private fun encodeVarint(value: Int): ByteArray {
        var remaining = value
        val bytes = mutableListOf<Byte>()
        do {
            var next = remaining and 0x7f
            remaining = remaining ushr 7
            if (remaining != 0) {
                next = next or 0x80
            }
            bytes += next.toByte()
        } while (remaining != 0)
        return bytes.toByteArray()
    }

    private fun encodeBase32(bytes: ByteArray): String {
        if (bytes.isEmpty()) {
            return ""
        }

        val output = StringBuilder(((bytes.size * 8) + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        bytes.forEach { byte ->
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                output.append(base32Alphabet[(buffer shr (bitsLeft - 5)) and 0x1f])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            output.append(base32Alphabet[(buffer shl (5 - bitsLeft)) and 0x1f])
        }
        return output.toString()
    }

    private const val CID_VERSION = 1
    private const val RAW_CODEC = 0x55
    private const val SHA256_CODE = 0x12
    private const val SHA256_SIZE = 32
}
