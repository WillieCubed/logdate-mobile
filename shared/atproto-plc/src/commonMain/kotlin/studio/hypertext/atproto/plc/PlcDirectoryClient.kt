package studio.hypertext.atproto.plc

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.identity.DidDocument

/**
 * Reads PLC directory documents and submits PLC entries.
 */
public interface PlcDirectoryClient {
    /**
     * Reads the resolved DID document published at the PLC directory for [did].
     */
    public suspend fun getDocument(did: AtprotoDid): Result<DidDocument>

    /**
     * Reads the raw PLC operation log published for [did].
     */
    public suspend fun getOperationLog(did: AtprotoDid): Result<List<PlcLogEntry>>

    /**
     * Reads the indexed PLC audit log published for [did].
     */
    public suspend fun getAuditLog(did: AtprotoDid): Result<List<PlcIndexedOperation>>

    /**
     * Reads newline-delimited PLC export entries.
     */
    public suspend fun export(
        after: String? = null,
        count: Int? = null,
    ): Result<List<PlcIndexedOperation>>

    /**
     * Submits [entry] to the PLC directory for [did].
     */
    public suspend fun submit(
        did: AtprotoDid,
        entry: PlcLogEntry,
    ): Result<Unit>
}

/**
 * Ktor-backed PLC directory client.
 *
 * @property httpClient HTTP client used for requests.
 * @property baseUrl PLC directory base URL.
 * @property json JSON instance used for decoding and request encoding.
 */
public class KtorPlcDirectoryClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = true
            encodeDefaults = true
        },
) : PlcDirectoryClient {
    /**
     * Returns the PLC document URL for [did].
     */
    public fun documentUrlFor(did: AtprotoDid): String {
        requirePlcDid(did)
        return "${normalizedBaseUrl()}/$did"
    }

    /**
     * Returns the raw PLC log URL for [did].
     */
    public fun operationLogUrlFor(did: AtprotoDid): String {
        requirePlcDid(did)
        return "${normalizedBaseUrl()}/$did/log"
    }

    /**
     * Returns the PLC audit log URL for [did].
     */
    public fun auditLogUrlFor(did: AtprotoDid): String {
        requirePlcDid(did)
        return "${normalizedBaseUrl()}/$did/log/audit"
    }

    /**
     * Returns the PLC submit URL for [did].
     */
    public fun submitUrlFor(did: AtprotoDid): String {
        requirePlcDid(did)
        return "${normalizedBaseUrl()}/$did"
    }

    /**
     * Returns the PLC export URL for [after] and [count].
     */
    public fun exportUrl(
        after: String? = null,
        count: Int? = null,
    ): String {
        val normalizedBaseUrl = normalizedBaseUrl()
        val parameters =
            buildList {
                after?.let { add("after=$it") }
                count?.let { add("count=$it") }
            }
        if (parameters.isEmpty()) {
            return "$normalizedBaseUrl/export"
        }
        return "$normalizedBaseUrl/export?${parameters.joinToString("&")}"
    }

    override suspend fun getDocument(did: AtprotoDid): Result<DidDocument> =
        runCatching {
            val document =
                executeGet(
                    url = documentUrlFor(did),
                    serializer = DidDocument.serializer(),
                    failureLabel = "PLC document fetch",
                )
            require(document.id == did) { "Resolved DID document id mismatch" }
            document
        }

    override suspend fun getOperationLog(did: AtprotoDid): Result<List<PlcLogEntry>> =
        runCatching {
            executeGet(
                url = operationLogUrlFor(did),
                serializer = ListSerializer(PlcLogEntrySerializer),
                failureLabel = "PLC operation log fetch",
            )
        }

    override suspend fun getAuditLog(did: AtprotoDid): Result<List<PlcIndexedOperation>> =
        runCatching {
            executeGet(
                url = auditLogUrlFor(did),
                serializer = ListSerializer(PlcIndexedOperation.serializer()),
                failureLabel = "PLC audit log fetch",
            )
        }

    override suspend fun export(
        after: String?,
        count: Int?,
    ): Result<List<PlcIndexedOperation>> =
        runCatching {
            val response =
                httpClient.get("${normalizedBaseUrl()}/export") {
                    after?.let { parameter("after", it) }
                    count?.let { parameter("count", it) }
                }
            val body = response.bodyAsText()
            if (response.status != HttpStatusCode.OK) {
                throw PlcDirectoryException("PLC export failed with ${response.status.value}")
            }
            body
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { line -> decode(line, PlcIndexedOperation.serializer()) }
                .toList()
        }

    override suspend fun submit(
        did: AtprotoDid,
        entry: PlcLogEntry,
    ): Result<Unit> =
        runCatching {
            require(entry.isSigned) { "PLC entries must be signed before submission" }
            val rawEntry: String =
                when (entry) {
                    is PlcOperation -> json.encodeToString(PlcOperation.serializer(), entry)
                    is PlcTombstone -> json.encodeToString(PlcTombstone.serializer(), entry)
                }
            val response =
                httpClient.post(submitUrlFor(did)) {
                    contentType(ContentType.Application.Json)
                    setBody(rawEntry)
                }
            if (response.status.value !in 200..299) {
                throw PlcDirectoryException("PLC operation submission failed with ${response.status.value}")
            }
        }

    private suspend fun <T> executeGet(
        url: String,
        serializer: DeserializationStrategy<T>,
        failureLabel: String,
    ): T {
        val response = httpClient.get(url)
        val body = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            throw PlcDirectoryException("$failureLabel failed with ${response.status.value}")
        }
        return decode(body, serializer)
    }

    private fun <T> decode(
        body: String,
        serializer: DeserializationStrategy<T>,
    ): T =
        try {
            json.decodeFromString(serializer, body)
        } catch (exception: SerializationException) {
            throw PlcDirectoryException("Failed to decode PLC directory response", exception)
        }

    private fun normalizedBaseUrl(): String = baseUrl.trim().removeSuffix("/")

    private fun requirePlcDid(did: AtprotoDid) {
        require(did.method == PLC_METHOD) { "PLC directory only supports did:plc identifiers" }
    }

    public companion object {
        /**
         * Default public PLC directory base URL.
         */
        public const val DEFAULT_BASE_URL: String = "https://plc.directory"
    }
}

/**
 * PLC directory transport or decoding failure.
 */
public class PlcDirectoryException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

private const val PLC_METHOD: String = "plc"
