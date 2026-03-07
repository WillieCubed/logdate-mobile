package app.logdate.atproto.xrpc

import app.logdate.atproto.syntax.Nsid
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json

/**
 * Ktor-backed [XrpcClient] implementation.
 *
 * @property httpClient HTTP client used for requests.
 * @property baseUrl Base service URL, for example `https://public.api.bsky.app`.
 * @property json JSON instance used for request and response encoding.
 */
public class KtorXrpcClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : XrpcClient {
    override suspend fun queryRaw(
        nsid: Nsid,
        block: XrpcRequestBuilder.() -> Unit,
    ): String {
        val builder = XrpcRequestBuilder().apply(block)

        return executeAndRead {
            httpClient.get("${baseUrl.trimEnd('/')}/xrpc/${nsid.value}") {
                applyBuilder(builder)
            }
        }
    }

    override suspend fun <Response> query(
        nsid: Nsid,
        deserializer: DeserializationStrategy<Response>,
        block: XrpcRequestBuilder.() -> Unit,
    ): Response {
        val body: String = queryRaw(nsid, block)
        return decode(body, deserializer)
    }

    override suspend fun procedureRaw(
        nsid: Nsid,
        body: String?,
        block: XrpcRequestBuilder.() -> Unit,
    ): String {
        val builder = XrpcRequestBuilder().apply(block)

        return executeAndRead {
            httpClient.post("${baseUrl.trimEnd('/')}/xrpc/${nsid.value}") {
                applyBuilder(builder)
                contentType(ContentType.Application.Json)
                if (body != null) {
                    setBody(body)
                }
            }
        }
    }

    override suspend fun <Body, Response> procedure(
        nsid: Nsid,
        body: Body,
        serializer: SerializationStrategy<Body>,
        deserializer: DeserializationStrategy<Response>,
        block: XrpcRequestBuilder.() -> Unit,
    ): Response {
        val rawBody: String = json.encodeToString(serializer, body)
        val responseBody: String = procedureRaw(nsid, rawBody, block)
        return decode(responseBody, deserializer)
    }

    private suspend fun executeAndRead(block: suspend () -> HttpResponse): String =
        try {
            val response = block()
            val body = response.bodyAsText()
            if (response.status.value !in 200..299) {
                throw protocolException(response.status, body)
            }
            body
        } catch (exception: XrpcException) {
            throw exception
        } catch (exception: Exception) {
            throw XrpcTransportException("XRPC request failed", exception)
        }

    private fun io.ktor.client.request.HttpRequestBuilder.applyBuilder(builder: XrpcRequestBuilder) {
        builder.queryParameters.forEach { (name, value) ->
            parameter(name, value)
        }
        builder.headers.forEach { (name, value) ->
            header(name, value)
        }
        when (val auth = builder.auth) {
            is BearerToken -> header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            NoAuth -> Unit
        }
    }

    private fun <Response> decode(
        body: String,
        deserializer: DeserializationStrategy<Response>,
    ): Response =
        try {
            json.decodeFromString(deserializer, body)
        } catch (exception: SerializationException) {
            throw XrpcSerializationException("Failed to decode XRPC response", exception)
        }

    private fun protocolException(
        status: HttpStatusCode,
        body: String,
    ): XrpcProtocolException {
        val errorPayload: XrpcErrorPayload? =
            try {
                json.decodeFromString(XrpcErrorPayload.serializer(), body)
            } catch (_: SerializationException) {
                null
            }

        return XrpcProtocolException(
            statusCode = status.value,
            error = errorPayload?.error,
            errorMessage = errorPayload?.message,
        )
    }
}

@Serializable
private data class XrpcErrorPayload(
    val error: String? = null,
    val message: String? = null,
)
