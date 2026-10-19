package studio.hypertext.atproto.oauth.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.xrpc.KtorXrpcClient
import studio.hypertext.atproto.xrpc.XrpcClient
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A snapshot of a session's token state, without the tokens themselves.
 *
 * @property did The account the session authorizes.
 * @property issuer The authorization server that issued the tokens.
 * @property pdsUrl The account's data server.
 * @property scope The granted scopes, space-separated.
 * @property expiresAt When the access token expires, if known.
 */
public data class OAuthTokenInfo(
    val did: AtprotoDid,
    val issuer: String,
    val pdsUrl: String,
    val scope: String,
    val expiresAt: Instant?,
)

/**
 * An authorized OAuth session for one account.
 *
 * The session itself holds no tokens: every call reads the latest state from the
 * [OAuthSessionStore], renewing the access token first when it is about to expire. That makes a
 * session object cheap to create and safe to use from any server instance that shares the store.
 *
 * Requests to the account's data server go through [httpClient] or [xrpcClient], which add the
 * DPoP-bound `Authorization` header and a DPoP proof to every request, retry once when the server
 * asks for a fresh DPoP nonce, and renew the access token once when the server rejects it.
 * When the session has ended, those requests fail with an [OAuthSessionEndedException] (wrapped in
 * an `XrpcTransportException` when it comes through [xrpcClient]).
 *
 * @property did The account this session authorizes.
 */
public class OAuthSession internal constructor(
    public val did: AtprotoDid,
    private val manager: OAuthSessionManager,
    private val baseHttpClient: HttpClient,
    private val crypto: OAuthCrypto,
    private val nonces: DpopNonceCache,
    private val clock: Clock,
) {
    /**
     * Returns the current token state, renewing the access token according to [refresh].
     */
    public suspend fun tokenInfo(refresh: OAuthRefreshMode = OAuthRefreshMode.AUTO): OAuthTokenInfo {
        val stored = manager.session(did, refresh)
        return OAuthTokenInfo(
            did = stored.did,
            issuer = stored.issuer,
            pdsUrl = stored.pdsUrl,
            scope = stored.scope,
            expiresAt = stored.expiresAt,
        )
    }

    /**
     * An HTTP client that authenticates every request to the account's data server.
     *
     * It shares the engine of the client given to [AtprotoOAuthClient] but none of that client's
     * plugins. Requests to any other origin fail with [IllegalStateException] so the access token
     * is never sent elsewhere. Request bodies must be re-sendable (for example, text or bytes),
     * because a request may be sent up to three times.
     *
     * Closing this client does not close the shared engine.
     */
    public val httpClient: HttpClient by lazy {
        HttpClient(baseHttpClient.engine) {
            install(dpopAuthentication())
        }
    }

    /**
     * Returns an [XrpcClient] for the account's data server that authenticates with this session.
     */
    public suspend fun xrpcClient(): XrpcClient = KtorXrpcClient(httpClient, manager.session(did, OAuthRefreshMode.NEVER).pdsUrl)

    /**
     * Revokes the session at the authorization server and removes it from the session store.
     *
     * The local session is removed even if revocation fails; in that case this throws
     * [OAuthRevocationException] and the tokens stop working when they expire.
     */
    public suspend fun signOut() {
        manager.signOut(did)
    }

    private fun dpopAuthentication() =
        createClientPlugin("AtprotoOAuthDpop") {
            on(Send) { request ->
                var session = manager.session(did, OAuthRefreshMode.AUTO)
                val targetOrigin = request.url.build().origin()
                val pdsOrigin = Url(session.pdsUrl).origin()
                check(targetOrigin == pdsOrigin) {
                    "This session only authenticates requests to $pdsOrigin, not $targetOrigin"
                }

                suspend fun sendWithNonceRetry(): HttpResponse {
                    authorize(request, session)
                    var response = proceed(request).response
                    if (rememberNonce(request, response) && response.isDpopError("use_dpop_nonce")) {
                        authorize(request, session)
                        response = proceed(request).response
                        rememberNonce(request, response)
                    }
                    return response
                }

                var response = sendWithNonceRetry()
                if (response.isDpopError("invalid_token")) {
                    session = manager.session(did, OAuthRefreshMode.FORCE)
                    response = sendWithNonceRetry()
                    if (response.isDpopError("invalid_token")) {
                        manager.end(did, OAuthSessionRevokedException(did, "the data server rejected a freshly renewed access token"))
                    }
                }
                response.call
            }
        }

    private suspend fun authorize(
        request: HttpRequestBuilder,
        session: OAuthStoredSession,
    ) {
        val url = request.url.buildString()
        val proof =
            crypto.createDpopProof(
                dpopKey = session.dpopKey,
                htm = request.method.value,
                htu = dpopHtu(url),
                nonce = nonces.get(url),
                accessToken = session.accessToken,
                now = clock.now(),
            )
        request.headers[HttpHeaders.Authorization] = "DPoP ${session.accessToken}"
        request.headers[DPOP_HEADER] = proof
    }

    /**
     * Stores the response's DPoP nonce. Returns `true` if the nonce differs from the one sent.
     */
    private suspend fun rememberNonce(
        request: HttpRequestBuilder,
        response: HttpResponse,
    ): Boolean {
        val url = request.url.buildString()
        val nonce = response.headers[DPOP_NONCE_HEADER] ?: return false
        val previous = nonces.get(url)
        nonces.put(url, nonce)
        return nonce != previous
    }
}

/**
 * Whether the response is a 401 whose `WWW-Authenticate` challenge carries [error]
 * (RFC 6750 section 3, RFC 9449 section 9).
 */
private fun HttpResponse.isDpopError(error: String): Boolean {
    if (status != HttpStatusCode.Unauthorized) return false
    val challenge = headers[HttpHeaders.WWWAuthenticate] ?: return false
    val scheme = challenge.substringBefore(' ')
    val isTokenChallenge = scheme.equals("DPoP", ignoreCase = true) || scheme.equals("Bearer", ignoreCase = true)
    return isTokenChallenge && challenge.contains("error=\"$error\"")
}
