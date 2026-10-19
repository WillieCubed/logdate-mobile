package studio.hypertext.atproto.oauth.client

import io.ktor.http.DEFAULT_PORT
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A client authentication key: an ES256 private key the client signs `private_key_jwt`
 * assertions with.
 *
 * The public half of every client key is published in the client metadata document, either
 * inline (`jwks`) or at [OAuthClientConfig.jwksUri]. Authorization servers bind each session to the
 * key that started it, so keep a retired key in the configuration until no stored session uses it.
 *
 * @property privateJwk The private P-256 key. Its `kid` must be set.
 */
public class OAuthClientKey(
    public val privateJwk: JsonWebKey,
) {
    init {
        require(privateJwk.isPrivate) { "A client key must include its private part (the JWK \"d\" member)" }
        require(privateJwk.kty == "EC" && privateJwk.crv == "P-256") {
            "Client keys must be P-256 EC keys for ES256, got kty=${privateJwk.kty} crv=${privateJwk.crv}"
        }
        require(!privateJwk.kid.isNullOrBlank()) { "A client key needs a \"kid\" so servers can tell keys apart" }
    }

    /**
     * The key identifier.
     */
    public val kid: String
        get() = requireNotNull(privateJwk.kid)

    /**
     * The public key as it appears in the client's JWKS.
     */
    public val publicJwk: JsonWebKey
        get() = privateJwk.toPublicJwk().copy(alg = ES256, use = "sig")

    override fun toString(): String = "OAuthClientKey(kid=$kid)"
}

/**
 * Configuration for a confidential AT Protocol OAuth client.
 *
 * The client publishes a metadata document at [clientId]; serve [AtprotoOAuthClient.clientMetadata]
 * at that exact URL with HTTP 200 and `Content-Type: application/json`.
 *
 * @property clientId The `https://` URL of the client metadata document, without a port.
 * @property redirectUris Callback URLs the authorization server may return the user to. The first
 *   one is the default.
 * @property keys Client authentication keys. The first key signs new sessions.
 * @property scope Every scope this client may request, space-separated. Must include `atproto`.
 * @property clientName Human-readable client name.
 * @property clientUri Client home page. Must share its hostname with [clientId].
 * @property logoUri `https://` URL of the client logo.
 * @property tosUri `https://` URL of the terms of service.
 * @property policyUri `https://` URL of the privacy policy.
 * @property jwksUri When set, the metadata points here instead of listing the keys inline, and the
 *   caller serves [AtprotoOAuthClient.jwks] at this URL.
 * @property applicationType `web` (the default) or `native`.
 */
public data class OAuthClientConfig(
    val clientId: String,
    val redirectUris: List<String>,
    val keys: List<OAuthClientKey>,
    val scope: String = ATPROTO_SCOPE,
    val clientName: String? = null,
    val clientUri: String? = null,
    val logoUri: String? = null,
    val tosUri: String? = null,
    val policyUri: String? = null,
    val jwksUri: String? = null,
    val applicationType: String = "web",
) {
    init {
        val clientUrl = parseUrl(clientId, "clientId")
        require(clientUrl.protocol == URLProtocol.HTTPS) { "clientId must be an https:// URL, got $clientId" }
        require(clientUrl.specifiedPort == DEFAULT_PORT) { "clientId must not include a port, got $clientId" }
        require(redirectUris.isNotEmpty()) { "At least one redirect URI is required" }
        require(keys.isNotEmpty()) { "A confidential client needs at least one client key" }
        require(keys.map { it.kid }.toSet().size == keys.size) { "Client key ids must be unique" }
        require(scope.split(' ').contains(ATPROTO_SCOPE)) { "The client scope must include \"$ATPROTO_SCOPE\"" }
        require(applicationType == "web" || applicationType == "native") {
            "applicationType must be \"web\" or \"native\", got $applicationType"
        }
        if (applicationType == "web") {
            redirectUris.forEach { uri ->
                require(parseUrl(uri, "redirect URI").protocol == URLProtocol.HTTPS) {
                    "Web clients must use https:// redirect URIs, got $uri"
                }
            }
        }
        clientUri?.let { uri ->
            require(parseUrl(uri, "clientUri").host == clientUrl.host) {
                "clientUri must have the same hostname as clientId"
            }
        }
        listOfNotNull(logoUri, tosUri, policyUri, jwksUri).forEach { uri ->
            require(parseUrl(uri, "metadata URL").protocol == URLProtocol.HTTPS) { "$uri must be an https:// URL" }
        }
    }

    /**
     * The key that signs client assertions for new sessions.
     */
    internal val signingKey: OAuthClientKey
        get() = keys.first()

    internal fun key(kid: String): OAuthClientKey? = keys.firstOrNull { it.kid == kid }

    /**
     * Builds the client metadata document for this configuration.
     */
    public fun toClientMetadata(): OAuthClientMetadata =
        OAuthClientMetadata(
            clientId = clientId,
            applicationType = applicationType,
            grantTypes = listOf("authorization_code", "refresh_token"),
            scope = scope,
            responseTypes = listOf("code"),
            redirectUris = redirectUris,
            tokenEndpointAuthMethod = "private_key_jwt",
            tokenEndpointAuthSigningAlg = ES256,
            dpopBoundAccessTokens = true,
            jwks = if (jwksUri == null) JsonWebKeySet(keys.map { it.publicJwk }) else null,
            jwksUri = jwksUri,
            clientName = clientName,
            clientUri = clientUri,
            logoUri = logoUri,
            tosUri = tosUri,
            policyUri = policyUri,
        )

    override fun toString(): String = "OAuthClientConfig(clientId=$clientId, redirectUris=$redirectUris, keys=$keys, scope=$scope)"
}

/**
 * The client metadata document an authorization server fetches from the `client_id` URL.
 *
 * See the "Client ID Metadata Document" section of the AT Protocol OAuth specification.
 */
@Serializable
public data class OAuthClientMetadata(
    @SerialName("client_id") val clientId: String,
    @SerialName("application_type") val applicationType: String,
    @SerialName("grant_types") val grantTypes: List<String>,
    val scope: String,
    @SerialName("response_types") val responseTypes: List<String>,
    @SerialName("redirect_uris") val redirectUris: List<String>,
    @SerialName("token_endpoint_auth_method") val tokenEndpointAuthMethod: String,
    @SerialName("token_endpoint_auth_signing_alg") val tokenEndpointAuthSigningAlg: String,
    @SerialName("dpop_bound_access_tokens") val dpopBoundAccessTokens: Boolean,
    val jwks: JsonWebKeySet? = null,
    @SerialName("jwks_uri") val jwksUri: String? = null,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("client_uri") val clientUri: String? = null,
    @SerialName("logo_uri") val logoUri: String? = null,
    @SerialName("tos_uri") val tosUri: String? = null,
    @SerialName("policy_uri") val policyUri: String? = null,
)

/**
 * The scope every AT Protocol OAuth session must include.
 */
public const val ATPROTO_SCOPE: String = "atproto"

private fun parseUrl(
    value: String,
    label: String,
): Url =
    runCatching { Url(value) }
        .getOrElse { throw IllegalArgumentException("$label is not a valid URL: $value", it) }
        .also { require(value.startsWith("https://") || value.startsWith("http://")) { "$label must be a web URL: $value" } }
