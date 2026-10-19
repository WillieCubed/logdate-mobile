package studio.hypertext.atproto.oauth.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.DEFAULT_PORT
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.identity.DidDocument
import studio.hypertext.atproto.identity.IdentityResolver
import studio.hypertext.atproto.syntax.Handle

/**
 * An account identity that has been resolved and, for handles, bidirectionally verified.
 *
 * @property did The account DID.
 * @property handle The verified handle, when the input was a handle.
 * @property pdsUrl The account's data server URL from its DID document.
 */
public data class OAuthResolvedIdentity(
    val did: AtprotoDid,
    val handle: Handle?,
    val pdsUrl: String,
)

/**
 * The authorization server a sign-in will use, plus the identity it was found from.
 *
 * @property identity The resolved account, or `null` when the sign-in started from a server URL.
 * @property metadata The validated authorization server metadata.
 */
public data class OAuthResolvedServer(
    val identity: OAuthResolvedIdentity?,
    val metadata: OAuthAuthorizationServerMetadata,
)

/**
 * Finds the authorization server for an account or server, following the "Identity
 * Authentication" and "Server Metadata" sections of the AT Protocol OAuth specification.
 *
 * Nothing is cached: sign-in flows are security-critical, and every token response is verified
 * against a fresh resolution.
 *
 * @param allowHttp Accept `http://` data servers and authorization servers. Only for local
 *   development.
 */
