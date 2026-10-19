package studio.hypertext.atproto.oauth.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.xrpc.KtorXrpcClient
import studio.hypertext.atproto.xrpc.XrpcClient
import studio.hypertext.atproto.xrpc.XrpcRequestBuilder
import studio.hypertext.atproto.xrpc.XrpcTransportException
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
     * plugins: a caller's logging or default-header plugins do not apply here, so they never see
     * the access token. Add request logging, if needed, outside this client.
     *
     * Requests to any other origin fail with [OAuthForeignOriginException] so the access token is
     * never sent elsewhere; the check runs again after a renewal. Redirects are not followed, for
     * the same reason; a 3xx response is returned as it is. Request bodies must be re-sendable (for
     * example, text or bytes), because a request may be sent up to three times.
     *
     * Closing this client does not close the shared engine.
     */
    public val httpClient: HttpClient by lazy {
        dataServerHttpClient(baseHttpClient) {
            install(dpopAuthentication())
        }
    }

    /**
     * Returns an [XrpcClient] for the account's data server that authenticates with this session.
     *
     * Each call renews the access token if it is about to expire, then sends the request to the
     * account's current data server, so the client keeps working after the account moves to
     * another one. Every failure, including a failed renewal and [OAuthSessionNotFoundException]
     * once the session has been removed, arrives as the cause of an `XrpcTransportException`.
     */
    public fun xrpcClient(): XrpcClient = SessionXrpcClient()

    private inner class SessionXrpcClient : XrpcClient {
        private suspend fun current(): XrpcClient {
            // Renewing first means the request is addressed to the data server the renewal found.
            // Any renewal failure (an ended session, a token endpoint error, a network or resolution
            // failure) is reported the way the XRPC client reports its own failures.
            val pdsUrl =
                try {
                    manager.session(did, OAuthRefreshMode.AUTO).pdsUrl
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    throw XrpcTransportException("XRPC request failed", exception)
                }
            return KtorXrpcClient(httpClient, pdsUrl)
        }

        override suspend fun queryRaw(
            nsid: Nsid,
            block: XrpcRequestBuilder.() -> Unit,
        ): String = current().queryRaw(nsid, block)

        override suspend fun <Response> query(
            nsid: Nsid,
            deserializer: DeserializationStrategy<Response>,
            block: XrpcRequestBuilder.() -> Unit,
        ): Response = current().query(nsid, deserializer, block)

        override suspend fun procedureRaw(
            nsid: Nsid,
            body: String?,
            block: XrpcRequestBuilder.() -> Unit,
        ): String = current().procedureRaw(nsid, body, block)

        override suspend fun <Body, Response> procedure(
            nsid: Nsid,
            body: Body,
            serializer: SerializationStrategy<Body>,
            deserializer: DeserializationStrategy<Response>,
            block: XrpcRequestBuilder.() -> Unit,
        ): Response = current().procedure(nsid, body, serializer, deserializer, block)
    }

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
                requireDataServerOrigin(request, session)

                suspend fun sendWithNonceRetry(): HttpResponse {
                    val sentNonce = authorize(request, session, nonces.get(request.url.buildString()))
                    var response = proceed(request).response
                    val freshNonce = rememberNonce(request, response)?.takeIf { it != sentNonce }
                    if (freshNonce != null && response.isDpopError("use_dpop_nonce")) {
                        authorize(request, session, freshNonce)
                        response = proceed(request).response
                        rememberNonce(request, response)
                    }
                    return response
                }

                var response = sendWithNonceRetry()
                if (response.isDpopError("invalid_token")) {
                    session = manager.renewAfterRejection(session)
                    // The renewal re-resolves the account, which may now live on another data server.
                    requireDataServerOrigin(request, session)
                    response = sendWithNonceRetry()
                    if (response.isDpopError("invalid_token")) {
                        manager.end(did, OAuthSessionRevokedException(did, "the data server rejected a freshly renewed access token"))
                    }
                }
                response.call
            }
        }

    private fun requireDataServerOrigin(
        request: HttpRequestBuilder,
        session: OAuthStoredSession,
    ) {
        val targetOrigin = request.url.build().origin()
        val pdsOrigin = Url(session.pdsUrl).origin()
        if (targetOrigin != pdsOrigin) throw OAuthForeignOriginException(did, targetOrigin, pdsOrigin)
    }

    /**
     * Adds the access token and a DPoP proof carrying [nonce], and returns [nonce].
     */
    private fun authorize(
        request: HttpRequestBuilder,
        session: OAuthStoredSession,
        nonce: String?,
    ): String? {
        val proof =
            crypto.createDpopProof(
                dpopKey = session.dpopKey,
                htm = request.method.value,
                htu = dpopHtu(request.url.buildString()),
                nonce = nonce,
                accessToken = session.accessToken,
                now = clock.now(),
            )
        request.headers[HttpHeaders.Authorization] = "DPoP ${session.accessToken}"
        request.headers[DPOP_HEADER] = proof
        return nonce
    }

    /**
     * Stores and returns the response's DPoP nonce, if it has one.
     */
    private suspend fun rememberNonce(
        request: HttpRequestBuilder,
        response: HttpResponse,
    ): String? {
        val nonce = response.headers[DPOP_NONCE_HEADER] ?: return null
        nonces.put(request.url.buildString(), nonce)
        return nonce
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
