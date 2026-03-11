package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.TokenService
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.oauth.OAuthAuthorizationService
import app.logdate.server.oauth.OAuthConfig
import app.logdate.server.oauth.OAuthException
import app.logdate.server.oauth.OAuthInvalidRequestException
import app.logdate.server.oauth.OAuthKeyService
import app.logdate.server.oauth.OAuthUseDpopNonceException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import studio.hypertext.atproto.pds.AuthorizationCodeTokenRequest
import studio.hypertext.atproto.pds.AuthorizationDecisionRequest
import studio.hypertext.atproto.pds.AuthorizationPrompt
import studio.hypertext.atproto.pds.AuthorizationPromptResponse
import studio.hypertext.atproto.pds.OAuthErrorResponse
import studio.hypertext.atproto.pds.OAuthRevokeRequest
import studio.hypertext.atproto.pds.OAuthTokenResponse
import studio.hypertext.atproto.pds.PdsDiscoveryService
import studio.hypertext.atproto.pds.PushedAuthorizationBody
import studio.hypertext.atproto.pds.PushedAuthorizationRequest
import studio.hypertext.atproto.pds.RefreshTokenGrantRequest
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Serves OAuth discovery metadata and AT Protocol-compatible OAuth authorization endpoints.
 */
@OptIn(ExperimentalUuidApi::class)
fun Route.oauthRoutes(
    config: OAuthConfig,
    keyService: OAuthKeyService,
    discoveryService: PdsDiscoveryService? = null,
    authorizationService: OAuthAuthorizationService? = null,
    accountRepository: AccountRepository? = null,
    tokenService: TokenService? = null,
    identityService: AtprotoIdentityService? = null,
) {
    get("/.well-known/oauth-authorization-server") {
        call.respond(HttpStatusCode.OK, discoveryService?.authorizationServerMetadata() ?: config.authorizationServerMetadata())
    }

    get("/.well-known/oauth-protected-resource") {
        call.respond(HttpStatusCode.OK, discoveryService?.protectedResourceMetadata() ?: config.protectedResourceMetadata())
    }

    get("/oauth/jwks") {
        call.respond(HttpStatusCode.OK, keyService.jwks())
    }

    post("/oauth/par") {
        val service =
            authorizationService ?: return@post call.respond(
                HttpStatusCode.NotImplemented,
                OAuthErrorResponse("server_error", "OAuth PAR is not configured"),
            )
        val parameters = call.receiveParameters()
        val dpopProof =
            call.request
                .header(DPOP_HEADER)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return@post call.respondOAuthError(OAuthInvalidRequestException("DPoP proof is required"))

        runCatching {
            service
                .createPushedAuthorizationRequest(
                    PushedAuthorizationRequest(
                        clientId = parameters.requireValue("client_id"),
                        redirectUri = parameters.requireValue("redirect_uri"),
                        scope = parameters.requireValue("scope"),
                        responseType = parameters.requireValue("response_type"),
                        codeChallenge = parameters.requireValue("code_challenge"),
                        codeChallengeMethod = parameters.requireValue("code_challenge_method"),
                        state = parameters["state"],
                        loginHint = parameters["login_hint"],
                        clientAssertionType = parameters["client_assertion_type"],
                        clientAssertion = parameters["client_assertion"],
                        dpopProof = dpopProof,
                        htu = call.absoluteRequestUrl(),
                    ),
                ).getOrThrow()
        }.onSuccess { response ->
            call.response.header(DPOP_NONCE_HEADER, response.dpopNonce)
            call.respond(
                HttpStatusCode.Created,
                PushedAuthorizationBody(requestUri = response.requestUri, expiresInSeconds = response.expiresInSeconds),
            )
        }.onFailure { error ->
            call.respondOAuthError(error)
        }
    }

    get("/oauth/authorize") {
        val service =
            authorizationService ?: return@get call.respond(
                HttpStatusCode.NotImplemented,
                OAuthErrorResponse("server_error", "OAuth authorization is not configured"),
            )
        val requestUri =
            call.request.queryParameters["request_uri"]
                ?.trim()
                .orEmpty()
        if (requestUri.isBlank()) {
            call.respondOAuthError(OAuthInvalidRequestException("request_uri is required"))
            return@get
        }

        val account =
            resolveOAuthAccount(
                call = call,
                accountRepository = accountRepository,
                tokenService = tokenService,
                identityService = identityService,
            ) ?: return@get

        runCatching {
            service.loadAuthorizationPrompt(requestUri).getOrThrow().toResponse(account)
        }.onSuccess { prompt ->
            call.respond(HttpStatusCode.OK, prompt)
        }.onFailure { error ->
            call.respondOAuthError(error)
        }
    }

    post("/oauth/authorize") {
        val service =
            authorizationService ?: return@post call.respond(
                HttpStatusCode.NotImplemented,
                OAuthErrorResponse("server_error", "OAuth authorization is not configured"),
            )
        val account =
            resolveOAuthAccount(
                call = call,
                accountRepository = accountRepository,
                tokenService = tokenService,
                identityService = identityService,
            ) ?: return@post
        val parameters = call.receiveParameters()
        val requestUri = parameters["request_uri"]?.trim().orEmpty()
        val decision = parameters["decision"]?.trim().orEmpty()
        if (requestUri.isBlank()) {
            call.respondOAuthError(OAuthInvalidRequestException("request_uri is required"))
            return@post
        }
        if (decision != "approve" && decision != "deny") {
            call.respondOAuthError(OAuthInvalidRequestException("decision must be approve or deny"))
            return@post
        }

        runCatching {
            service
                .completeAuthorization(
                    AuthorizationDecisionRequest(
                        requestUri = requestUri,
                        subjectDid = requireNotNull(account.did),
                        subjectHandle = requireNotNull(account.handle),
                        approved = decision == "approve",
                    ),
                ).getOrThrow()
        }.onSuccess { redirectUri ->
            call.respondRedirect(redirectUri, permanent = false)
        }.onFailure { error ->
            call.respondOAuthError(error)
        }
    }

    post("/oauth/token") {
        val service =
            authorizationService ?: return@post call.respond(
                HttpStatusCode.NotImplemented,
                OAuthErrorResponse("server_error", "OAuth token exchange is not configured"),
            )
        val parameters = call.receiveParameters()
        val dpopProof =
            call.request
                .header(DPOP_HEADER)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return@post call.respondOAuthError(OAuthInvalidRequestException("DPoP proof is required"))

        runCatching {
            when (val grantType = parameters.requireValue("grant_type")) {
                "authorization_code" ->
                    service
                        .exchangeAuthorizationCode(
                            AuthorizationCodeTokenRequest(
                                code = parameters.requireValue("code"),
                                redirectUri = parameters.requireValue("redirect_uri"),
                                clientId = parameters.requireValue("client_id"),
                                codeVerifier = parameters.requireValue("code_verifier"),
                                clientAssertionType = parameters["client_assertion_type"],
                                clientAssertion = parameters["client_assertion"],
                                dpopProof = dpopProof,
                                htu = call.absoluteRequestUrl(),
                            ),
                        ).getOrThrow()

                "refresh_token" ->
                    service
                        .exchangeRefreshToken(
                            RefreshTokenGrantRequest(
                                refreshToken = parameters.requireValue("refresh_token"),
                                clientId = parameters.requireValue("client_id"),
                                clientAssertionType = parameters["client_assertion_type"],
                                clientAssertion = parameters["client_assertion"],
                                dpopProof = dpopProof,
                                htu = call.absoluteRequestUrl(),
                            ),
                        ).getOrThrow()

                else ->
                    throw app.logdate.server.oauth
                        .OAuthUnsupportedGrantTypeException("Unsupported grant_type: $grantType")
            }
        }.onSuccess { response ->
            call.respondOAuthToken(response, service.nonce())
        }.onFailure { error ->
            call.respondOAuthError(error)
        }
    }

    post("/oauth/revoke") {
        val service =
            authorizationService ?: return@post call.respond(
                HttpStatusCode.NotImplemented,
                OAuthErrorResponse("server_error", "OAuth revocation is not configured"),
            )
        val parameters = call.receiveParameters()
        val dpopProof =
            call.request
                .header(DPOP_HEADER)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return@post call.respondOAuthError(OAuthInvalidRequestException("DPoP proof is required"))

        runCatching {
            service
                .revokeRefreshToken(
                    OAuthRevokeRequest(
                        refreshToken = parameters.requireValue("token"),
                        clientId = parameters.requireValue("client_id"),
                        clientAssertionType = parameters["client_assertion_type"],
                        clientAssertion = parameters["client_assertion"],
                        dpopProof = dpopProof,
                        htu = call.absoluteRequestUrl(),
                    ),
                ).getOrThrow()
        }.onSuccess {
            call.response.header(DPOP_NONCE_HEADER, authorizationService.nonce())
            call.respond(HttpStatusCode.OK)
        }.onFailure { error ->
            call.respondOAuthError(error)
        }
    }
}

