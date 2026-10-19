package app.logdate.server.routes

import app.logdate.lexicon.app.logdate.crypto.Sealed
import app.logdate.lexicon.app.logdate.space.DeleteRecordOutput
import app.logdate.lexicon.app.logdate.space.GetRecordOutput
import app.logdate.lexicon.app.logdate.space.ListRepoOpsChange
import app.logdate.lexicon.app.logdate.space.ListRepoOpsOutput
import app.logdate.lexicon.app.logdate.space.PutRecordOutput
import app.logdate.lexicon.app.logdate.space.UploadBlobOutput
import app.logdate.server.auth.Account
import app.logdate.server.database.toJavaUUID
import app.logdate.server.logdate.LogDateBlobNamespace
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateBlobWriteRequest
import app.logdate.server.routes.docs.XrpcSpaceDocs
import app.logdate.server.space.MAX_SPACE_CHANGES_LIMIT
import app.logdate.server.space.SpaceBlob
import app.logdate.server.space.SpaceCursorTooOldException
import app.logdate.server.space.SpaceInvalidRecordException
import app.logdate.server.space.SpaceMissingBlobException
import app.logdate.server.space.SpaceRecordWrite
import app.logdate.server.space.SpaceStore
import app.logdate.server.space.SpaceSwapException
import app.logdate.server.space.checkSealedRecord
import app.logdate.space.SpaceRef
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import studio.hypertext.atproto.lexicon.data.Blob
import studio.hypertext.atproto.lexicon.data.CidLink
import studio.hypertext.atproto.pds.PdsErrorResponse
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.syntax.RecordKey
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The largest blob a space accepts. */
internal const val MAX_SPACE_BLOB_BYTES: Long = 100L * 1024 * 1024

/** Room for the largest sealed record, 4,000,000 bytes of ciphertext written as base64, and the rest of its JSON. */
internal const val MAX_SPACE_RECORD_REQUEST_BYTES: Long = 6L * 1024 * 1024

private const val DEFAULT_CHANGES_LIMIT = 200
private const val MAX_RECORD_BLOBS = 64

/** CIDs are stored in 255-character columns, so a longer value can't name anything. */
private const val MAX_CID_LENGTH = 255
private const val RECORD_NOT_FOUND = "RecordNotFound"
private const val BLOB_NOT_FOUND = "BlobNotFound"
private const val CURSOR_TOO_OLD = "CursorTooOld"
private const val SPACE_BLOB_MIME_TYPE = "application/octet-stream"

private val spaceJson = Json { ignoreUnknownKeys = true }

/**
 * The body of `app.logdate.space.putRecord`. Its record stays JSON, so the server stores exactly
 * the envelope that was sent and refuses one with fields it doesn't know.
 */
@Serializable
internal data class SpacePutRecordInput(
    val space: String,
    val rkey: String,
    val record: JsonObject,
    val blobs: List<String>? = null,
    val swapCid: String? = null,
    val createOnly: Boolean? = null,
)

/** The body of `app.logdate.space.deleteRecord`. */
@Serializable
internal data class SpaceDeleteRecordInput(
    val space: String,
    val rkey: String,
    val swapCid: String? = null,
)

/**
 * The `app.logdate.space.*` XRPC methods. Each account reaches only its own personal space; any
 * other space, including another account's, answers exactly as a missing record or blob does, so
 * nobody learns whether it exists. Only the owner's own LogDate devices get in: [auth] accepts
 * LogDate access tokens and nothing else. With [enabled] off, or without a [store], every method
 * answers `501 MethodNotImplemented`; the blob methods also need [blobStorage]. Every method checks
 * the access token first, so a caller without one gets `401` whether or not the feature is on.
 * Blobs larger than [maxBlobBytes] answer `413 BlobTooLarge`.
 */
