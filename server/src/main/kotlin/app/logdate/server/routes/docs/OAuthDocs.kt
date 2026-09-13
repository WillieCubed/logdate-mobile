package app.logdate.server.routes.docs

import app.logdate.server.oauth.JsonWebKey
import app.logdate.server.oauth.JsonWebKeySet
import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.ErrorEnvelope
import app.logdate.server.routes.OAuthAuthorizationDecisionForm
import app.logdate.server.routes.OAuthParForm
import app.logdate.server.routes.OAuthRevokeForm
import app.logdate.server.routes.OAuthTokenForm
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.bearerUnauthorized
import app.logdate.server.routes.dpopNonceHeader
import app.logdate.server.routes.oauthError
import app.logdate.server.routes.ok
import app.logdate.server.routes.publicOperation
import io.github.smiley4.ktoropenapi.config.RequestConfig
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import studio.hypertext.atproto.pds.AuthorizationPromptResponse
import studio.hypertext.atproto.pds.AuthorizationServerMetadata
import studio.hypertext.atproto.pds.OAuthTokenResponse
import studio.hypertext.atproto.pds.ProtectedResourceMetadata
import studio.hypertext.atproto.pds.PushedAuthorizationBody

/** Documentation for the OAuth endpoints in `OAuthRoutes.kt`. */
internal object OAuthDocs {
    private const val ISSUER = "https://cloud.logdate.app"
    private const val CLIENT_ID = "https://journalviewer.example/oauth-client-metadata.json"
    private const val REQUEST_URI = "urn:ietf:params:oauth:request_uri:req_7f3c9a1b2d4e"
    private const val DPOP_PROOF = "eyJ0eXAiOiJkcG9wK2p3dCIsImFsZyI6IkVTMjU2IiwiandrIjp7Li4ufX0.dpop-proof-example"

    private val notConfigured =
        ErrorCase(
            "server_error",
            "This deployment has AT Protocol OAuth switched off (`ATPROTO_OAUTH` is missing from **Describe this server**). " +
                "There is nothing a client can do; use the LogDate sign-in instead.",
            "OAuth is not configured",
        )
    private val serverError =
        ErrorCase("server_error", "Something failed on the server. Retry with backoff.", "OAuth request failed")

    private val invalidRequest =
        ErrorCase(
            "invalid_request",
            "A required form field or the `DPoP` header is missing or malformed. The `error_description` names it.",
            "client_id is required",
        )
    private val invalidClient =
        ErrorCase(
            "invalid_client",
            "The `client_id` is not a valid client metadata URL, or the document it points at is invalid.",
            "Client metadata could not be loaded",
        )
    private val invalidDpop =
        ErrorCase(
            "invalid_dpop_proof",
            "The DPoP proof does not verify, or its `htm`/`htu` do not match this request. Sign a fresh proof.",
            "DPoP proof signature is invalid",
        )
    private val useDpopNonce =
        ErrorCase(
            "use_dpop_nonce",
            "The proof must carry the server's current nonce. Read it from the `DPoP-Nonce` response header, put it in the proof's `nonce` claim, and retry once.",
            "The DPoP proof must include the current server nonce",
        )

    private fun RequestConfig.dpopHeader() {
        headerParameter<String>("DPoP") {
            description =
                "A DPoP proof JWT for this exact request: `htm` = the method, `htu` = this URL, `nonce` = the latest `DPoP-Nonce` " +
                "the server sent you, signed with your client's key pair."
            required = true
            example("Example") { value = DPOP_PROOF }
        }
    }

