package studio.hypertext.atproto.xrpc

/**
 * Base exception for XRPC request failures.
 */
public sealed class XrpcException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * Thrown when an XRPC request fails before a protocol response is received.
 */
public class XrpcTransportException : XrpcException {
    public constructor(message: String) : super(message)

    public constructor(message: String, cause: Throwable?) : super(message, cause)
}

/**
 * Thrown when an XRPC server returns a non-success HTTP status.
 *
 * @property statusCode HTTP status code.
 * @property error Optional protocol error code.
 * @property errorMessage Optional protocol error message.
 * @property retryAfterSeconds How long the server asked the caller to wait before trying again, from
 *   a `Retry-After` header given in seconds; null when it gave none, or gave a date instead.
 */
public class XrpcProtocolException(
    public val statusCode: Int,
    public val error: String? = null,
    public val errorMessage: String? = null,
    public val retryAfterSeconds: Long? = null,
) : XrpcException(
        buildString {
            append("XRPC request failed with HTTP ")
            append(statusCode)
            error?.let {
                append(" [")
                append(it)
                append(']')
            }
            errorMessage?.let {
                append(": ")
                append(it)
            }
        },
    )

/**
 * Thrown when an XRPC payload cannot be serialized or deserialized.
 */
public class XrpcSerializationException : XrpcException {
    public constructor(message: String) : super(message)

    public constructor(message: String, cause: Throwable?) : super(message, cause)
}
