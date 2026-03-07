package studio.hypertext.atproto.xrpc

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer
import studio.hypertext.atproto.syntax.Nsid

/**
 * Low-level client for executing AT Protocol XRPC requests.
 */
public interface XrpcClient {
    /**
     * Executes a query request and returns the raw response body.
     */
    public suspend fun queryRaw(
        nsid: Nsid,
        block: XrpcRequestBuilder.() -> Unit = {},
    ): String

    /**
     * Executes a query request and decodes the response with [deserializer].
     */
    public suspend fun <Response> query(
        nsid: Nsid,
        deserializer: DeserializationStrategy<Response>,
        block: XrpcRequestBuilder.() -> Unit = {},
    ): Response

    /**
     * Executes a procedure request and returns the raw response body.
     */
    public suspend fun procedureRaw(
        nsid: Nsid,
        body: String? = null,
        block: XrpcRequestBuilder.() -> Unit = {},
    ): String

    /**
     * Executes a procedure request and decodes the response with [deserializer].
     */
    public suspend fun <Body, Response> procedure(
        nsid: Nsid,
        body: Body,
        serializer: SerializationStrategy<Body>,
        deserializer: DeserializationStrategy<Response>,
        block: XrpcRequestBuilder.() -> Unit = {},
    ): Response
}

/**
 * Executes a query request using reified serializers.
 */
@OptIn(ExperimentalSerializationApi::class)
public suspend inline fun <reified Response> XrpcClient.query(
    nsid: Nsid,
    noinline block: XrpcRequestBuilder.() -> Unit = {},
): Response = query(nsid, serializer<Response>(), block)

/**
 * Executes a procedure request using reified serializers.
 */
@OptIn(ExperimentalSerializationApi::class)
public suspend inline fun <reified Body, reified Response> XrpcClient.procedure(
    nsid: Nsid,
    body: Body,
    noinline block: XrpcRequestBuilder.() -> Unit = {},
): Response = procedure(nsid, body, serializer<Body>(), serializer<Response>(), block)
