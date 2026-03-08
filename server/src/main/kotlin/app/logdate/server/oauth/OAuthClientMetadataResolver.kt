package app.logdate.server.oauth

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Resolves and validates OAuth client metadata documents from `client_id` URLs.
 */
class OAuthClientMetadataResolver(
    private val httpClient: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val clock: Clock = Clock.System,
    private val cacheTtl: Duration = 10.minutes,
) {
    private val cache = mutableMapOf<String, CachedClientMetadata>()

    /**
     * Resolves and validates the metadata document at [clientId].
     */
    suspend fun resolve(clientId: String): Result<OAuthClientMetadata> =
        runCatching {
            val normalizedClientId = normalizeClientId(clientId)
            val cached = cache[normalizedClientId]
            if (cached != null && clock.now() < cached.expiresAt) {
                return@runCatching cached.metadata
            }

            val response = httpClient.get(normalizedClientId)
            if (response.status != HttpStatusCode.OK) {
                throw OAuthInvalidClientException("Client metadata document returned HTTP ${response.status.value}")
            }

            val metadata =
                json.decodeFromString<OAuthClientMetadata>(response.bodyAsText()).validated(expectedClientId = normalizedClientId)
            cache[normalizedClientId] = CachedClientMetadata(metadata = metadata, expiresAt = clock.now() + cacheTtl)
            metadata
        }

    private fun normalizeClientId(clientId: String): String {
        val trimmed = clientId.trim()
        require(trimmed.isNotBlank()) { "client_id is required" }
        val uri =
            runCatching { URI(trimmed) }.getOrNull()
                ?: throw OAuthInvalidClientException("client_id must be a valid URL")
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        val isLoopbackHttp = scheme == "http" && (host == "localhost" || host == "127.0.0.1")
        if (scheme != "https" && !isLoopbackHttp) {
            throw OAuthInvalidClientException("client_id must use https or a loopback http URL")
        }
        return trimmed
    }

    private data class CachedClientMetadata(
        val metadata: OAuthClientMetadata,
        val expiresAt: kotlin.time.Instant,
    )
}

/**
 * Minimal AT Protocol OAuth client metadata document.
 */
@Serializable
data class OAuthClientMetadata(
    val client_id: String,
    val redirect_uris: List<String>,
    val grant_types: List<String> = listOf("authorization_code", "refresh_token"),
    val response_types: List<String> = listOf("code"),
    val scope: String? = null,
    val token_endpoint_auth_method: String = "none",
    val dpop_bound_access_tokens: Boolean = true,
    val client_name: String? = null,
) {
    /**
     * Returns `true` when [redirectUri] is listed in this metadata document.
     */
    fun supportsRedirect(redirectUri: String): Boolean = redirect_uris.any { it == redirectUri }

    /**
     * Returns the declared scope tokens in canonical order.
     */
    fun scopeSet(): Set<String> =
        scope
            .orEmpty()
            .split(' ')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
}

private fun OAuthClientMetadata.validated(expectedClientId: String): OAuthClientMetadata {
    if (client_id != expectedClientId) {
        throw OAuthInvalidClientException("Client metadata client_id must match the resolved client_id URL")
    }
    if (redirect_uris.isEmpty()) {
        throw OAuthInvalidClientException("Client metadata must declare at least one redirect_uri")
    }
    if ("code" !in response_types) {
        throw OAuthInvalidClientException("Client metadata must support response_type=code")
    }
    if ("authorization_code" !in grant_types) {
        throw OAuthInvalidClientException("Client metadata must support the authorization_code grant")
    }
    if ("refresh_token" !in grant_types) {
        throw OAuthInvalidClientException("Client metadata must support the refresh_token grant")
    }
    if (token_endpoint_auth_method != "none") {
        throw OAuthInvalidClientException("Only token_endpoint_auth_method=none is currently supported")
    }
    if (!dpop_bound_access_tokens) {
        throw OAuthInvalidClientException("Client metadata must require DPoP-bound access tokens")
    }
    return copy(
        redirect_uris = redirect_uris.map(String::trim),
        grant_types = grant_types.map(String::trim),
        response_types = response_types.map(String::trim),
        scope = scope?.trim()?.takeIf(String::isNotEmpty),
        client_name = client_name?.trim()?.takeIf(String::isNotEmpty),
    )
}
