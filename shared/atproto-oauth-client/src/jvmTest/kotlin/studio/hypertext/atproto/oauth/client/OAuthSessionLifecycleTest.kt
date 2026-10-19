package studio.hypertext.atproto.oauth.client

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.xrpc.XrpcTransportException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Renewal, concurrency, nonce, and pending sign-in behaviour across the session lifecycle.
 */
class OAuthSessionLifecycleTest {
    private val getSession = Nsid.require("com.atproto.server.getSession")

    @Test
    fun concurrentRenewalsSpendTheRefreshTokenOnce() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            val did = harness.signIn().session.did
            atmosphere.clock.advance(6.minutes)

            val renewed = (1..5).map { async { harness.client.restore(did).tokenInfo(OAuthRefreshMode.NEVER) } }.awaitAll()

            val grant = atmosphere.grantsFor(atmosphere.alice.did).single()
            assertEquals(1, grant.usedRefreshTokens.size, "the callers queued behind the first renewal reuse its result")
            assertEquals(false, grant.revoked)
            assertEquals(setOf(atmosphere.clock.now() + 5.minutes), renewed.map { it.expiresAt }.toSet())
        }

    @Test
    fun aRefreshRaceWithoutASharedLockSignsThePersonOut() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            val session = harness.signIn().session
            raceAnotherInstanceOnTheNextRefresh(harness, session.did)

            // The re-read finds the other instance's tokens, so this call still succeeds...
            harness.client.restore(session.did)

            // ...but the server saw a spent refresh token and ended the whole session, so the next
            // data server call finds every token revoked and the person has to sign in again.
            assertTrue(atmosphere.grantsFor(atmosphere.alice.did).single().revoked)
            val error = assertFailsWith<XrpcTransportException> { session.xrpcClient().queryRaw(getSession) }
            assertIs<OAuthSessionRejectedException>(error.cause)
            assertNull(harness.sessionStore.get(session.did))
        }

    @Test
    fun recoversFromARefreshRaceWhenTheServerDoesNotDetectReuse() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            atmosphere.detectRefreshTokenReuse = false
            val session = harness.signIn().session
            raceAnotherInstanceOnTheNextRefresh(harness, session.did)

            harness.client.restore(session.did)
            val body = session.xrpcClient().queryRaw(getSession)

            assertTrue(body.contains(atmosphere.alice.did))
            val grant = atmosphere.grantsFor(atmosphere.alice.did).single()
            assertEquals(grant.refreshToken, harness.sessionStore.get(session.did)!!.refreshToken, "the other instance's tokens are used")
        }

    /**
     * Expires the access token and arranges for a second server instance, with its own in-process
     * lock, to refresh the session while this instance's refresh request is on its way.
     */
    private fun raceAnotherInstanceOnTheNextRefresh(
        harness: OAuthTestHarness,
        did: AtprotoDid,
    ) {
        val atmosphere = harness.atmosphere
        val otherInstance = harness.newClientInstance()
        atmosphere.clock.advance(6.minutes)
        var raced = false
        atmosphere.beforeRequest = { request ->
            if (!raced && atmosphere.isRefreshRequest(request)) {
                raced = true
                otherInstance.restore(did, OAuthRefreshMode.FORCE)
            }
        }
    }

    @Test
    fun endsAndRevokesASessionWhoseAuthorizationServerChanged() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            val did = harness.signIn().session.did
            val refreshToken = harness.sessionStore.get(did)!!.refreshToken
            atmosphere.move(atmosphere.alice, atmosphere.otherPdsUrl)
            atmosphere.clock.advance(6.minutes)

            val error = assertFailsWith<OAuthSessionRevokedException> { harness.client.restore(did) }

            val mismatch = assertIs<OAuthIssuerMismatchException>(error.cause)
            assertEquals(atmosphere.otherIssuer, mismatch.actualIssuer)
            assertTrue(refreshToken in atmosphere.revokedTokens, "the tokens are revoked at the server that issued them")
            assertNull(harness.sessionStore.get(did))
        }

    @Test
    fun endsAndRevokesASessionWhoseRefreshedTokensNameAnotherAccount() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            val did = harness.signIn().session.did
            atmosphere.tokenSubjectOverride = atmosphere.bob.did
            atmosphere.clock.advance(6.minutes)

            val error = assertFailsWith<OAuthSessionRevokedException> { harness.client.restore(did) }

            assertIs<OAuthSubjectMismatchException>(error.cause)
            val grant = atmosphere.grantsFor(atmosphere.alice.did).single()
            assertTrue(grant.refreshToken in atmosphere.revokedTokens, "the tokens the refresh just issued are revoked")
            assertNull(harness.sessionStore.get(did))
        }

    @Test
    fun followsAnAccountToItsNewDataServer() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            val session = harness.signIn().session
            val xrpc = session.xrpcClient()
            xrpc.queryRaw(getSession)
            atmosphere.move(atmosphere.alice, atmosphere.secondPdsUrl)
            atmosphere.clock.advance(6.minutes)

            atmosphere.requestLog.clear()

            // The renewal runs before the request is addressed, so the first call goes to the new server.
            val body = xrpc.queryRaw(getSession)

            assertTrue(body.contains(atmosphere.alice.did))
            assertEquals(atmosphere.secondPdsUrl, harness.sessionStore.get(session.did)!!.pdsUrl)
            val dataServerRequests = atmosphere.requestLog.filter { "/xrpc/" in it }
            assertTrue(dataServerRequests.all { it.startsWith("GET ${atmosphere.secondPdsUrl}/") }, "$dataServerRequests")
        }

    @Test
    fun refusesARequestWhenARenewalMidRequestFindsTheAccountMoved() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            val session = harness.signIn().session
            atmosphere.move(atmosphere.alice, atmosphere.secondPdsUrl)
            // The old data server stops accepting the token before it expires, which forces a renewal.
            atmosphere.grantsFor(atmosphere.alice.did).single().accessTokenExpiresAt = atmosphere.clock.now()

            val error = assertFailsWith<XrpcTransportException> { session.xrpcClient().queryRaw(getSession) }

            val foreign = assertIs<OAuthForeignOriginException>(error.cause)
            assertEquals(atmosphere.pdsUrl, foreign.targetOrigin)
            assertEquals(atmosphere.secondPdsUrl, foreign.pdsOrigin)
        }

    @Test
    fun keepsSeparateNoncesForTheAuthorizationAndDataServers() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            val session = harness.signIn().session
            val xrpc = session.xrpcClient()

            xrpc.queryRaw(getSession)
            val firstCall = atmosphere.requestLog.count { it.endsWith("/xrpc/com.atproto.server.getSession") }
            xrpc.queryRaw(getSession)
            val secondCall = atmosphere.requestLog.count { it.endsWith("/xrpc/com.atproto.server.getSession") } - firstCall
            atmosphere.requestLog.clear()
            harness.client.restore(session.did, OAuthRefreshMode.FORCE)

            assertEquals(2, firstCall, "the data server's nonce is not the authorization server's, so the first call retries")
            assertEquals(1, secondCall)
            assertEquals(
                1,
                atmosphere.requestLog.count { it == "POST ${atmosphere.issuer}/oauth/token" },
                "the token endpoint nonce is kept",
            )
        }

    @Test
    fun worksWithServersThatDoNotUseNonces() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            atmosphere.requireNonce = false

            val session = harness.signIn().session
            val body = session.xrpcClient().queryRaw(getSession)

            assertTrue(body.contains(atmosphere.alice.did))
            assertEquals(1, atmosphere.requestLog.count { it == "POST ${atmosphere.issuer}/oauth/par" })
            assertEquals(1, atmosphere.requestLog.count { it.endsWith("/xrpc/com.atproto.server.getSession") })
        }

    @Test
    fun strictNoncesRefuseServersWithoutNonces() =
        runTest {
            val harness = OAuthTestHarness()
            harness.atmosphere.requireNonce = false
            val strict = harness.newClientInstance(strictDpopNonces = true)

            val error = assertFailsWith<OAuthServerResponseException> { strict.authorize("alice.example.com") }

            assertEquals("pushed_authorization_request", error.endpoint)
            assertTrue(error.message!!.contains("DPoP-Nonce"))
            assertEquals(0, harness.stateStore.size())
        }

    @Test
    fun strictNoncesAcceptServersThatUseThem() =
        runTest {
            val harness = OAuthTestHarness()
            val strict = harness.newClientInstance(strictDpopNonces = true)

            val redirect = strict.authorize("alice.example.com")
            val result = strict.callback(harness.atmosphere.approve(redirect.url))

            assertEquals(harness.atmosphere.alice.did, result.session.did.toString())
        }

    @Test
    fun removesThePendingSignInWhenThePushedRequestFails() =
        runTest {
            val harness = OAuthTestHarness()
            harness.atmosphere.parError = "invalid_request"

            val error = assertFailsWith<OAuthServerResponseException> { harness.client.authorize("alice.example.com") }

            assertEquals("invalid_request", error.error)
            assertEquals(0, harness.stateStore.size())
        }

    @Test
    fun pendingSignInsExpireFromTheStore() =
        runTest {
            val harness = OAuthTestHarness()
            harness.client.authorize("alice.example.com")
            assertEquals(1, harness.stateStore.size())

            harness.atmosphere.clock.advance(61.minutes)

            assertEquals(0, harness.stateStore.size())
        }

    @Test
    fun reportsAnExpiredSessionThatCannotBeRenewed() =
        runTest {
            val harness = OAuthTestHarness()
            harness.atmosphere.issueRefreshTokens = false
            val did = harness.signIn().session.did
            harness.atmosphere.clock.advance(6.minutes)

            assertFailsWith<OAuthSessionExpiredException> { harness.client.restore(did) }
            assertNull(harness.sessionStore.get(did))
        }

    @Test
    fun reportsAPendingSignInWhoseClientKeyWasRemoved() =
        runTest {
            val harness = OAuthTestHarness()
            val redirect = harness.client.authorize("alice.example.com")
            val parameters = harness.atmosphere.approve(redirect.url)
            val rotated = harness.newClientInstance(harness.config.copy(keys = listOf(JvmOAuthCrypto.generateClientKey("client-key-2"))))

            val error = assertFailsWith<OAuthClientKeyRemovedException> { rotated.callback(parameters) }

            assertEquals("client-key-1", error.keyId)
        }

    @Test
    fun reportsAFailedRenewalThroughTheXrpcClient() =
        runTest {
            val harness = OAuthTestHarness()
            val session = harness.signIn().session
            harness.atmosphere.clock.advance(6.minutes)
            harness.atmosphere.tokenEndpointFailureBody = "upstream unavailable"

            val error = assertFailsWith<XrpcTransportException> { session.xrpcClient().queryRaw(getSession) }

            val cause = assertIs<OAuthServerResponseException>(error.cause)
            assertEquals("token", cause.endpoint)
            assertEquals(502, cause.statusCode)
        }

    @Test
    fun aFailedSignOutKeepsBothTheStoreAndRevocationErrors() =
        runTest {
            val harness = OAuthTestHarness()
            val did = harness.signIn().session.did
            val client = harness.newClientInstance(sessions = DeleteFailingStore(harness.sessionStore))
            harness.atmosphere.revocationFails = true

            val error = assertFailsWith<StoreDownException> { client.revoke(did) }

            assertIs<OAuthServerResponseException>(error.suppressed.single())
        }

    @Test
    fun theInProcessLockForgetsAccountsNobodyIsUsing() =
        runTest {
            val lock = InProcessOAuthSessionLock()
            val dids = (0 until 20).map { AtprotoDid.require("did:plc:" + "b".repeat(23) + ('a' + it)) }

            dids.map { did -> async { lock.withLock(did) { lock.activeLockCount() } } }.awaitAll()

            assertEquals(0, lock.activeLockCount())
            lock.withLock(dids.first()) { assertEquals(1, lock.activeLockCount()) }
            assertEquals(0, lock.activeLockCount())
        }

    private class StoreDownException : Exception("The session store is unavailable")

    private class DeleteFailingStore(
        private val delegate: OAuthSessionStore,
    ) : OAuthSessionStore by delegate {
        override suspend fun delete(did: AtprotoDid): Unit = throw StoreDownException()
    }
}
