package studio.hypertext.atproto.oauth.client

import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.identity.IdentityResolver
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Options for one authorization request.
 *
 * @property scope The scopes to request, space-separated. Defaults to every scope in the client
 *   configuration. Must include `atproto` and stay within the configured scopes.
 * @property redirectUri One of the configured redirect URIs. Defaults to the first.
 * @property appState Opaque data returned from [AtprotoOAuthClient.callback], such as the page to
 *   return to. It is stored server-side, never sent to the authorization server.
 * @property prompt The OpenID Connect `prompt` value, for example `login` or `consent`.
 */
public data class OAuthAuthorizeOptions(
    val scope: String? = null,
    val redirectUri: String? = null,
    val appState: String? = null,
    val prompt: String? = null,
)

/**
 * Where to send the person to approve the sign-in.
 *
 * @property url The authorization server URL to redirect the browser to.
 * @property state The `state` value that identifies this request in the callback.
 * @property issuer The authorization server the request was pushed to.
 */
public data class OAuthAuthorizationRedirect(
    val url: String,
    val state: String,
    val issuer: String,
)

/**
 * The outcome of a successful callback.
 *
 * @property session The new session, already saved in the session store.
 * @property appState The [OAuthAuthorizeOptions.appState] given when the sign-in started.
 * @property previousSessionRevocationFailure When the account already had a session, the client
 *   replaces it and revokes its tokens. If that revocation failed, this is the reason; the old
 *   tokens then stop working when they expire.
 */
public data class OAuthCallbackResult(
    val session: OAuthSession,
    val appState: String?,
    val previousSessionRevocationFailure: Throwable? = null,
)

/**
 * A confidential AT Protocol OAuth client: it signs people in with accounts hosted anywhere in
 * the Atmosphere and makes DPoP-authenticated requests to their data servers.
 *
 * The flow has three steps:
 * 1. [authorize] resolves the account to its authorization server, pushes the authorization
 *    request (PAR), and returns the URL to send the person to.
 * 2. The authorization server sends the person back to a redirect URI. Pass the query parameters
 *    to [callback], which exchanges the code for tokens, verifies that the server is really
 *    responsible for the account, and saves the session.
 * 3. Later, from any server instance that shares the stores, [restore] the session by DID and use
 *    [OAuthSession.xrpcClient] or [OAuthSession.httpClient].
 *
 * Serve [clientMetadata] at [OAuthClientConfig.clientId] so authorization servers can identify the
 * client.
 *
 * @param config The client configuration and keys.
 * @param identityResolver Resolves handles and DIDs. It should support every handle resolution
 *   method (DNS TXT and HTTPS) and every DID method the AT Protocol allows.
 * @param httpClient The HTTP client for metadata, token, and data server requests.
 * @param crypto Platform cryptography; `JvmOAuthCrypto` on the JVM.
 * @param stateStore Storage for pending authorization requests.
 * @param sessionStore Storage for sessions. Share it between server instances.
 * @param sessionLock Serializes token refreshes per account. See [OAuthSessionLock].
 * @param clock Time source, replaceable in tests.
 * @param allowHttp Accept `http://` data and authorization servers. Only for local development.
 * @param stateLifetime How long a pending authorization request stays valid.
 */