public class OAuthServerResolver(
    private val identityResolver: IdentityResolver,
    private val httpClient: HttpClient,
    private val allowHttp: Boolean = false,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Resolves [input] — a handle, a DID, or an `https://` data server or entryway URL — to its
     * authorization server.
     */
    public suspend fun resolve(input: String): OAuthResolvedServer {
        val trimmed = input.trim()
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            return OAuthResolvedServer(identity = null, metadata = resolveFromService(trimmed))
        }
        val identity = resolveIdentity(trimmed)
        return OAuthResolvedServer(identity = identity, metadata = authorizationServerForPds(identity.pdsUrl))
    }

    /**
     * Resolves a handle or DID to its DID document and data server. Handles are verified in both
     * directions: the handle must point at the DID, and the DID document must claim the handle.
     */
    public suspend fun resolveIdentity(input: String): OAuthResolvedIdentity {
        val identifier = input.trim().removePrefix("@")
        if (identifier.startsWith("did:")) {
            val did =
                AtprotoDid
                    .parse(identifier)
                    .getOrElse { throw OAuthIdentityResolutionException(input, "not a supported AT Protocol DID", it) }
            return OAuthResolvedIdentity(did = did, handle = null, pdsUrl = pdsUrl(did, resolveDocument(input, did)))
        }

        val handle =
            Handle
                .parse(identifier)
                .getOrElse { throw OAuthIdentityResolutionException(input, "not a valid handle or DID", it) }
        val did =
            identityResolver
                .resolveDid(handle)
                .getOrElse { throw OAuthIdentityResolutionException(input, "handle resolution failed", it) }
        val document = resolveDocument(input, did)
        val claimsHandle = document.alsoKnownAs.any { it.equals("at://${handle.normalized}", ignoreCase = true) }
        if (!claimsHandle) throw OAuthHandleMismatchException(handle.normalized, did)
        return OAuthResolvedIdentity(did = did, handle = handle, pdsUrl = pdsUrl(did, document))
    }

    /**
     * Resolves the authorization server currently responsible for [did], ignoring any cache.
     *
     * The client calls this after every token response to confirm the issuer really speaks for
     * the account.
     *
     * @return The account's data server URL, paired with its authorization server metadata.
     */
    public suspend fun resolveForDid(did: AtprotoDid): Pair<String, OAuthAuthorizationServerMetadata> {
        val pds = pdsUrl(did, resolveDocument(did.toString(), did))
        return pds to authorizationServerForPds(pds)
    }

    /**
     * Fetches the protected resource metadata of [pdsUrl] and the authorization server it names.
     *
     * A data server that does not publish protected resource metadata (HTTP 404) is treated as its
     * own authorization server.
     */
    public suspend fun authorizationServerForPds(pdsUrl: String): OAuthAuthorizationServerMetadata {
        val origin = webOrigin(pdsUrl, "data server URL")
        val resourceMetadata = protectedResourceMetadata(origin) ?: return authorizationServerMetadata(origin)
        val issuer =
            resourceMetadata.authorizationServers.singleOrNull()
                ?: throw OAuthServerMetadataException(
                    origin,
                    "expected exactly one authorization server, found ${resourceMetadata.authorizationServers.size}",
                )
        val metadata = authorizationServerMetadata(issuer)
        val protectedResources = metadata.protectedResources
        if (protectedResources != null && resourceMetadata.resource !in protectedResources) {
            throw OAuthServerMetadataException(issuer, "does not list $origin among its protected resources")
        }
        return metadata
    }

    /**
     * Fetches and validates the metadata of the authorization server [issuer].
     */
    public suspend fun authorizationServerMetadata(issuer: String): OAuthAuthorizationServerMetadata {
        val origin = webOrigin(issuer, "authorization server issuer")
        if (origin != issuer) {
            throw OAuthServerMetadataException(issuer, "an issuer must be a bare origin without a path or default port")
        }
        val url = "$origin/.well-known/oauth-authorization-server"
        val metadata =
            fetchJson(url, OAuthAuthorizationServerMetadata.serializer())
                ?: throw OAuthServerMetadataException(url, "HTTP 404")
        if (metadata.issuer != issuer) {
            throw OAuthServerMetadataException(url, "issuer is ${metadata.issuer}, expected $issuer")
        }
        val violations = atprotoProfileViolations(metadata, allowHttp)
        if (violations.isNotEmpty()) {
            throw OAuthServerMetadataException(url, "not an AT Protocol OAuth server: ${violations.joinToString("; ")}")
        }
        return metadata
    }

    private suspend fun resolveFromService(url: String): OAuthAuthorizationServerMetadata {
        val origin = webOrigin(url, "server URL")
        val resourceFailure =
            try {
                return authorizationServerForPds(origin)
            } catch (exception: OAuthServerMetadataException) {
                exception
            }
        return try {
            authorizationServerMetadata(origin)
        } catch (exception: OAuthServerMetadataException) {
            resourceFailure.addSuppressed(exception)
            throw resourceFailure
        }
    }

    private suspend fun resolveDocument(
        input: String,
        did: AtprotoDid,
    ): DidDocument =
        identityResolver
            .resolveDocument(did)
            .getOrElse { throw OAuthIdentityResolutionException(input, "DID document resolution failed", it) }

    private fun pdsUrl(
        did: AtprotoDid,
        document: DidDocument,
    ): String {
        val service =
            document.service.firstOrNull { service ->
                (service.id == "#atproto_pds" || service.id == "$did#atproto_pds") &&
                    service.type == "AtprotoPersonalDataServer"
            } ?: throw OAuthPdsNotFoundException(did, "the DID document has no #atproto_pds service")
        val url = runCatching { Url(service.serviceEndpoint) }.getOrNull()
        val usable =
            url != null &&
                (url.protocol == URLProtocol.HTTPS || (allowHttp && url.protocol == URLProtocol.HTTP)) &&
                url.host.isNotEmpty()
        if (!usable) throw OAuthPdsNotFoundException(did, "the endpoint ${service.serviceEndpoint} is not an https:// URL")
        return service.serviceEndpoint
    }

    private suspend fun protectedResourceMetadata(origin: String): OAuthProtectedResourceMetadata? {
        val url = "$origin/.well-known/oauth-protected-resource"
        val metadata = fetchJson(url, OAuthProtectedResourceMetadata.serializer()) ?: return null
        if (metadata.resource != origin) {
            throw OAuthServerMetadataException(url, "resource is ${metadata.resource}, expected $origin")
        }
        return metadata
    }

    /**
     * GETs [url] and decodes it, or returns `null` for HTTP 404. The response must be exactly
     * HTTP 200 JSON from [url] itself; redirects are rejected.
     */
    private suspend fun <T> fetchJson(
        url: String,
        deserializer: DeserializationStrategy<T>,
    ): T? {
        val response: HttpResponse =
            try {
                httpClient.get(url) { header(HttpHeaders.Accept, ContentType.Application.Json.toString()) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                throw OAuthServerMetadataException(url, "request failed", exception)
            }
        if (response.status == HttpStatusCode.NotFound) return null
        if (response.status != HttpStatusCode.OK) throw OAuthServerMetadataException(url, "HTTP ${response.status.value}")
        if (response.request.url.toString() != Url(url).toString()) {
            throw OAuthServerMetadataException(url, "redirected to ${response.request.url}")
        }
        if (response.contentType()?.withoutParameters() != ContentType.Application.Json) {
            throw OAuthServerMetadataException(url, "content type is ${response.contentType()}, expected application/json")
        }
        return try {
            json.decodeFromString(deserializer, response.bodyAsText())
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw OAuthServerMetadataException(url, "invalid JSON document", exception)
        }
    }

    private fun webOrigin(
        value: String,
        label: String,
    ): String {
        val url =
            runCatching { Url(value) }.getOrNull()
                ?: throw OAuthServerMetadataException(value, "$label is not a valid URL")
        val secure = url.protocol == URLProtocol.HTTPS
        if (!secure && !(allowHttp && url.protocol == URLProtocol.HTTP)) {
            throw OAuthServerMetadataException(value, "$label must use https://")
        }
        return url.origin()
    }
}

/**
 * Returns `scheme://host[:port]`, leaving out a default port.
 */
internal fun Url.origin(): String {
    val port = specifiedPort.takeIf { it != DEFAULT_PORT && it != protocol.defaultPort }
    return "${protocol.name}://$host" + (port?.let { ":$it" } ?: "")
}
