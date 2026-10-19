package studio.hypertext.atproto.oauth.client

import io.ktor.client.request.get
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.xrpc.XrpcProtocolException
import studio.hypertext.atproto.xrpc.XrpcTransportException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class AtprotoOAuthClientFlowTest {
    private val getSession = Nsid.require("com.atproto.server.getSession")

    @Test
    fun signsInFromAHandleAndCallsTheDataServer() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere

            val redirect = harness.client.authorize("@alice.example.com", OAuthAuthorizeOptions(appState = "return-to=/journal"))

            val url = Url(redirect.url)
            assertEquals("${atmosphere.issuer}/oauth/authorize", "${url.protocol.name}://${url.host}${url.encodedPath}")
            assertEquals(OAuthTestHarness.CLIENT_ID, url.parameters["client_id"])
            assertEquals(atmosphere.issuer, redirect.issuer)
            val parAttempts = atmosphere.requestLog.count { it == "POST ${atmosphere.issuer}/oauth/par" }
            assertEquals(2, parAttempts, "the first PAR has no nonce, so the server asks for one and the client retries")

            val result = harness.client.callback(atmosphere.approve(redirect.url))

            assertEquals("return-to=/journal", result.appState)
            assertNull(result.previousSessionRevocationFailure)
            val alice = AtprotoDid.require(atmosphere.alice.did)
            assertEquals(alice, result.session.did)
            val info = result.session.tokenInfo()
            assertEquals(atmosphere.issuer, info.issuer)
            assertEquals(atmosphere.pdsUrl, info.pdsUrl)
            assertEquals("atproto transition:generic", info.scope)
            assertEquals(atmosphere.clock.now() + 5.minutes, info.expiresAt)

            val body = result.session.xrpcClient().queryRaw(getSession)

            val did =
                Json
                    .parseToJsonElement(body)
                    .jsonObject["did"]!!
                    .jsonPrimitive.content
            assertEquals(atmosphere.alice.did, did)
            val stored = assertNotNull(harness.sessionStore.get(alice))
            assertEquals(harness.keys.first().kid, stored.clientKeyId)
            assertTrue(stored.dpopKey.isPrivate)
        }

    @Test
    fun signsInFromADid() =
        runTest {
            val harness = OAuthTestHarness()

            val result = harness.signIn(input = harness.atmosphere.alice.did)

            assertEquals(harness.atmosphere.alice.did, result.session.did.toString())
        }

    @Test
    fun signsInFromAServerUrlAndVerifiesTheAccountAfterwards() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere

            val redirect = harness.client.authorize(atmosphere.pdsUrl)
            val result = harness.client.callback(atmosphere.approve(redirect.url, atmosphere.bob))

            assertEquals(atmosphere.bob.did, result.session.did.toString())
        }

    @Test
    fun reportsDeclinedConsentWithTheAppState() =
        runTest {
            val harness = OAuthTestHarness()
            val redirect = harness.client.authorize("alice.example.com", OAuthAuthorizeOptions(appState = "app-state"))

            val error = assertFailsWith<OAuthConsentDeniedException> { harness.client.callback(harness.atmosphere.deny(redirect.url)) }

            assertEquals("app-state", error.appState)
            assertEquals("The user declined", error.errorDescription)
        }

    @Test
    fun rejectsAReplayedCallback() =
        runTest {
            val harness = OAuthTestHarness()
            val redirect = harness.client.authorize("alice.example.com")
            val parameters = harness.atmosphere.approve(redirect.url)
            harness.client.callback(parameters)

            val error = assertFailsWith<OAuthCallbackException> { harness.client.callback(parameters) }

            assertEquals(OAuthCallbackException.Reason.UNKNOWN_STATE, error.reason)
        }

    @Test
    fun rejectsAnExpiredPendingSignIn() =
        runTest {
            val harness = OAuthTestHarness()
            val redirect = harness.client.authorize("alice.example.com")
            val parameters = harness.atmosphere.approve(redirect.url)
            harness.atmosphere.clock.advance(61.minutes)

            val error = assertFailsWith<OAuthCallbackException> { harness.client.callback(parameters) }

            assertEquals(OAuthCallbackException.Reason.EXPIRED_STATE, error.reason)
        }

    @Test
    fun rejectsACallbackFromAnotherIssuer() =
        runTest {
            val harness = OAuthTestHarness()
            val redirect = harness.client.authorize("alice.example.com")
            val parameters = harness.atmosphere.approve(redirect.url) + ("iss" to "https://evil.example.com")

            val error = assertFailsWith<OAuthIssuerMismatchException> { harness.client.callback(parameters) }

            assertEquals(harness.atmosphere.issuer, error.expectedIssuer)
        }

    @Test
    fun rejectsACallbackWithoutTheIssuerWhenTheServerPromisedIt() =
        runTest {
            val harness = OAuthTestHarness()
            val redirect = harness.client.authorize("alice.example.com")
            val parameters = harness.atmosphere.approve(redirect.url) - "iss"

            val error = assertFailsWith<OAuthCallbackException> { harness.client.callback(parameters) }

            assertEquals(OAuthCallbackException.Reason.MISSING_ISSUER, error.reason)
        }

    @Test
    fun rejectsATokenForAnotherAccountAndRevokesIt() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            atmosphere.tokenSubjectOverride = atmosphere.bob.did

            assertFailsWith<OAuthSubjectMismatchException> { harness.signIn() }

            assertEquals(1, atmosphere.revokedTokens.size, "the unusable access token is revoked")
            assertNull(harness.sessionStore.get(AtprotoDid.require(atmosphere.bob.did)))
            assertNull(harness.sessionStore.get(AtprotoDid.require(atmosphere.alice.did)))
        }

    @Test
    fun rejectsAnAccountTheAuthorizationServerDoesNotSpeakFor() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            // The sign-in starts from a server, and the server claims an account hosted elsewhere.
            atmosphere.tokenSubjectOverride = atmosphere.elsewhere.did
            val redirect = harness.client.authorize(atmosphere.pdsUrl)

            val error =
                assertFailsWith<OAuthIssuerMismatchException> { harness.client.callback(atmosphere.approve(redirect.url, atmosphere.bob)) }

            assertEquals(atmosphere.otherIssuer, error.actualIssuer)
            assertNull(harness.sessionStore.get(AtprotoDid.require(atmosphere.elsewhere.did)))
        }

    @Test
    fun rejectsATokenResponseWithoutTheAtprotoScope() =
        runTest {
            val harness = OAuthTestHarness()
            harness.atmosphere.grantedScopeOverride = "transition:generic"

            assertFailsWith<OAuthInvalidTokenResponseException> { harness.signIn() }
        }

    @Test
    fun rejectsScopesBeyondTheClientMetadata() =
        runTest {
            val harness = OAuthTestHarness()

            assertFailsWith<IllegalArgumentException> {
                harness.client.authorize("alice.example.com", OAuthAuthorizeOptions(scope = "atproto transition:chat.bsky"))
            }
        }

    @Test
    fun replacesAnEarlierSessionForTheSameAccountAndRevokesIt() =
        runTest {
            val harness = OAuthTestHarness()
            val first = harness.signIn()
            val firstRefreshToken = harness.sessionStore.get(first.session.did)!!.refreshToken

            val second = harness.signIn()

            assertNull(second.previousSessionRevocationFailure)
            assertTrue(firstRefreshToken in harness.atmosphere.revokedTokens)
            assertNotEquals(firstRefreshToken, harness.sessionStore.get(second.session.did)!!.refreshToken)
        }

    @Test
    fun refreshesAnExpiredAccessTokenWithTheSameKeys() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            val session = harness.signIn().session
            val before = harness.sessionStore.get(session.did)!!
            atmosphere.clock.advance(6.minutes)

            val body = session.xrpcClient().queryRaw(getSession)

            assertTrue(body.contains(atmosphere.alice.did))
            val after = harness.sessionStore.get(session.did)!!
            assertNotEquals(before.accessToken, after.accessToken)
            assertNotEquals(before.refreshToken, after.refreshToken)
            assertEquals(before.dpopKey, after.dpopKey)
            assertEquals(
                1,
                atmosphere
                    .grantsFor(atmosphere.alice.did)
                    .single()
                    .usedRefreshTokens.size,
            )
        }

    @Test
    fun retriesWithANewNonceWhenTheServerRotatesIt() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            val session = harness.signIn().session
            session.xrpcClient().queryRaw(getSession)
            atmosphere.rotateNonce()
            atmosphere.requestLog.clear()

            session.xrpcClient().queryRaw(getSession)

            assertEquals(2, atmosphere.requestLog.count { it.endsWith("/xrpc/com.atproto.server.getSession") })
        }

    @Test
    fun sessionsSurviveARestartAndWorkFromAnotherInstance() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            val did = harness.signIn().session.did

            val otherInstance = harness.newClientInstance()
            val restored = otherInstance.restore(did)
            val body = restored.xrpcClient().queryRaw(getSession)

            assertTrue(body.contains(atmosphere.alice.did))

            atmosphere.clock.advance(10.minutes)
            val refreshed = otherInstance.restore(did, OAuthRefreshMode.AUTO).tokenInfo(OAuthRefreshMode.NEVER)
            assertEquals(atmosphere.clock.now() + 5.minutes, refreshed.expiresAt)
            assertTrue(
                harness.client
                    .restore(did)
                    .xrpcClient()
                    .queryRaw(getSession)
                    .contains(atmosphere.alice.did),
            )
        }

    @Test
    fun reportsARevokedSessionAndRemovesIt() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            val session = harness.signIn().session
            atmosphere.revokeAllSessions(atmosphere.alice.did)

            val error = assertFailsWith<XrpcTransportException> { session.xrpcClient().queryRaw(getSession) }

            assertIs<OAuthSessionRevokedException>(error.cause)
            assertNull(harness.sessionStore.get(session.did))
            assertFailsWith<OAuthSessionNotFoundException> { harness.client.restore(session.did) }
        }

    @Test
    fun reportsAnExpiredSessionAndRemovesIt() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            val did = harness.signIn().session.did
            atmosphere.expireAllSessions(atmosphere.alice.did)
            atmosphere.clock.advance(6.minutes)

            assertFailsWith<OAuthSessionExpiredException> { harness.client.restore(did) }
            assertNull(harness.sessionStore.get(did))
        }

    @Test
    fun forcedRefreshAfterARejectedTokenRecovers() =
        runTest {
            val harness = OAuthTestHarness()
            val atmosphere = harness.atmosphere
            val session = harness.signIn().session
            val grant = atmosphere.grantsFor(atmosphere.alice.did).single()
            // The data server stops accepting the current access token before its stated expiry.
            grant.accessTokenExpiresAt = atmosphere.clock.now()

            val body = session.xrpcClient().queryRaw(getSession)

            assertTrue(body.contains(atmosphere.alice.did))
            assertEquals(1, grant.usedRefreshTokens.size)
        }

    @Test
    fun signOutRevokesTheRefreshTokenAndRemovesTheSession() =
        runTest {
            val harness = OAuthTestHarness()
            val session = harness.signIn().session
            val refreshToken = harness.sessionStore.get(session.did)!!.refreshToken

            session.signOut()

            assertTrue(refreshToken in harness.atmosphere.revokedTokens)
            assertNull(harness.sessionStore.get(session.did))
            assertFailsWith<OAuthSessionNotFoundException> { harness.client.restore(session.did) }
        }

    @Test
    fun signOutReportsAFailedRevocationButStillRemovesTheSession() =
        runTest {
            val harness = OAuthTestHarness()
            val session = harness.signIn().session
            harness.atmosphere.revocationFails = true

            val error = assertFailsWith<OAuthRevocationException> { harness.client.revoke(session.did) }

            assertIs<OAuthServerResponseException>(error.cause)
            assertNull(harness.sessionStore.get(session.did))
        }

    @Test
    fun sessionsKeepTheirClientKeyAfterRotation() =
        runTest {
            val oldKey = JvmOAuthCrypto.generateClientKey("client-key-1")
            val harness = OAuthTestHarness(keys = listOf(oldKey))
            val did = harness.signIn().session.did
            val newKey = JvmOAuthCrypto.generateClientKey("client-key-2")
            val rotated = harness.newClientInstance(harness.config.copy(keys = listOf(newKey, oldKey)))
            harness.atmosphere.clientMetadataJson = rotated.clientMetadataJson()
            harness.atmosphere.clock.advance(6.minutes)

            rotated.restore(did)

            assertEquals("client-key-1", harness.sessionStore.get(did)!!.clientKeyId)
        }

    @Test
    fun endsSessionsWhoseClientKeyWasRemoved() =
        runTest {
            val harness = OAuthTestHarness()
            val did = harness.signIn().session.did
            val rotated = harness.newClientInstance(harness.config.copy(keys = listOf(JvmOAuthCrypto.generateClientKey("client-key-2"))))
            harness.atmosphere.clock.advance(6.minutes)

            assertFailsWith<OAuthSessionRevokedException> { rotated.restore(did) }
            assertNull(harness.sessionStore.get(did))
        }

    @Test
    fun refusesToSendTheTokenToAnotherServer() =
        runTest {
            val harness = OAuthTestHarness()
            val session = harness.signIn().session

            assertFailsWith<IllegalStateException> {
                session.httpClient.get("https://pds.example.net/xrpc/com.atproto.server.getSession")
            }
        }

    @Test
    fun surfacesDataServerErrorsUnchanged() =
        runTest {
            val harness = OAuthTestHarness()
            val session = harness.signIn().session

            val error =
                assertFailsWith<XrpcProtocolException> {
                    session.xrpcClient().queryRaw(Nsid.require("com.atproto.repo.listRecords"))
                }

            assertEquals(404, error.statusCode)
        }
}
