package studio.hypertext.atproto.repo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import studio.hypertext.atproto.syntax.Tid
import kotlin.time.Instant

/**
 * Encodes and decodes the messages of `com.atproto.sync.subscribeRepos`.
 *
 * Every WebSocket message is two DAG-CBOR values back to back: a header that says what kind of
 * message follows, and the body. A header of `{op: 1, t: "#commit"}` announces an event, and
 * `{op: -1}` announces an error after which the sender closes the stream.
 */
public object RepoEventFrames {
    /**
     * Writes [message] as one stream frame.
     */
    public fun encode(message: RepoStreamMessage): ByteArray {
        val (header, body) = split(message)
        return DagCborCodec.encode(header) + DagCborCodec.encode(body)
    }

    /**
     * Reads one stream frame.
     *
     * @throws IllegalArgumentException when [frame] is not a frame this library understands, such
     * as a message type a newer protocol version added.
     */
    public fun decode(frame: ByteArray): RepoStreamMessage {
        val values = DagCborCodec.decodeSequence(frame)
        require(values.size == FRAME_VALUE_COUNT) { "A stream frame holds a header and a body, found ${values.size} values" }
        val header = values[0].jsonObject
        val body = values[1].jsonObject
        val operation = header.getValue("op").jsonPrimitive.longOrNull
        if (operation == ERROR_OP) {
            return RepoErrorMessage(
                error = body.getValue("error").jsonPrimitive.content,
                message = body.optionalString("message"),
            )
        }
        require(operation == MESSAGE_OP) { "Unknown stream frame op $operation" }
        return when (val type = header.getValue("t").jsonPrimitive.content) {
            COMMIT_TYPE -> decodeCommit(body)
            SYNC_TYPE -> decodeSync(body)
            IDENTITY_TYPE -> decodeIdentity(body)
            ACCOUNT_TYPE -> decodeAccount(body)
            INFO_TYPE -> RepoInfoMessage(name = body.getValue("name").jsonPrimitive.content, message = body.optionalString("message"))
            else -> throw IllegalArgumentException("Unknown stream message type $type")
        }
    }

    private fun split(message: RepoStreamMessage): Pair<JsonObject, JsonObject> =
        when (message) {
            is RepoCommitEvent -> eventHeader(COMMIT_TYPE) to commitBody(message)
            is RepoSyncEvent -> eventHeader(SYNC_TYPE) to syncBody(message)
            is RepoIdentityEvent -> eventHeader(IDENTITY_TYPE) to identityBody(message)
            is RepoAccountEvent -> eventHeader(ACCOUNT_TYPE) to accountBody(message)
            is RepoInfoMessage -> eventHeader(INFO_TYPE) to infoBody(message)
            is RepoErrorMessage -> errorHeader() to errorBody(message)
        }

    private fun eventHeader(type: String): JsonObject =
        buildJsonObject {
            put("op", MESSAGE_OP)
            put("t", type)
        }

    private fun errorHeader(): JsonObject = buildJsonObject { put("op", ERROR_OP) }

    private fun commitBody(event: RepoCommitEvent): JsonObject =
        buildJsonObject {
            put("seq", event.seq)
            put("rebase", false)
            put("tooBig", false)
            put("repo", event.repo.toString())
            put("commit", DagCborCodec.link(event.commit))
            put("rev", event.rev.toString())
            put("since", event.since?.toString()?.let(::JsonPrimitive) ?: JsonNull)
            put("blocks", DagCborCodec.bytes(event.blocks))
            put("ops", buildJsonArray { event.ops.forEach { add(operationBody(it)) } })
            put("blobs", JsonArray(emptyList()))
            event.prevData?.let { put("prevData", DagCborCodec.link(it)) }
            put("time", event.time.toString())
        }

    private fun operationBody(operation: RepoOperation): JsonObject =
        buildJsonObject {
            put("action", operation.action.wireName)
            put("path", operation.path)
            put("cid", operation.cid?.let(DagCborCodec::link) ?: JsonNull)
            operation.prev?.let { put("prev", DagCborCodec.link(it)) }
        }

    private fun syncBody(event: RepoSyncEvent): JsonObject =
        buildJsonObject {
            put("seq", event.seq)
            put("did", event.did.toString())
            put("blocks", DagCborCodec.bytes(event.blocks))
            put("rev", event.rev.toString())
            put("time", event.time.toString())
        }

    private fun identityBody(event: RepoIdentityEvent): JsonObject =
        buildJsonObject {
            put("seq", event.seq)
            put("did", event.did.toString())
            put("time", event.time.toString())
            event.handle?.let { put("handle", it) }
        }

