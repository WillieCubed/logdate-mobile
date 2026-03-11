package studio.hypertext.atproto.pds

import kotlinx.serialization.Serializable
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.identity.DidDocument
import studio.hypertext.atproto.syntax.Nsid

/**
 * RFC 8414-style authorization server metadata for AT Protocol clients.
 */
@Serializable
public data class AuthorizationServerMetadata(
    val issuer: String,
    val authorization_endpoint: String,
    val token_endpoint: String,
    val pushed_authorization_request_endpoint: String,
    val revocation_endpoint: String,
    val jwks_uri: String,
    val response_types_supported: List<String>,
    val grant_types_supported: List<String>,
    val code_challenge_methods_supported: List<String>,
    val token_endpoint_auth_methods_supported: List<String>,
    val token_endpoint_auth_signing_alg_values_supported: List<String>,
    val dpop_signing_alg_values_supported: List<String>,
    val scopes_supported: List<String>,
    val authorization_response_iss_parameter_supported: Boolean,
    val require_pushed_authorization_requests: Boolean,
    val require_request_uri_registration: Boolean = true,
    val client_id_metadata_document_supported: Boolean,
)

/**
 * OAuth protected resource metadata served by the PDS.
 */
@Serializable
public data class ProtectedResourceMetadata(
    val resource: String,
    val authorization_servers: List<String>,
)

/**
 * Response for `com.atproto.identity.resolveHandle`.
 */
@Serializable
public data class ResolveHandleResponse(
    val did: AtprotoDid,
)

/**
 * Response for `com.atproto.server.describeServer`.
 */
@Serializable
public data class DescribeServerResponse(
    val did: String,
    val availableUserDomains: List<String>,
    val inviteCodeRequired: Boolean,
    val phoneVerificationRequired: Boolean,
)

/**
 * Response for `com.atproto.repo.describeRepo`.
 */
@Serializable
public data class DescribeRepoResponse(
    val handle: String,
    val did: AtprotoDid,
    val didDoc: DidDocument,
    val collections: List<Nsid>,
    val handleIsCorrect: Boolean,
)
