package app.logdate.server

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.util.AttributeKey

private val ClientAddressKey = AttributeKey<String>("LogDateClientAddress")

/**
 * The address of the client that sent this call. Behind a trusted proxy it is the address that proxy
 * recorded in `X-Forwarded-For`; otherwise it is the connection's peer. Rate limits key on it.
 */
internal fun ApplicationCall.clientAddress(): String = attributes.getOrNull(ClientAddressKey) ?: request.local.remoteHost

/**
 * Reads `TRUSTED_PROXY_HOPS`: unset or blank is `0`. Anything that is not a whole number of zero or
 * more stops the server, because guessing would either let clients choose their own address or put
 * every visitor in one rate limit.
 */
internal fun parseTrustedProxyHops(raw: String?): Int {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return 0
    val hops = requireNotNull(value.toIntOrNull()) { "TRUSTED_PROXY_HOPS must be a whole number, not '$value'" }
    require(hops >= 0) { "TRUSTED_PROXY_HOPS must not be negative" }
    return hops
}

/**
 * Picks the client's address from every `X-Forwarded-For` header line, joined in the order they
 * arrived. Each proxy appends the address it received the request from, so the last entry is the one
 * the nearest trusted proxy saw; [trustedProxyHops] skips further trusted proxies that append after
 * it. Entries before that came from the client and may be invented. Returns `null` when there are
 * not enough entries to reach a trusted one.
 */
internal fun forwardedClientAddress(
    headerLines: List<String>,
    trustedProxyHops: Int,
): String? {
    val entries =
        headerLines
            .flatMap { it.split(',') }
            .map(String::trim)
            .filter(String::isNotEmpty)
    return entries.getOrNull(entries.size - 1 - trustedProxyHops)
}

/**
 * Trusts the proxy in front of the server for two things only: the scheme it received the request
 * over, and the client address it recorded. A client-sent `X-Forwarded-Host` or `X-Forwarded-Port` is
 * ignored, so nothing a client writes can change the URL the server believes it was called at.
 */
internal fun Application.installForwardedClientAddress(trustedProxyHops: Int = 0) {
    require(trustedProxyHops >= 0) { "TRUSTED_PROXY_HOPS must not be negative" }
    install(XForwardedHeaders) {
        extractEdgeProxy { origin, values ->
            values.protoHeader
                ?.substringAfterLast(',')
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { origin.scheme = it }
        }
    }
    intercept(ApplicationCallPipeline.Setup) {
        val lines =
            call.request.headers
                .getAll(HttpHeaders.XForwardedFor)
                .orEmpty()
        forwardedClientAddress(lines, trustedProxyHops)?.let { call.attributes.put(ClientAddressKey, it) }
    }
}
