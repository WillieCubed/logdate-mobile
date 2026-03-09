package app.logdate.server.oauth

import studio.hypertext.atproto.pds.AuthorizationServerMetadata
import studio.hypertext.atproto.pds.ProtectedResourceMetadata

/**
 * Server configuration and discovery metadata for AT Protocol OAuth surfaces.
 */
data class OAuthConfig(
    val issuer: String,
    val resource: String = issuer,
) {
    init {
        require(issuer.startsWith("https://")) { "issuer must use https" }
        require(resource.startsWith("https://")) { "resource must use https" }
    }

    val normalizedIssuer: String = issuer.trim().removeSuffix("/")
    val normalizedResource: String = resource.trim().removeSuffix("/")
    val authorizationEndpoint: String = "$normalizedIssuer/oauth/authorize"
    val tokenEndpoint: String = "$normalizedIssuer/oauth/token"
    val pushedAuthorizationRequestEndpoint: String = "$normalizedIssuer/oauth/par"
    val revocationEndpoint: String = "$normalizedIssuer/oauth/revoke"
    val jwksUri: String = "$normalizedIssuer/oauth/jwks"

    /**
     * Builds OAuth authorization server metadata for discovery.
     */
    fun authorizationServerMetadata(): AuthorizationServerMetadata =
        AuthorizationServerMetadata(
            issuer = normalizedIssuer,
            authorization_endpoint = authorizationEndpoint,
            token_endpoint = tokenEndpoint,
            pushed_authorization_request_endpoint = pushedAuthorizationRequestEndpoint,
            revocation_endpoint = revocationEndpoint,
            jwks_uri = jwksUri,
            response_types_supported = listOf("code"),
            grant_types_supported = listOf("authorization_code", "refresh_token"),
            code_challenge_methods_supported = listOf("S256"),
            token_endpoint_auth_methods_supported = listOf("none"),
            dpop_signing_alg_values_supported = listOf("ES256"),
            scopes_supported = listOf("atproto"),
            client_id_metadata_document_supported = true,
        )

    /**
     * Builds protected resource metadata for AT Protocol clients.
     */
    fun protectedResourceMetadata(): ProtectedResourceMetadata =
        ProtectedResourceMetadata(
            resource = normalizedResource,
            authorization_servers = listOf(normalizedIssuer),
        )

    companion object {
        /**
         * Reads OAuth discovery configuration from environment variables.
         */
        fun fromEnvironment(
            defaultIssuer: String = "https://logdate.app",
            issuer: String? = System.getenv("ATPROTO_OAUTH_ISSUER"),
            resource: String? = System.getenv("ATPROTO_OAUTH_RESOURCE"),
        ): OAuthConfig {
            val resolvedIssuer =
                issuer
                    ?.trim()
                    .orEmpty()
                    .ifBlank { defaultIssuer }
                    .removeSuffix("/")
            val resolvedResource =
                resource
                    ?.trim()
                    .orEmpty()
                    .ifBlank { resolvedIssuer }
                    .removeSuffix("/")
            return OAuthConfig(
                issuer = resolvedIssuer,
                resource = resolvedResource,
            )
        }
    }
}

/**
 * RFC 8414-style authorization server metadata for AT Protocol clients.
 */
