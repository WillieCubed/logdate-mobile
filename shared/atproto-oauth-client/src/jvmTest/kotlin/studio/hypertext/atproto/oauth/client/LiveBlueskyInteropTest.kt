package studio.hypertext.atproto.oauth.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import studio.hypertext.atproto.identity.DefaultIdentityResolver
import studio.hypertext.atproto.identity.DidPlcResolver
import studio.hypertext.atproto.identity.WellKnownHandleResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

/**
 * Checks the client against Bluesky's production authorization server.
 *
 * These tests reach the public network, so they run only when `ATPROTO_OAUTH_LIVE=1` is set:
 *
 * ```
 * ATPROTO_OAUTH_LIVE=1 ./gradlew :shared:atproto-oauth-client:jvmTest --tests '*LiveBlueskyInteropTest*'
 * ```
 *
 * They cannot finish a sign-in, which needs a person at the consent screen and a client metadata
 * document on the public web. They do confirm that resolution and Bluesky's metadata pass every
 * check, and that Bluesky (which runs the reference `@atproto/oauth-provider`) accepts the client's
 * pushed authorization request parameters, DPoP proofs, and nonce handling: the request is refused
 * only at the next step, because the made-up client's metadata cannot be fetched.
 */
class LiveBlueskyInteropTest {
    private val enabled = System.getenv("ATPROTO_OAUTH_LIVE") == "1"

    @Test
    fun resolvesABlueskyAccountToBlueskysAuthorizationServer() =
        runTest(timeout = 60.seconds) {
            assumeTrue("Set ATPROTO_OAUTH_LIVE=1 to run checks against Bluesky", enabled)
            val httpClient = HttpClient(OkHttp)
            val resolver = OAuthServerResolver(identityResolver(httpClient), httpClient)

            val resolved = resolver.resolve(BLUESKY_DID)

            assertEquals(BLUESKY_ISSUER, resolved.metadata.issuer)
        }

    @Test
    fun blueskyAcceptsTheClientsDpopProofs() =
        runTest(timeout = 60.seconds) {
            assumeTrue("Set ATPROTO_OAUTH_LIVE=1 to run checks against Bluesky", enabled)
            val httpClient = HttpClient(OkHttp)
            val client =
                AtprotoOAuthClient(
                    config =
                        OAuthClientConfig(
                            clientId = "https://example.com/oauth-client-metadata.json",
                            redirectUris = listOf("https://example.com/oauth/callback"),
                            keys = listOf(JvmOAuthCrypto.generateClientKey("live-check")),
                        ),
                    identityResolver = identityResolver(httpClient),
                    httpClient = httpClient,
                    crypto = JvmOAuthCrypto,
                    stateStore = InMemoryOAuthStateStore(),
                    sessionStore = InMemoryOAuthSessionStore(),
                )

            val error = assertFailsWith<OAuthServerResponseException> { client.authorize(BLUESKY_ISSUER) }

            // Bluesky checks the DPoP proof (and asks for a nonce) before it loads the client metadata,
            // so reaching the metadata check means the proof, the nonce retry, and the request
            // parameters were all accepted.
            assertEquals("pushed_authorization_request", error.endpoint)
            assertEquals("invalid_client_metadata", error.error, error.message)
        }

    private fun identityResolver(httpClient: HttpClient) =
        DefaultIdentityResolver(DidPlcResolver(httpClient), WellKnownHandleResolver(httpClient))

    private companion object {
        /** The public DID of the bsky.app account, published in the PLC directory; not a secret. */
        const val BLUESKY_DID = "did:plc:z72i7hdynmk6r22z27h6tvur" // ggignore
        const val BLUESKY_ISSUER = "https://bsky.social"
    }
}