    private fun accountBody(event: RepoAccountEvent): JsonObject =
        buildJsonObject {
            put("seq", event.seq)
            put("did", event.did.toString())
            put("time", event.time.toString())
            put("active", event.active)
            event.status?.let { put("status", it) }
        }

    private fun infoBody(info: RepoInfoMessage): JsonObject =
        buildJsonObject {
            put("name", info.name)
            info.message?.let { put("message", it) }
        }

    private fun errorBody(error: RepoErrorMessage): JsonObject =
        buildJsonObject {
            put("error", error.error)
            error.message?.let { put("message", it) }
        }

    private fun decodeCommit(body: JsonObject): RepoCommitEvent =
        RepoCommitEvent(
            seq = body.getValue("seq").jsonPrimitive.long(),
            time = body.time(),
            repo = AtprotoDid.require(body.getValue("repo").jsonPrimitive.content),
            commit = body.link("commit"),
            rev = Tid.require(body.getValue("rev").jsonPrimitive.content),
            since =
                body["since"]
                    ?.takeUnless { it is JsonNull }
                    ?.jsonPrimitive
                    ?.content
                    ?.let(Tid::require),
            blocks = body.bytes("blocks"),
            ops = body.getValue("ops").jsonArray.map { decodeOperation(it.jsonObject) },
            prevData = body["prevData"]?.takeUnless { it is JsonNull }?.let { requireNotNull(DagCborCodec.linkOrNull(it)) },
        )

    private fun decodeOperation(body: JsonObject): RepoOperation {
        val actionName = body.getValue("action").jsonPrimitive.content
        val path = body.getValue("path").jsonPrimitive.content
        val parts = path.split('/', limit = 2)
        require(parts.size == 2) { "Operation path must be collection/recordKey, got $path" }
        return RepoOperation(
            action = requireNotNull(RepoOperationAction.fromWireName(actionName)) { "Unknown operation action $actionName" },
            collection = Nsid.require(parts[0]),
            recordKey = RecordKey.require(parts[1]),
            cid = body["cid"]?.takeUnless { it is JsonNull }?.let { requireNotNull(DagCborCodec.linkOrNull(it)) },
            prev = body["prev"]?.takeUnless { it is JsonNull }?.let { requireNotNull(DagCborCodec.linkOrNull(it)) },
        )
    }

    private fun decodeSync(body: JsonObject): RepoSyncEvent =
        RepoSyncEvent(
            seq = body.getValue("seq").jsonPrimitive.long(),
            time = body.time(),
            did = AtprotoDid.require(body.getValue("did").jsonPrimitive.content),
            rev = Tid.require(body.getValue("rev").jsonPrimitive.content),
            blocks = body.bytes("blocks"),
        )

    private fun decodeIdentity(body: JsonObject): RepoIdentityEvent =
        RepoIdentityEvent(
            seq = body.getValue("seq").jsonPrimitive.long(),
            time = body.time(),
            did = AtprotoDid.require(body.getValue("did").jsonPrimitive.content),
            handle = body.optionalString("handle"),
        )

    private fun decodeAccount(body: JsonObject): RepoAccountEvent =
        RepoAccountEvent(
            seq = body.getValue("seq").jsonPrimitive.long(),
            time = body.time(),
            did = AtprotoDid.require(body.getValue("did").jsonPrimitive.content),
            active = requireNotNull(body.getValue("active").jsonPrimitive.booleanOrNull) { "An account event's active must be a boolean" },
            status = body.optionalString("status"),
        )

    private fun JsonPrimitive.long(): Long = requireNotNull(longOrNull) { "Expected an integer, found $content" }

    private fun JsonObject.time(): Instant = Instant.parse(getValue("time").jsonPrimitive.content)

    private fun JsonObject.optionalString(name: String): String? = this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull

    private fun JsonObject.link(name: String): Cid = requireNotNull(DagCborCodec.linkOrNull(getValue(name))) { "$name must be a CID link" }

    private fun JsonObject.bytes(name: String): ByteArray =
        requireNotNull(DagCborCodec.bytesOrNull(getValue(name))) { "$name must be bytes" }

    private const val FRAME_VALUE_COUNT: Int = 2
    private const val MESSAGE_OP: Long = 1L
    private const val ERROR_OP: Long = -1L
    private const val COMMIT_TYPE: String = "#commit"
    private const val SYNC_TYPE: String = "#sync"
    private const val IDENTITY_TYPE: String = "#identity"
    private const val ACCOUNT_TYPE: String = "#account"
    private const val INFO_TYPE: String = "#info"
}