    val getAuthorizationServerMetadata: RouteConfig.() -> Unit = {
        publicOperation(
            "getOAuthAuthorizationServerMetadata",
            ApiTags.OAUTH,
            "Get authorization server metadata",
            """
            The RFC 8414 discovery document for this server's OAuth authorization server. An AT Protocol client
            reads it first to learn the endpoint URLs and what the server supports: only the `code` response
            type with PKCE (`S256`), pushed authorization requests are required, DPoP proofs must be signed with
            `ES256` or `ES256K`, and the only scope is `atproto`.
            """,
        )
        response {
            ok(
                "The discovery document.",
                AuthorizationServerMetadata(
                    issuer = ISSUER,
                    authorization_endpoint = "$ISSUER/oauth/authorize",
                    token_endpoint = "$ISSUER/oauth/token",
                    pushed_authorization_request_endpoint = "$ISSUER/oauth/par",
                    revocation_endpoint = "$ISSUER/oauth/revoke",
                    jwks_uri = "$ISSUER/oauth/jwks",
                    response_types_supported = listOf("code"),
                    grant_types_supported = listOf("authorization_code", "refresh_token"),
                    code_challenge_methods_supported = listOf("S256"),
                    token_endpoint_auth_methods_supported = listOf("none", "private_key_jwt"),
                    token_endpoint_auth_signing_alg_values_supported = listOf("ES256", "ES256K"),
                    dpop_signing_alg_values_supported = listOf("ES256", "ES256K"),
                    scopes_supported = listOf("atproto"),
                    authorization_response_iss_parameter_supported = true,
                    require_pushed_authorization_requests = true,
                    client_id_metadata_document_supported = true,
                ),
            )
        }
    }

    val getProtectedResourceMetadata: RouteConfig.() -> Unit = {
        publicOperation(
            "getOAuthProtectedResourceMetadata",
            ApiTags.OAUTH,
            "Get protected resource metadata",
            """
            The RFC 9728 document that says which authorization server issues tokens for this personal data
            server. Clients that only know the PDS URL read this to find the authorization server; here it is
            the same host.
            """,
        )
        response {
            ok("The protected-resource document.", ProtectedResourceMetadata(resource = ISSUER, authorization_servers = listOf(ISSUER)))
        }
    }

    val getJwks: RouteConfig.() -> Unit = {
        publicOperation(
            "getOAuthJwks",
            ApiTags.OAUTH,
            "Get the signing keys",
            """
            The JSON Web Key Set the server signs OAuth tokens with. Clients and other servers fetch it to
            verify token signatures. Keys may rotate; cache by `kid` and refetch on an unknown key.
            """,
        )
        response {
            ok(
                "The public keys.",
                JsonWebKeySet(
                    keys =
                        listOf(
                            JsonWebKey(
                                kty = "EC",
                                use = "sig",
                                key_ops = listOf("verify"),
                                alg = "ES256",
                                kid = "2026-09-01",
                                crv = "P-256",
                                x = "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU",
                                y = "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0",
                            ),
                        ),
                ),
            )
        }
    }

    val pushAuthorizationRequest: RouteConfig.() -> Unit = {
        publicOperation(
            "pushAuthorizationRequest",
            ApiTags.OAUTH,
            "Push an authorization request",
            """
            Step one of the OAuth flow (RFC 9126, required here). Instead of putting your authorization
            parameters in a browser URL, you POST them as a form and receive a short-lived `request_uri`.
            Then send the person to `/oauth/authorize?request_uri=<it>`.

            The form fields are `client_id` (the URL of your client metadata document), `redirect_uri`,
            `scope` (`atproto`), `response_type` (`code`), `code_challenge` and `code_challenge_method`
            (`S256`), plus optional `state`, `login_hint`, and `client_assertion_type` /
            `client_assertion` for confidential clients. The request must carry a `DPoP` proof; the response's
            `DPoP-Nonce` header is the nonce to use on your next proof.
            """,
        )
        request {
            dpopHeader()
            body<OAuthParForm> {
                description =
                    "`application/x-www-form-urlencoded`. Confidential clients add `client_assertion_type` and `client_assertion`."
                required = true
                mediaTypes(ContentType.Application.FormUrlEncoded)
                example("Example") {
                    value =
                        OAuthParForm(
                            clientId = CLIENT_ID,
                            redirectUri = "https://journalviewer.example/callback",
                            scope = "atproto",
                            responseType = "code",
                            codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                            codeChallengeMethod = "S256",
                            state = "af0ifjsldkj",
                        )
                }
            }
        }
        response {
            code(HttpStatusCode.Created) {
                description =
                    "The request is stored. Send the person to `/oauth/authorize?request_uri=<request_uri>` within `expires_in` seconds."
                header<String>("DPoP-Nonce") { this.description = "The nonce to put in your next DPoP proof." }
                body<PushedAuthorizationBody> {
                    example("Example") { value = PushedAuthorizationBody(requestUri = REQUEST_URI, expiresInSeconds = 300) }
                }
            }
            oauthError(HttpStatusCode.BadRequest, invalidRequest, invalidClient, invalidDpop, useDpopNonce, headers = dpopNonceHeader)
            oauthError(HttpStatusCode.InternalServerError, serverError)
            oauthError(HttpStatusCode.NotImplemented, notConfigured)
        }
    }

