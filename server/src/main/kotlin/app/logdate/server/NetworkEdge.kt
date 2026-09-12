package app.logdate.server

import app.logdate.server.config.profileAwareBoolEnv
import io.github.aakira.napier.Napier
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.httpsredirect.HttpsRedirect
import java.net.URI

/**
 * Installs the CORS, forwarded-headers, and HTTPS-redirect plugins based on environment
 * configuration.
 *
 * Knobs:
 *  - `ALLOWED_ORIGINS` — comma-separated `scheme://host[:port]` entries trusted for CORS. Unset or
 *    blank disables the CORS plugin entirely (same-origin only). No default in any profile.
 *  - `TRUST_FORWARDED_HEADERS` — when `true`, installs [XForwardedHeaders] so the request's
 *    `scheme` / `remoteHost` reflect what the load balancer saw from the client rather than what
 *    the LB's backend socket looks like. **Defaults to `true` in production** (the typical deploy
 *    is behind Cloud Run / an ALB / GKE Ingress / Cloudflare, all of which forward plain HTTP to
 *    the container). Set `false` when the app is directly internet-facing — otherwise a malicious
 *    client could lie about their scheme with `X-Forwarded-Proto: https`.
 *  - `REQUIRE_HTTPS` — when `true`, installs [HttpsRedirect]. **Defaults to `true` in production.**
 *    Combined with [XForwardedHeaders] above, a request the LB received over HTTPS will pass
 *    through (scheme reads as `https`) while a request the LB received over HTTP gets 301'd. With
 *    [HttpsRedirect], any request that comes in as `http` (but not from `localhost` or `127.0.0.1`)
 *    is redirected to `https`.
 */
internal fun Application.installNetworkEdge(
    allowedOrigins: String = System.getenv("ALLOWED_ORIGINS") ?: "",
    trustForwarded: Boolean = profileAwareBoolEnv("TRUST_FORWARDED_HEADERS", productionDefault = true, devDefault = true),
    requireHttps: Boolean = profileAwareBoolEnv("REQUIRE_HTTPS", productionDefault = true, devDefault = false),
) {
    val parsedOrigins = parseAllowedOrigins(allowedOrigins)
    if (parsedOrigins.isNotEmpty()) {
        install(CORS) {
            parsedOrigins.forEach { origin ->
                val hostAndPort =
                    buildString {
                        append(origin.host)
                        origin.port?.let { append(":").append(it) }
                    }
                allowHost(host = hostAndPort, schemes = listOf(origin.scheme))
            }
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Patch)
            allowMethod(HttpMethod.Delete)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowCredentials = true
        }
    }

    if (trustForwarded) {
        install(XForwardedHeaders)
    }

    if (requireHttps) {
        install(HttpsRedirect)
    }
}

/**
 * Parses a comma-separated list of origin URIs into a list of [AllowedOrigin] structures.
 * Malformed entries are logged and skipped.
 */
internal fun parseAllowedOrigins(raw: String?): List<AllowedOrigin> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { originStr ->
            runCatching {
                val uri = URI(originStr)
                requireNotNull(uri.scheme) { "Missing scheme in origin: $originStr" }
                requireNotNull(uri.host) { "Missing host in origin: $originStr" }
                AllowedOrigin(
                    scheme = uri.scheme,
                    host = uri.host,
                    port = if (uri.port != -1) uri.port else null,
                )
            }.getOrElse { error ->
                Napier.w("Skipping malformed ALLOWED_ORIGINS entry: $originStr", error)
                null
            }
        }
}

/**
 * Structured breakdown of a trusted origin for CORS configuration.
 */
internal data class AllowedOrigin(
    val scheme: String,
    val host: String,
    val port: Int? = null,
)
