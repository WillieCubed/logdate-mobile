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
import studio.hypertext.atproto.xrpc.XrpcProtocolException
import studio.hypertext.atproto.xrpc.procedure
import studio.hypertext.atproto.xrpc.query

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
            client.query<ListRepoOpsOutput>(LIST) {
                auth = credentials
                queryParameter("space", space.uri)
                queryParameter("since", since.toString())
                queryParameter("limit", limit.toString())
            }
        val changes = output.ops.map { RecordChange(it.rkey, it.seq, it.deleted, it.cid, it.value) }
        return ChangePage(changes, output.cursor, output.hasMore)
    }

    override suspend fun uploadBlob(
        space: SpaceRef,
        bytes: ByteArray,
    ): String {
        val response =
            httpClient.post(url(UPLOAD_BLOB)) {
                parameter("space", space.uri)
                bearer(auth())
                contentType(ContentType.Application.OctetStream)
                setBody(bytes)
            }
        if (!response.status.isSuccess()) throw response.failure()
        return json
            .decodeFromString<UploadBlobOutput>(response.bodyAsText())
            .blob.ref.cid.value
    }

    override suspend fun getBlob(
        space: SpaceRef,
        cid: String,
    ): ByteArray? {
        val response =
            httpClient.get(url(GET_BLOB)) {
                parameter("space", space.uri)
                parameter("cid", cid)
                bearer(auth())
            }
        if (response.status.isSuccess()) return response.readRawBytes()
        val failure = response.failure()
        if (failure.error == BLOB_NOT_FOUND) return null
        throw failure
    }

    private fun url(nsid: Nsid): String = "${baseUrl.trimEnd('/')}/xrpc/${nsid.value}"

    private fun HttpRequestBuilder.bearer(credentials: XrpcAuth) {
        if (credentials is BearerToken) header(HttpHeaders.Authorization, "Bearer ${credentials.token}")
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
        val PUT = Nsid.require("app.logdate.space.putRecord")
        val DELETE = Nsid.require("app.logdate.space.deleteRecord")
        val GET = Nsid.require("app.logdate.space.getRecord")
        val LIST = Nsid.require("app.logdate.space.listRepoOps")
        val UPLOAD_BLOB = Nsid.require("app.logdate.space.uploadBlob")
        val GET_BLOB = Nsid.require("app.logdate.space.getBlob")
    }
}