    val getAuthorizationPrompt: RouteConfig.() -> Unit = {
        bearerOperation(
            "getAuthorizationPrompt",
            ApiTags.OAUTH,
            "Load the consent prompt",
            """
            Step two, called by the LogDate app or web front end, not by the third-party client. It loads the
            pushed request named by `request_uri` and returns what to show the signed-in person: which client
            is asking, what it wants, and where it will be sent back. The person must be signed in to LogDate
            (a LogDate bearer token, not an OAuth token).

            The third-party client never sees this; it only sends the person to this URL and waits for the
            redirect.
            """,
        )
        request {
            queryParameter<String>("request_uri") {
                description = "The `request_uri` returned by **Push an authorization request**."
                required = true
                example("Example") { value = REQUEST_URI }
            }
        }
        response {
            ok(
                "What to show on the consent screen.",
                AuthorizationPromptResponse(
                    clientId = CLIENT_ID,
                    clientName = "Journal Viewer",
                    redirectUri = "https://journalviewer.example/callback",
                    scope = "atproto",
                    state = "af0ifjsldkj",
                    loginHint = null,
                    did = DocExamples.DID,
                    handle = DocExamples.HANDLE,
                ),
            )
            oauthError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "invalid_request",
                    "`request_uri` is missing, unknown, already used, or expired. Start again from **Push an authorization request**.",
                    "request_uri is required",
                ),
            )
            bearerUnauthorized(ErrorEnvelope.OAUTH)
            oauthError(HttpStatusCode.InternalServerError, serverError)
            oauthError(HttpStatusCode.NotImplemented, notConfigured)
        }
    }

    val submitAuthorizationDecision: RouteConfig.() -> Unit = {
        bearerOperation(
            "submitAuthorizationDecision",
            ApiTags.OAUTH,
            "Approve or deny the request",
            """
            Step three, also called by the LogDate front end. Records the signed-in person's decision for the
            pushed request and answers a `302` redirect to the client's `redirect_uri` carrying either an
            authorization `code` (and `iss` and `state`) or an `error`. The front end follows the redirect;
            the third-party client receives it and continues with **Exchange a code or refresh token**.
            """,
        )
        request {
            body<OAuthAuthorizationDecisionForm> {
                description = "`application/x-www-form-urlencoded`."
                required = true
                mediaTypes(ContentType.Application.FormUrlEncoded)
                example("Approve") { value = OAuthAuthorizationDecisionForm(requestUri = REQUEST_URI, decision = "approve") }
            }
        }
        response {
            code(HttpStatusCode.Found) {
                description =
                    "Redirect to the client's `redirect_uri` with `code`, `iss` and `state` on approval, or `error=access_denied` on denial."
                header<String>(
                    "Location",
                ) { this.description = "The client's `redirect_uri` with the result appended as query parameters." }
            }
            oauthError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "invalid_request",
                    "`request_uri` is missing or expired, or `decision` is not `approve` or `deny`.",
                    "decision must be approve or deny",
                ),
            )
            bearerUnauthorized(ErrorEnvelope.OAUTH)
            oauthError(HttpStatusCode.InternalServerError, serverError)
            oauthError(HttpStatusCode.NotImplemented, notConfigured)
        }
    }

    val exchangeToken: RouteConfig.() -> Unit = {
        publicOperation(
            "exchangeOAuthToken",
            ApiTags.OAUTH,
            "Exchange a code or refresh token",
            """
            Step four. Send `grant_type=authorization_code` with the `code` from the redirect, your
            `redirect_uri`, `client_id` and the PKCE `code_verifier`, and receive a DPoP-bound access token
            and a refresh token. Later, send `grant_type=refresh_token` with `refresh_token` and `client_id`
            to get a new pair.

            Tokens are bound to the key that signed the `DPoP` proof: send them as `Authorization: DPoP <token>`
            together with a fresh proof on every request, and rotate the nonce from each `DPoP-Nonce` header.
            The response is marked `Cache-Control: no-store`.
            """,
        )
        request {
            dpopHeader()
            body<OAuthTokenForm> {
                description = "`application/x-www-form-urlencoded`. Which fields are required depends on `grant_type`."
                required = true
                mediaTypes(ContentType.Application.FormUrlEncoded)
                example("Authorization code") {
                    value =
                        OAuthTokenForm(
                            grantType = "authorization_code",
                            code = "c-7f3c9a1b2d4e5f60",
                            redirectUri = "https://journalviewer.example/callback",
                            clientId = CLIENT_ID,
                            codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                        )
                }
                example("Refresh token") {
                    value = OAuthTokenForm(grantType = "refresh_token", clientId = CLIENT_ID, refreshToken = "rt-9a8b7c6d5e4f")
                }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "Tokens bound to your DPoP key. `sub` is the person's DID."
                header<String>("DPoP-Nonce") { this.description = "The nonce to put in your next DPoP proof." }
                body<OAuthTokenResponse> {
                    example("Example") {
                        value =
                            OAuthTokenResponse(
                                access_token = "at-eyJhbGciOiJFUzI1NiJ9.access-example",
                                token_type = "DPoP",
                                expires_in = 3600,
                                refresh_token = "rt-9a8b7c6d5e4f",
                                sub = DocExamples.DID,
                                scope = "atproto",
                            )
                    }
                }
            }
            oauthError(
                HttpStatusCode.BadRequest,
                invalidRequest,
                invalidClient,
                ErrorCase(
                    "invalid_grant",
                    "The code or refresh token is unknown, already used, expired, or issued to another client or key. Start the flow again.",
                    "Authorization code has expired",
                ),
                ErrorCase(
                    "unsupported_grant_type",
                    "`grant_type` is not `authorization_code` or `refresh_token`.",
                    "Unsupported grant_type: password",
                ),
                invalidDpop,
                useDpopNonce,
                headers = dpopNonceHeader,
            )
            oauthError(HttpStatusCode.InternalServerError, serverError)
            oauthError(HttpStatusCode.NotImplemented, notConfigured)
        }
    }

    val revokeToken: RouteConfig.() -> Unit = {
        publicOperation(
            "revokeOAuthToken",
            ApiTags.OAUTH,
            "Revoke a refresh token",
            """
            Ends an OAuth session (RFC 7009): send `token` (the refresh token) and `client_id` as a form, with
            a `DPoP` proof, and the refresh token is invalidated. Access tokens already issued expire on their
            own. Call it when the person disconnects your app.
            """,
        )
        request {
            dpopHeader()
            body<OAuthRevokeForm> {
                description =
                    "`application/x-www-form-urlencoded`. Confidential clients add `client_assertion_type` and `client_assertion`."
                required = true
                mediaTypes(ContentType.Application.FormUrlEncoded)
                example("Example") { value = OAuthRevokeForm(token = "rt-9a8b7c6d5e4f", clientId = CLIENT_ID) }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "The refresh token is revoked. The body is empty."
                header<String>("DPoP-Nonce") { this.description = "The nonce to put in your next DPoP proof." }
            }
            oauthError(HttpStatusCode.BadRequest, invalidRequest, invalidClient, invalidDpop, useDpopNonce, headers = dpopNonceHeader)
            oauthError(HttpStatusCode.InternalServerError, serverError)
            oauthError(HttpStatusCode.NotImplemented, notConfigured)
        }
    }
}
