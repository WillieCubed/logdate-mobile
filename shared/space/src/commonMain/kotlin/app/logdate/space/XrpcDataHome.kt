package app.logdate.space

import app.logdate.lexicon.app.logdate.crypto.Sealed
import app.logdate.lexicon.app.logdate.space.DeleteRecordInput
import app.logdate.lexicon.app.logdate.space.DeleteRecordOutput
import app.logdate.lexicon.app.logdate.space.GetRecordOutput
import app.logdate.lexicon.app.logdate.space.ListRepoOpsOutput
import app.logdate.lexicon.app.logdate.space.PutRecordInput
import app.logdate.lexicon.app.logdate.space.PutRecordOutput
import app.logdate.lexicon.app.logdate.space.UploadBlobOutput
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.xrpc.BearerToken
import studio.hypertext.atproto.xrpc.XrpcAuth
import studio.hypertext.atproto.xrpc.XrpcClient
import studio.hypertext.atproto.xrpc.XrpcException
import studio.hypertext.atproto.xrpc.XrpcProtocolException
import studio.hypertext.atproto.xrpc.XrpcSerializationException
import studio.hypertext.atproto.xrpc.XrpcTransportException
import studio.hypertext.atproto.xrpc.procedure
import studio.hypertext.atproto.xrpc.query
import kotlin.coroutines.cancellation.CancellationException

/**
 * LogDate Cloud, or a self-hosted LogDate server, as a [DataHome].
 *
 * @param client sends the JSON XRPC methods.
 * @param httpClient sends the blob methods, which carry raw bytes instead of JSON.
 * @param baseUrl the server's base URL; must be the one [client] is configured with.
 * @param auth supplies the credentials for each request.
 */