private suspend fun ApplicationCall.respondOAuthToken(
    tokenResponse: OAuthTokenResponse,
    nonce: String,
) {
    response.header(HttpHeaders.CacheControl, "no-store")
    response.header(HttpHeaders.Pragma, "no-cache")
    response.header(DPOP_NONCE_HEADER, nonce)
    respond(HttpStatusCode.OK, tokenResponse)
}

private suspend fun ApplicationCall.respondOAuthError(error: Throwable) {
    val oauthError = error as? OAuthException
    if (oauthError is OAuthUseDpopNonceException) {
        response.header(DPOP_NONCE_HEADER, oauthError.nonce)
    }

    if (oauthError != null) {
        respond(oauthError.status, OAuthErrorResponse(oauthError.error, oauthError.message))
        return
    }

    respond(HttpStatusCode.InternalServerError, OAuthErrorResponse("server_error", error.message ?: "OAuth request failed"))
}

private fun AuthorizationPrompt.toResponse(account: Account): AuthorizationPromptResponse =
    AuthorizationPromptResponse(
        clientId = clientId,
        clientName = clientName,
        redirectUri = redirectUri,
        scope = scope,
        state = state,
        loginHint = loginHint,
        did = requireNotNull(account.did),
        handle = requireNotNull(account.handle),
    )