public class AtprotoOAuthClient(
    public val config: OAuthClientConfig,
    identityResolver: IdentityResolver,
    private val httpClient: HttpClient,
    private val crypto: OAuthCrypto,
    private val stateStore: OAuthStateStore,
    private val sessionStore: OAuthSessionStore,
    sessionLock: OAuthSessionLock = InProcessOAuthSessionLock(),
    private val clock: Clock = Clock.System,
    allowHttp: Boolean = false,
    private val stateLifetime: Duration = 1.hours,
) {
    /**
     * The resolver this client uses to find authorization servers.
     */
    public val resolver: OAuthServerResolver = OAuthServerResolver(identityResolver, httpClient, allowHttp)

    private val nonces = DpopNonceCache()
    private val manager =
        OAuthSessionManager(
            config = config,
            resolver = resolver,
            httpClient = httpClient,
            crypto = crypto,
            nonces = nonces,
            sessionStore = sessionStore,
            sessionLock = sessionLock,
            clock = clock,
        )

    /**
     * The client metadata document to serve at [OAuthClientConfig.clientId].
     */
    public val clientMetadata: OAuthClientMetadata = config.toClientMetadata()

    /**
     * The public client keys, to serve at [OAuthClientConfig.jwksUri] when one is configured.
     */
    public val jwks: JsonWebKeySet = JsonWebKeySet(config.keys.map { it.publicJwk })

    /**
     * Returns [clientMetadata] as the JSON body to serve.
     */
    public fun clientMetadataJson(): String = metadataJson.encodeToString(OAuthClientMetadata.serializer(), clientMetadata)

    /**
     * Starts a sign-in for [input]: a handle (`alice.example.com`), a DID, or the `https://` URL of
     * a data server or entryway.
     *
     * @throws OAuthResolutionException if the account or its servers cannot be found or used.
     * @throws OAuthServerResponseException if the authorization server rejects the request.
     */
    public suspend fun authorize(
        input: String,
        options: OAuthAuthorizeOptions = OAuthAuthorizeOptions(),
    ): OAuthAuthorizationRedirect {
        val redirectUri = options.redirectUri ?: config.redirectUris.first()
        require(redirectUri in config.redirectUris) { "$redirectUri is not one of the configured redirect URIs" }
        val scope = options.scope ?: config.scope
        val allowedScopes = config.scope.split(' ').toSet()
        val requestedScopes = scope.split(' ').filter { it.isNotEmpty() }
        require(ATPROTO_SCOPE in requestedScopes) { "The requested scope must include \"$ATPROTO_SCOPE\"" }
        require(allowedScopes.containsAll(requestedScopes)) {
            "The requested scope \"$scope\" goes beyond the configured scope \"${config.scope}\""
        }

        val resolved = resolver.resolve(input)
        val metadata = resolved.metadata
        val pkceVerifier = crypto.randomToken(32)
        val dpopKey = crypto.generateEs256Key()
        val clientKey = config.signingKey
        val state = crypto.randomToken(32)

        stateStore.save(
            OAuthAuthorizationState(
                state = state,
                issuer = metadata.issuer,
                dpopKey = dpopKey,
                clientKeyId = clientKey.kid,
                pkceVerifier = pkceVerifier,
                redirectUri = redirectUri,
                expectedDid = resolved.identity?.did,
                appState = options.appState,
                createdAt = clock.now(),
            ),
        )

        val parameters =
            buildMap {
                put("response_type", "code")
                put("redirect_uri", redirectUri)
                put("scope", requestedScopes.joinToString(" "))
                put("state", state)
                put("code_challenge", crypto.pkceChallenge(pkceVerifier))
                put("code_challenge_method", "S256")
                resolved.identity?.let { put("login_hint", input.trim().removePrefix("@")) }
                options.prompt?.let { put("prompt", it) }
            }
        val requestUri =
            try {
                manager.agentFor(metadata, dpopKey, clientKey).pushAuthorizationRequest(parameters)
            } catch (exception: Exception) {
                stateStore.take(state)
                throw exception
            }

        val url =
            URLBuilder(metadata.authorizationEndpoint)
                .apply {
                    this.parameters.append("client_id", config.clientId)
                    this.parameters.append("request_uri", requestUri)
                }.buildString()
        return OAuthAuthorizationRedirect(url = url, state = state, issuer = metadata.issuer)
    }

    /**
     * Completes a sign-in from the query parameters of the redirect back to the client.
     *
     * Before the session is saved, the client verifies that `iss` matches the authorization server
     * the request went to, that `sub` is the account the sign-in started for, and that the
     * account's current data server still names that authorization server.
     *
     * @throws OAuthConsentDeniedException if the person declined.
     * @throws OAuthAuthorizationErrorException if the server returned another error.
     * @throws OAuthCallbackException if the parameters do not match a pending request.
     * @throws OAuthVerificationException if an identity check fails.
     * @throws OAuthServerResponseException if the token request fails.
     */
    public suspend fun callback(parameters: Map<String, String>): OAuthCallbackResult {
        val (pending, code) = takePendingRequest(parameters)

        val metadata = resolver.authorizationServerMetadata(pending.issuer)
        val issuerParameter = parameters["iss"]
        if (issuerParameter != null && issuerParameter != pending.issuer) {
            throw OAuthIssuerMismatchException(pending.issuer, issuerParameter)
        }
        if (issuerParameter == null && metadata.authorizationResponseIssParameterSupported) {
            throw OAuthCallbackException(OAuthCallbackException.Reason.MISSING_ISSUER, "The callback has no iss parameter")
        }

        val clientKey =
            config.key(pending.clientKeyId)
                ?: throw IllegalStateException("Client key ${pending.clientKeyId} was removed while the sign-in was pending")
        val agent = manager.agentFor(metadata, pending.dpopKey, clientKey)
        val tokens = agent.exchangeCode(code, pending.pkceVerifier, pending.redirectUri)
        val now = clock.now()

        val (did, pdsUrl) =
            try {
                verifySubject(tokens.sub, pending)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                revokeQuietlyInto(exception, agent, tokens.accessToken)
                throw exception
            }

        val previousFailure = revokePrevious(did)
        saveNewSession(
            agent = agent,
            session =
                OAuthStoredSession(
                    did = did,
                    issuer = pending.issuer,
                    pdsUrl = pdsUrl,
                    scope = tokens.scope,
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    expiresAt = tokens.expiresAt(now),
                    dpopKey = pending.dpopKey,
                    clientKeyId = pending.clientKeyId,
                ),
        )
        return OAuthCallbackResult(
            session = newSession(did),
            appState = pending.appState,
            previousSessionRevocationFailure = previousFailure,
        )
    }

    /**
     * Loads the stored session for [did], renewing its access token according to [refresh].
     *
     * @throws OAuthSessionNotFoundException if there is no session for [did].
     * @throws OAuthSessionEndedException if the session expired or was revoked; it is removed.
     */
    public suspend fun restore(
        did: AtprotoDid,
        refresh: OAuthRefreshMode = OAuthRefreshMode.AUTO,
    ): OAuthSession {
        manager.session(did, refresh)
        return newSession(did)
    }

    /**
     * Revokes and removes the session for [did]. Does nothing if there is no session.
     *
     * @throws OAuthRevocationException if the server did not confirm revocation. The local session
     *   is removed anyway.
     */
    public suspend fun revoke(did: AtprotoDid) {
        manager.signOut(did)
    }

    /**
     * Checks the callback parameters, consumes the matching pending request, and returns it with
     * the authorization code.
     */
    private suspend fun takePendingRequest(parameters: Map<String, String>): Pair<OAuthAuthorizationState, String> {
        if (parameters["response"] != null) {
            throw OAuthCallbackException(
                OAuthCallbackException.Reason.UNSUPPORTED_RESPONSE,
                "JWT-secured authorization responses are not supported",
            )
        }
        val stateValue =
            parameters["state"]
                ?: throw OAuthCallbackException(OAuthCallbackException.Reason.MISSING_STATE, "The callback has no state parameter")
        val pending =
            stateStore.take(stateValue)
                ?: throw OAuthCallbackException(OAuthCallbackException.Reason.UNKNOWN_STATE, "No pending sign-in matches this state")
        if (clock.now() - pending.createdAt > stateLifetime) {
            throw OAuthCallbackException(OAuthCallbackException.Reason.EXPIRED_STATE, "The sign-in request expired; start again")
        }

        parameters["error"]?.let { error ->
            val description = parameters["error_description"]
            if (error == "access_denied") throw OAuthConsentDeniedException(description, pending.appState)
            throw OAuthAuthorizationErrorException(error, description, pending.appState)
        }
        val code =
            parameters["code"]
                ?: throw OAuthCallbackException(OAuthCallbackException.Reason.MISSING_CODE, "The callback has no code parameter")
        return pending to code
    }

    private suspend fun saveNewSession(
        agent: OAuthServerAgent,
        session: OAuthStoredSession,
    ) {
        try {
            sessionStore.save(session)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            revokeQuietlyInto(exception, agent, session.refreshToken ?: session.accessToken)
            throw exception
        }
    }

    private suspend fun verifySubject(
        subject: String,
        pending: OAuthAuthorizationState,
    ): Pair<AtprotoDid, String> {
        val did =
            AtprotoDid.parse(subject).getOrElse {
                throw OAuthInvalidTokenResponseException("sub \"$subject\" is not an AT Protocol DID")
            }
        pending.expectedDid?.let { expected ->
            if (did != expected) throw OAuthSubjectMismatchException(expected, subject)
        }
        val (pdsUrl, currentServer) = resolver.resolveForDid(did)
        if (currentServer.issuer != pending.issuer) throw OAuthIssuerMismatchException(pending.issuer, currentServer.issuer)
        return did to pdsUrl
    }

    private suspend fun revokePrevious(did: AtprotoDid): Throwable? {
        if (sessionStore.get(did) == null) return null
        return try {
            manager.signOut(did)
            null
        } catch (exception: OAuthRevocationException) {
            exception
        }
    }

    private suspend fun revokeQuietlyInto(
        primary: Exception,
        agent: OAuthServerAgent,
        token: String,
    ) {
        try {
            agent.revoke(token)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            primary.addSuppressed(exception)
        }
    }

    private fun newSession(did: AtprotoDid): OAuthSession =
        OAuthSession(
            did = did,
            manager = manager,
            baseHttpClient = httpClient,
            crypto = crypto,
            nonces = nonces,
            clock = clock,
        )

    private companion object {
        val metadataJson = Json { explicitNulls = false }
    }
}