public class XrpcDataHome(
    private val client: XrpcClient,
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val auth: suspend () -> XrpcAuth,
) : DataHome {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun putRecord(
        space: SpaceRef,
        rkey: String,
        record: Sealed,
        blobs: List<String>,
        swap: RecordSwap,
    ): StoredRecord {
        val input =
            PutRecordInput(
                space = space.uri,
                rkey = rkey,
                record = record,
                blobs = blobs.ifEmpty { null },
                swapCid = (swap as? RecordSwap.Matching)?.cid,
                createOnly = if (swap == RecordSwap.Absent) true else null,
            )
        val credentials = auth()
        val output =
            conflicts {
                client.procedure<PutRecordInput, PutRecordOutput>(PUT, input) { auth = credentials }
            }
        return StoredRecord(rkey, output.cid, output.seq, record)
    }

    override suspend fun deleteRecord(
        space: SpaceRef,
        rkey: String,
        swap: RecordSwap,
    ): Long {
        require(swap != RecordSwap.Absent) { "A delete can't require the record to be absent" }
        val input = DeleteRecordInput(space = space.uri, rkey = rkey, swapCid = (swap as? RecordSwap.Matching)?.cid)
        val credentials = auth()
        return conflicts {
            client.procedure<DeleteRecordInput, DeleteRecordOutput>(DELETE, input) { auth = credentials }
        }.seq
    }

    override suspend fun getRecord(
        space: SpaceRef,
        rkey: String,
    ): StoredRecord? {
        val credentials = auth()
        val output =
            try {
                client.query<GetRecordOutput>(GET) {
                    auth = credentials
                    queryParameter("space", space.uri)
                    queryParameter("rkey", rkey)
                }
            } catch (e: XrpcProtocolException) {
                if (e.error == RECORD_NOT_FOUND) return null
                throw e
            }
        return StoredRecord(rkey, output.cid, output.seq, output.value)
    }

    override suspend fun listChanges(
        space: SpaceRef,
        since: Long,
        limit: Int,
    ): ChangePage {
        val credentials = auth()
        val output =
            try {
                client.query<ListRepoOpsOutput>(LIST) {
                    auth = credentials
                    queryParameter("space", space.uri)
                    queryParameter("since", since.toString())
                    queryParameter("limit", limit.toString())
                }
            } catch (e: XrpcProtocolException) {
                if (e.error == CURSOR_TOO_OLD) throw DataHomeCursorTooOldException(e.errorMessage ?: "List again from 0")
                throw e
            }
        val changes = output.ops.map { RecordChange(it.rkey, it.seq, it.deleted, it.cid, it.value) }
        return ChangePage(changes, output.cursor, output.hasMore)
    }

    /**
     * @throws XrpcTransportException if the request fails before a response arrives.
     * @throws XrpcSerializationException if the response isn't a blob reference.
     * @throws IllegalStateException if [auth] supplies no bearer token.
     */
    override suspend fun uploadBlob(
        space: SpaceRef,
        bytes: ByteArray,
    ): String {
        val token = bearerToken(auth())
        val body =
            transport {
                val response =
                    httpClient.post(url(UPLOAD_BLOB)) {
                        parameter("space", space.uri)
                        bearer(token)
                        contentType(ContentType.Application.OctetStream)
                        setBody(bytes)
                    }
                if (!response.status.isSuccess()) throw response.failure()
                response.bodyAsText()
            }
        return try {
            json
                .decodeFromString<UploadBlobOutput>(body)
                .blob.ref.cid.value
        } catch (e: IllegalArgumentException) {
            throw XrpcSerializationException("Failed to decode the uploadBlob response", e)
        }
    }

    /**
     * @throws XrpcTransportException if the request fails before a response arrives.
     * @throws IllegalStateException if [auth] supplies no bearer token.
     */
    override suspend fun getBlob(
        space: SpaceRef,
        cid: String,
    ): ByteArray? {
        val token = bearerToken(auth())
        return transport {
            val response =
                httpClient.get(url(GET_BLOB)) {
                    parameter("space", space.uri)
                    parameter("cid", cid)
                    bearer(token)
                }
            if (response.status.isSuccess()) return@transport response.readRawBytes()
            val failure = response.failure()
            if (failure.error == BLOB_NOT_FOUND) return@transport null
            throw failure
        }
    }

    private fun url(nsid: Nsid): String = "${baseUrl.trimEnd('/')}/xrpc/${nsid.value}"

    /** The blob methods bypass [client] and sign their own requests, so they refuse to go out unsigned. */
    private fun bearerToken(credentials: XrpcAuth): String {
        check(credentials is BearerToken) { "The space blob methods need a bearer token" }
        return credentials.token
    }

    private fun HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    /** Runs a blob request, reporting a failure before any response as [XrpcTransportException], as [client] does. */
    private inline fun <T> transport(block: () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: XrpcException) {
            throw e
        } catch (e: Exception) {
            throw XrpcTransportException("XRPC request failed", e)
        }

    private suspend fun HttpResponse.failure(): XrpcProtocolException {
        val payload = runCatching { json.parseToJsonElement(bodyAsText()).jsonObject }.getOrNull()
        return XrpcProtocolException(
            statusCode = status.value,
            error = payload?.get("error")?.jsonPrimitive?.contentOrNull,
            errorMessage = payload?.get("message")?.jsonPrimitive?.contentOrNull,
        )
    }

    private inline fun <T> conflicts(block: () -> T): T =
        try {
            block()
        } catch (e: XrpcProtocolException) {
            if (e.error == INVALID_SWAP) throw DataHomeConflictException(e.errorMessage ?: "The record changed")
            throw e
        }

    private companion object {
        const val INVALID_SWAP = "InvalidSwap"
        const val RECORD_NOT_FOUND = "RecordNotFound"
        const val BLOB_NOT_FOUND = "BlobNotFound"
        const val CURSOR_TOO_OLD = "CursorTooOld"
        val PUT = Nsid.require("app.logdate.space.putRecord")
        val DELETE = Nsid.require("app.logdate.space.deleteRecord")
        val GET = Nsid.require("app.logdate.space.getRecord")
        val LIST = Nsid.require("app.logdate.space.listRepoOps")
        val UPLOAD_BLOB = Nsid.require("app.logdate.space.uploadBlob")
        val GET_BLOB = Nsid.require("app.logdate.space.getBlob")
    }
}