private fun io.ktor.http.Parameters.requireValue(name: String): String {
    val value = this[name]?.trim().orEmpty()
    if (value.isBlank()) {
        throw OAuthInvalidRequestException("$name is required")
    }
    return value
}

private fun ApplicationCall.absoluteRequestUrl(): String {
    val origin = request.origin
    val host =
        if (origin.serverPort == DEFAULT_HTTPS_PORT || origin.serverPort == DEFAULT_HTTP_PORT) {
            origin.serverHost
        } else {
            "${origin.serverHost}:${origin.serverPort}"
        }
    return "${origin.scheme}://$host${request.path()}"
}

@OptIn(ExperimentalUuidApi::class)
private suspend fun resolveOAuthAccount(
    call: ApplicationCall,
    accountRepository: AccountRepository?,
    tokenService: TokenService?,
    identityService: AtprotoIdentityService?,
): Account? {
    if (accountRepository == null || tokenService == null || identityService == null) {
        call.respond(HttpStatusCode.NotImplemented, OAuthErrorResponse("server_error", "OAuth account resolution is not configured"))
        return null
    }

    val authHeader = call.request.header(HttpHeaders.Authorization)
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        call.respond(HttpStatusCode.Unauthorized, OAuthErrorResponse("login_required", "LogDate bearer authentication is required"))
        return null
    }

    val accountId =
        tokenService
            .validateAccessToken(authHeader.removePrefix("Bearer ").trim())
            ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
    if (accountId == null) {
        call.respond(HttpStatusCode.Unauthorized, OAuthErrorResponse("login_required", "Invalid LogDate bearer token"))
        return null
    }

    val account = accountRepository.findById(accountId)?.let { identityService.ensureIdentity(it) }
    if (account == null) {
        call.respond(HttpStatusCode.Unauthorized, OAuthErrorResponse("login_required", "Authenticated LogDate account was not found"))
        return null
    }
    return account
}

private const val DPOP_HEADER = "DPoP"
private const val DPOP_NONCE_HEADER = "DPoP-Nonce"
private const val DEFAULT_HTTPS_PORT = 443
private const val DEFAULT_HTTP_PORT = 80
