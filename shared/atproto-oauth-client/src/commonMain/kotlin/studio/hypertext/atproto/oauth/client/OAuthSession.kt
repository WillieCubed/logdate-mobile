package studio.hypertext.atproto.oauth.client

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.xrpc.KtorXrpcClient
import studio.hypertext.atproto.xrpc.XrpcClient
import studio.hypertext.atproto.xrpc.XrpcRequestBuilder
import studio.hypertext.atproto.xrpc.XrpcTransportException
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
 * The session itself holds no tokens and no HTTP client: every call reads the latest state from
 * the [OAuthSessionStore], renewing the access token first when it is about to expire, and sends
 * requests through the one data server client its [AtprotoOAuthClient] owns. That makes a session
 * object free to create, and safe to use from any server instance that shares the store.
 *
 * Requests to the account's data server go through [request] or [xrpcClient], which add the
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
    private val dataServer: DataServerHttp,
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
     * Sends an authenticated HTTP request to the account's data server, for anything [xrpcClient]
     * does not cover. Set the full URL in [block]; [tokenInfo] gives the data server's URL.
     *
     * The request goes through a client that shares the engine of the client given to
     * [AtprotoOAuthClient] but none of its plugins: a caller's logging or default-header plugins do
     * not apply, so they never see the access token.
     *
     * A request to any other origin fails with [OAuthForeignOriginException], so the access token
     * is never sent elsewhere; the check runs again after a renewal. Redirects are not followed,
     * for the same reason; a 3xx response is returned as it is. The request body must be
     * re-sendable (for example, text or bytes), because a request may be sent up to three times.
     */
    public suspend fun request(block: HttpRequestBuilder.() -> Unit): HttpResponse = inSessionContext { dataServer.client.request(block) }

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
        private suspend fun <T> withCurrent(call: suspend XrpcClient.() -> T): T {
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
            return inSessionContext { KtorXrpcClient(dataServer.client, pdsUrl).call() }
        }

        override suspend fun queryRaw(
            nsid: Nsid,
            block: XrpcRequestBuilder.() -> Unit,
        ): String = withCurrent { queryRaw(nsid, block) }

        override suspend fun <Response> query(
            nsid: Nsid,
            deserializer: DeserializationStrategy<Response>,
            block: XrpcRequestBuilder.() -> Unit,
        ): Response = withCurrent { query(nsid, deserializer, block) }

        override suspend fun procedureRaw(
            nsid: Nsid,
            body: String?,
            block: XrpcRequestBuilder.() -> Unit,
        ): String = withCurrent { procedureRaw(nsid, body, block) }

        override suspend fun <Body, Response> procedure(
            nsid: Nsid,
            body: Body,
            serializer: SerializationStrategy<Body>,
            deserializer: DeserializationStrategy<Response>,
            block: XrpcRequestBuilder.() -> Unit,
        ): Response = withCurrent { procedure(nsid, body, serializer, deserializer, block) }
    }

    /**
     * Runs [block] with this session named in the coroutine context, where the shared client's
     * DPoP plugin finds it. A failure comes back as a [Result] and is rethrown outside, so the
     * exception the caller sees is the original one rather than a copy made at the context
     * boundary by coroutine stack trace recovery.
     */
    private suspend fun <T> inSessionContext(block: suspend () -> T): T =
        withContext(OAuthSessionContext(did)) { runCatching { block() } }.getOrThrow()

    /**
     * Revokes the session at the authorization server and removes it from the session store.
     *
     * The local session is removed even if revocation fails; in that case this throws
     * [OAuthRevocationException] and the tokens stop working when they expire.
     */
    public suspend fun signOut() {
        manager.signOut(did)
    }
}
