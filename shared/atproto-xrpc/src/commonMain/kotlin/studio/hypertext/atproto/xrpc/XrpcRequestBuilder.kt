package studio.hypertext.atproto.xrpc

/**
 * Mutable request customizer for XRPC calls.
 */
public class XrpcRequestBuilder {
    internal val queryParameters: MutableList<Pair<String, String>> = mutableListOf()
    internal val headers: MutableList<Pair<String, String>> = mutableListOf()

    /**
     * Authentication applied to the request.
     */
    public var auth: XrpcAuth = NoAuth

    /**
     * Adds a query parameter to the request.
     */
    public fun queryParameter(
        name: String,
        value: String,
    ) {
        queryParameters += name to value
    }

    /**
     * Adds a request header.
     */
    public fun header(
        name: String,
        value: String,
    ) {
        headers += name to value
    }
}
