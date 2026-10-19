package studio.hypertext.atproto.oauth.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OAuth 2.0 Protected Resource Metadata (RFC 9728) published by a data server at
 * `/.well-known/oauth-protected-resource`.
 *
 * @property resource The origin of the resource server.
 * @property authorizationServers The authorization server issuers. The AT Protocol requires exactly
 *   one.
 */
@Serializable
public data class OAuthProtectedResourceMetadata(
    val resource: String,
    @SerialName("authorization_servers") val authorizationServers: List<String> = emptyList(),
)

/**
 * OAuth 2.0 Authorization Server Metadata (RFC 8414) published at
 * `/.well-known/oauth-authorization-server`.
 *
 * Only the members this client uses are modeled. The client checks them against the AT Protocol
 * OAuth profile before using a server.
 */
@Serializable
public data class OAuthAuthorizationServerMetadata(
    val issuer: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("pushed_authorization_request_endpoint") val pushedAuthorizationRequestEndpoint: String? = null,
    @SerialName("revocation_endpoint") val revocationEndpoint: String? = null,
    @SerialName("response_types_supported") val responseTypesSupported: List<String> = emptyList(),
    @SerialName("grant_types_supported") val grantTypesSupported: List<String> = emptyList(),
    @SerialName("code_challenge_methods_supported") val codeChallengeMethodsSupported: List<String> = emptyList(),
    @SerialName("token_endpoint_auth_methods_supported") val tokenEndpointAuthMethodsSupported: List<String> = emptyList(),
    @SerialName("token_endpoint_auth_signing_alg_values_supported")
    val tokenEndpointAuthSigningAlgValuesSupported: List<String>? = null,
    @SerialName("dpop_signing_alg_values_supported") val dpopSigningAlgValuesSupported: List<String> = emptyList(),
    @SerialName("scopes_supported") val scopesSupported: List<String> = emptyList(),
    @SerialName("authorization_response_iss_parameter_supported") val authorizationResponseIssParameterSupported: Boolean = false,
    @SerialName("require_pushed_authorization_requests") val requirePushedAuthorizationRequests: Boolean = false,
    @SerialName("client_id_metadata_document_supported") val clientIdMetadataDocumentSupported: Boolean = false,
    @SerialName("protected_resources") val protectedResources: List<String>? = null,
)

/**
 * Lists every way [metadata] falls short of the AT Protocol OAuth profile for a confidential
 * client. An empty list means the server is usable.
 */
internal fun atprotoProfileViolations(metadata: OAuthAuthorizationServerMetadata): List<String> =
    buildList {
        if (!metadata.clientIdMetadataDocumentSupported) add("client_id_metadata_document_supported is not true")
        if (metadata.pushedAuthorizationRequestEndpoint == null) add("no pushed_authorization_request_endpoint")
        listOf("authorization_code", "refresh_token").forEach { grant ->
            if (grant !in metadata.grantTypesSupported) add("grant type \"$grant\" is not supported")
        }
        if ("code" !in metadata.responseTypesSupported) add("response type \"code\" is not supported")
        if ("S256" !in metadata.codeChallengeMethodsSupported) add("PKCE method S256 is not supported")
        if (ES256 !in metadata.dpopSigningAlgValuesSupported) add("DPoP algorithm ES256 is not supported")
        if ("private_key_jwt" !in metadata.tokenEndpointAuthMethodsSupported) add("private_key_jwt client authentication is not supported")
        metadata.tokenEndpointAuthSigningAlgValuesSupported?.let { algorithms ->
            if (ES256 !in algorithms) add("client assertion algorithm ES256 is not supported")
        }
        if (ATPROTO_SCOPE !in metadata.scopesSupported) add("scope \"$ATPROTO_SCOPE\" is not supported")
    }