fun Route.spaceXrpcRoutes(
    enabled: Boolean,
    store: SpaceStore?,
    blobStorage: LogDateBlobStorage?,
    auth: LogDateAccountAuth,
    maxBlobBytes: Long = MAX_SPACE_BLOB_BYTES,
) {
    val records = store?.takeIf { enabled }
    val objects = blobStorage?.takeIf { records != null }
    route("/xrpc") {
        putRecord(records, auth)
        deleteRecord(records, auth)
        getRecord(records, auth)
        listRepoOps(records, auth)
        uploadBlob(records, objects, auth, maxBlobBytes)
        getBlob(records, objects, auth)
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun Route.putRecord(
    records: SpaceStore?,
    auth: LogDateAccountAuth,
) {
    post("/app.logdate.space.putRecord", XrpcSpaceDocs.putRecord) {
        val account = auth.requireLogDateAccount(call) ?: return@post
        val store = records ?: return@post call.respondNotImplemented()
        val bytes =
            call.receiveBytesUpTo(MAX_SPACE_RECORD_REQUEST_BYTES)
                ?: return@post call.respond(HttpStatusCode.PayloadTooLarge, PdsErrorResponse("RecordTooLarge", "The record is too large"))
        val input = decodeOrNull<SpacePutRecordInput>(bytes) ?: return@post call.respondInvalid("Invalid putRecord input")
        val space = call.ownSpace(input.space, account, RECORD_NOT_FOUND) ?: return@post
        if (!isRecordKey(input.rkey)) return@post call.respondInvalid("rkey must be a record key")
        val blobs = input.blobs.orEmpty()
        if (blobs.size > MAX_RECORD_BLOBS) return@post call.respondInvalid("A record can refer to at most $MAX_RECORD_BLOBS blobs")
        if (!blobs.all(::isCid) ||
            input.swapCid?.let(::isCid) == false
        ) {
            return@post call.respondInvalid("blobs and swapCid must be CID-shaped")
        }
        checkSealedRecord(input.record)?.let { return@post call.respondInvalidRecord(it) }
        val write =
            SpaceRecordWrite(
                accountId = account.id.toJavaUUID(),
                space = space.uri,
                author = space.authority,
                rkey = input.rkey,
                record = input.record,
                blobs = blobs,
                swapCid = input.swapCid,
                createOnly = input.createOnly == true,
            )
        val stored =
            try {
                store.put(write)
            } catch (e: SpaceSwapException) {
                return@post call.respondSwapConflict(e)
            } catch (e: SpaceInvalidRecordException) {
                return@post call.respondInvalidRecord(e.reason)
            } catch (e: SpaceMissingBlobException) {
                return@post call.respondInvalidRecord(e.message ?: "The record refers to a missing blob")
            }
        call.respond(PutRecordOutput(cid = stored.cid, seq = stored.seq))
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun Route.deleteRecord(
    records: SpaceStore?,
    auth: LogDateAccountAuth,
) {
    post("/app.logdate.space.deleteRecord", XrpcSpaceDocs.deleteRecord) {
        val account = auth.requireLogDateAccount(call) ?: return@post
        val store = records ?: return@post call.respondNotImplemented()
        val bytes =
            call.receiveBytesUpTo(MAX_SPACE_RECORD_REQUEST_BYTES)
                ?: return@post call.respondInvalid("Invalid deleteRecord input")
        val input = decodeOrNull<SpaceDeleteRecordInput>(bytes) ?: return@post call.respondInvalid("Invalid deleteRecord input")
        val space = call.ownSpace(input.space, account, RECORD_NOT_FOUND) ?: return@post
        if (!isRecordKey(input.rkey)) return@post call.respondInvalid("rkey must be a record key")
        if (input.swapCid?.let(::isCid) == false) return@post call.respondInvalid("swapCid must be CID-shaped")
        val seq =
            try {
                store.delete(account.id.toJavaUUID(), space.uri, space.authority, input.rkey, input.swapCid)
            } catch (e: SpaceSwapException) {
                return@post call.respondSwapConflict(e)
            }
        call.respond(DeleteRecordOutput(seq = seq))
    }
}

private fun Route.getRecord(
    records: SpaceStore?,
    auth: LogDateAccountAuth,
) {
    get("/app.logdate.space.getRecord", XrpcSpaceDocs.getRecord) {
        val account = auth.requireLogDateAccount(call) ?: return@get
        val store = records ?: return@get call.respondNotImplemented()
        val space = call.ownSpace(call.request.queryParameters["space"], account, RECORD_NOT_FOUND) ?: return@get
        val rkey =
            call.request.queryParameters["rkey"]?.takeIf(::isRecordKey)
                ?: return@get call.respondInvalid("rkey must be a record key")
        val stored = store.get(space.uri, space.authority, rkey) ?: return@get call.respondMissing(RECORD_NOT_FOUND)
        call.respond(GetRecordOutput(cid = stored.cid, seq = stored.seq, value = stored.record.toSealed()))
    }
}

private fun Route.listRepoOps(
    records: SpaceStore?,
    auth: LogDateAccountAuth,
) {
    get("/app.logdate.space.listRepoOps", XrpcSpaceDocs.listRepoOps) {
        val account = auth.requireLogDateAccount(call) ?: return@get
        val store = records ?: return@get call.respondNotImplemented()
        val space = call.ownSpace(call.request.queryParameters["space"], account, RECORD_NOT_FOUND) ?: return@get
        val since =
            call.request.queryParameters["since"]?.let { param ->
                param.toLongOrNull()?.takeIf { it >= 0 } ?: return@get call.respondInvalid("since must be a whole number, 0 or more")
            } ?: 0L
        val limit =
            call.request.queryParameters["limit"]?.let { param ->
                param.toIntOrNull()?.coerceIn(1, MAX_SPACE_CHANGES_LIMIT) ?: return@get call.respondInvalid("limit must be a whole number")
            } ?: DEFAULT_CHANGES_LIMIT
        val page =
            try {
                store.changes(space.uri, space.authority, since, limit)
            } catch (_: SpaceCursorTooOldException) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    PdsErrorResponse(CURSOR_TOO_OLD, "Deletions after this cursor were purged; list again from 0"),
                )
            }
        val ops =
            page.changes.map {
                ListRepoOpsChange(cid = it.cid, deleted = it.deleted, rkey = it.rkey, seq = it.seq, value = it.record?.toSealed())
            }
        call.respond(ListRepoOpsOutput(cursor = page.cursor, hasMore = page.hasMore, ops = ops))
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun Route.uploadBlob(
    records: SpaceStore?,
    objects: LogDateBlobStorage?,
    auth: LogDateAccountAuth,
    maxBlobBytes: Long,
) {
    post("/app.logdate.space.uploadBlob", XrpcSpaceDocs.uploadBlob) {
        val account = auth.requireLogDateAccount(call) ?: return@post
        val store = records ?: return@post call.respondNotImplemented()
        val storage = objects ?: return@post call.respondNotImplemented()
        val space = call.ownSpace(call.request.queryParameters["space"], account, BLOB_NOT_FOUND) ?: return@post
        val bytes =
            call.receiveBytesUpTo(maxBlobBytes)
                ?: return@post call.respond(
                    HttpStatusCode.PayloadTooLarge,
                    PdsErrorResponse("BlobTooLarge", "Blobs can be at most $maxBlobBytes bytes"),
                )
        val cid = Cid.rawSha256(bytes).value
        val ownerId = account.id.toJavaUUID()
        val request =
            LogDateBlobWriteRequest(
                ownerId = ownerId,
                namespace = LogDateBlobNamespace.SPACE,
                blobId = "$cid-${Uuid.random()}",
                fileName = null,
                contentType = SPACE_BLOB_MIME_TYPE,
                bytes = bytes,
            )
        val path = withContext(Dispatchers.IO) { storage.putBlob(request) }
        val blob = SpaceBlob(cid = cid, mimeType = SPACE_BLOB_MIME_TYPE, size = bytes.size.toLong(), storagePath = path)
        val stored =
            runCatching { store.putBlob(ownerId, space.uri, blob) }.getOrElse { error ->
                withContext(NonCancellable + Dispatchers.IO) { storage.deleteUnsavedUpload(store, space.uri, cid, path, error) }
                throw error
            }
        if (stored.storagePath != path) withContext(Dispatchers.IO) { storage.deleteBlobIfExists(path) }
        call.respond(UploadBlobOutput(blob = Blob(ref = CidLink(cid = Cid.require(cid)), mimeType = stored.mimeType, size = stored.size)))
    }
}

private fun Route.getBlob(
    records: SpaceStore?,
    objects: LogDateBlobStorage?,
    auth: LogDateAccountAuth,
) {
    get("/app.logdate.space.getBlob", XrpcSpaceDocs.getBlob) {
        val account = auth.requireLogDateAccount(call) ?: return@get
        val store = records ?: return@get call.respondNotImplemented()
        val storage = objects ?: return@get call.respondNotImplemented()
        val space = call.ownSpace(call.request.queryParameters["space"], account, BLOB_NOT_FOUND) ?: return@get
        val cid = call.request.queryParameters["cid"]?.takeIf(::isCid) ?: return@get call.respondInvalid("cid must be CID-shaped")
        val bytes =
            store.getBlob(space.uri, cid)?.let { withContext(Dispatchers.IO) { storage.getBlob(it.storagePath) } }
                ?: return@get call.respondMissing(BLOB_NOT_FOUND)
        call.respondBytes(bytes, ContentType.Application.OctetStream)
    }
}

/**
 * The account's own personal space, named by [uri]. A URI that isn't a space is an invalid request;
 * any other space answers as missing with [notFoundCode], exactly as a missing record or blob does.
 */
private suspend fun ApplicationCall.ownSpace(
    uri: String?,
    account: Account,
    notFoundCode: String,
): SpaceRef? {
    val space = uri?.let(SpaceRef::parse)
    if (space == null) {
        respondInvalid("space must be a space URI")
        return null
    }
    val did = account.did
    if (did == null || space != SpaceRef.personal(did)) {
        respondMissing(notFoundCode)
        return null
    }
    return space
}

/**
 * Deletes the object at [path] after a failed metadata write, unless the write saved a row that
 * points at it anyway, as when the call is cancelled after the transaction commits. When the row
 * can't be read, the object stays: an orphaned object is harmless, a row without its object isn't.
 * Failures here are added to [error] rather than replacing it.
 */
private suspend fun LogDateBlobStorage.deleteUnsavedUpload(
    store: SpaceStore,
    space: String,
    cid: String,
    path: String,
    error: Throwable,
) {
    val saved = runCatching { store.getBlob(space, cid)?.storagePath == path }
    saved.exceptionOrNull()?.let(error::addSuppressed)
    if (saved.getOrNull() != false) return
    runCatching { deleteBlobIfExists(path) }.exceptionOrNull()?.let(error::addSuppressed)
}

/** Lowercase base32 multibase is all [Cid.parse] checks, so this is a shape check, not a full parse. */
private fun isCid(value: String): Boolean = value.length <= MAX_CID_LENGTH && value == value.trim() && Cid.parse(value).isSuccess

private fun isRecordKey(value: String): Boolean = RecordKey.parse(value).isSuccess

/** Up to [limit] bytes of the request body, or null when it's longer. */
private suspend fun ApplicationCall.receiveBytesUpTo(limit: Long): ByteArray? {
    val declared = request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (declared != null && declared > limit) return null
    val bytes = receiveChannel().readRemaining(limit + 1).readByteArray()
    return bytes.takeIf { it.size <= limit }
}

private inline fun <reified T> decodeOrNull(bytes: ByteArray): T? =
    try {
        spaceJson.decodeFromString<T>(bytes.decodeToString())
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

/** Stored records passed [checkSealedRecord] on the way in, so they always decode. */
private fun JsonObject.toSealed(): Sealed = spaceJson.decodeFromJsonElement(this)

private suspend fun ApplicationCall.respondNotImplemented() =
    respond(HttpStatusCode.NotImplemented, PdsErrorResponse("MethodNotImplemented", "Personal spaces aren't enabled on this server"))

private suspend fun ApplicationCall.respondMissing(code: String) = respond(HttpStatusCode.BadRequest, PdsErrorResponse(code, "Not found"))

private suspend fun ApplicationCall.respondInvalid(message: String) =
    respond(HttpStatusCode.BadRequest, PdsErrorResponse("InvalidRequest", message))

private suspend fun ApplicationCall.respondInvalidRecord(reason: String) =
    respond(HttpStatusCode.BadRequest, PdsErrorResponse("InvalidRecord", reason))

private suspend fun ApplicationCall.respondSwapConflict(error: SpaceSwapException) =
    respond(HttpStatusCode.Conflict, PdsErrorResponse("InvalidSwap", error.message ?: "The record changed"))
