package studio.hypertext.atproto.oauth.client

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import studio.hypertext.atproto.syntax.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * What the client keeps to itself: HTTP clients on the shared engine, and tokens away from caller
 * plugins and redirect targets.
 */
class OAuthClientIsolationTest {
    private val getSession = Nsid.require("com.atproto.server.getSession")

    @Test
    fun restoringSessionsAddsNothingToTheEngine() =
        runTest {
            val harness = OAuthTestHarness()
            val did = harness.signIn().session.did
            val engineJob = harness.atmosphere.httpClient.engine.coroutineContext[Job]!!
            val before = engineJob.children.count()

            repeat(100) {
                harness.client
                    .restore(did)
                    .xrpcClient()
                    .queryRaw(getSession)
            }

            assertEquals(before, engineJob.children.count())
        }

    @Test
    fun closingTheClientReleasesItsHttpClients() =
        runTest {
            val harness = OAuthTestHarness()
            val engineJob = harness.atmosphere.httpClient.engine.coroutineContext[Job]!!
            val before = engineJob.children.count()
            val client = harness.newClientInstance()
            assertTrue(engineJob.children.count() > before)

            client.close()

            assertEquals(before, engineJob.children.count())
        }

    @Test
    fun callerPluginsNeverSeeTokensOrClientAssertions() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            val seenByPlugin = mutableListOf<String>()
            val recorder = createClientPlugin("Recorder") { onRequest { request, _ -> seenByPlugin += request.url.buildString() } }
            val instrumented =
                atmosphere.httpClient.config {
                    install(recorder)
                    defaultRequest { header(CALLER_HEADER, "present") }
                }
            val headerReachedServer = mutableListOf<String>()
            atmosphere.beforeRequest = { request ->
                if (request.headers[CALLER_HEADER] != null) headerReachedServer += request.url.toString()
            }
            val client = harness.newClientInstance(httpClient = instrumented)

            val redirect = client.authorize("alice.example.com")
            val session = client.callback(atmosphere.approve(redirect.url)).session
            session.xrpcClient().queryRaw(getSession)
            atmosphere.clock.advance(6.minutes)
            session.xrpcClient().queryRaw(getSession)
            session.signOut()

            assertEquals(
                1,
                atmosphere
                    .grantsFor(atmosphere.alice.did)
                    .single()
                    .usedRefreshTokens.size,
                "a refresh happened",
            )
            assertTrue(atmosphere.revokedTokens.isNotEmpty(), "a revocation happened")
            assertEquals(emptyList(), seenByPlugin)
            assertEquals(emptyList(), headerReachedServer)
        }

    @Test
    fun dataServerRedirectsAreReturnedNotFollowed() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            val session = harness.signIn().session

            val response = session.request { url("${atmosphere.pdsUrl}/xrpc/com.example.redirect") }

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals(MockAtmosphere.REDIRECT_TARGET, response.headers[HttpHeaders.Location])
            assertFalse(atmosphere.requestLog.any { "collector.example.org" in it }, "${atmosphere.requestLog}")
        }

    private companion object {
        const val CALLER_HEADER = "X-Caller-Plugin"
    }
}
