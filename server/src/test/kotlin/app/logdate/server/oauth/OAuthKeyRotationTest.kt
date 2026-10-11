package app.logdate.server.oauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * OAuth signing keys rotate in three steps (stage, promote, retire) without invalidating any
 * access token that is still within its lifetime.
 */
class OAuthKeyRotationTest {
    private val clock = MutableClock(Instant.parse("2026-10-10T00:00:00Z"))
    private val storage = InMemoryOAuthSigningKeyRepository()
    private val config = OAuthConfig(issuer = "https://logdate.app", resource = "https://pds.logdate.app")

    @Test
    fun `nothing rotates before the active key reaches its maximum age`() {
        val keys = keyService()
        val original = keys.currentJwk()

        advance(POLICY.maxKeyAge - 1.minutes)

        assertEquals(OAuthKeyRotationStep.NONE, keys.rotateIfDue())
        assertEquals(original, keys.currentJwk())
        assertEquals(listOf(original), keys.jwks().keys)
    }

    @Test
    fun `a staged key is published and accepted but doesn't sign yet`() {
        val keys = keyService()
        val original = keys.currentJwk()
        advance(POLICY.maxKeyAge)

        assertEquals(OAuthKeyRotationStep.STAGED, keys.rotateIfDue())

        val staged = assertNotNull(storage.keySet().next)
        assertEquals(original, keys.currentJwk())
        assertEquals(listOf(original.kid, staged.keyId), keys.jwks().keys.map(JsonWebKey::kid))
        assertNotNull(keys.verificationKey(staged.keyId))
    }

    @Test
    fun `tokens signed before promotion stay valid until they expire, then the old key retires`() {
        val keys = keyService()
        val tokens = OAuthAccessTokenService(config = config, keyService = keys, clock = clock)
        val original = keys.currentJwk()
        advance(POLICY.maxKeyAge)
        keys.rotateIfDue()
        advance(POLICY.stagePeriod)
        val issuedBeforePromotion = tokens.issueAccessToken("did:plc:alice123", "https://viewer.example.com/client.json", "atproto", "jkt")

        assertEquals(OAuthKeyRotationStep.PROMOTED, keys.rotateIfDue())

        assertNotEquals(original, keys.currentJwk())
        assertNotNull(tokens.validateAccessToken(issuedBeforePromotion.token))
        val issuedAfterPromotion = tokens.issueAccessToken("did:plc:alice123", "https://viewer.example.com/client.json", "atproto", "jkt")
        assertNotNull(tokens.validateAccessToken(issuedAfterPromotion.token))

        advance(POLICY.retireAfterPromotion)
        assertEquals(OAuthKeyRotationStep.RETIRED, keys.rotateIfDue())

        assertNull(keys.verificationKey(original.kid))
        assertEquals(listOf(keys.currentJwk()), keys.jwks().keys)
        val issuedAfterRetirement = tokens.issueAccessToken("did:plc:alice123", "https://viewer.example.com/client.json", "atproto", "jkt")
        assertNotNull(tokens.validateAccessToken(issuedAfterRetirement.token))
    }

    @Test
    fun `instances sharing storage promote once and all sign with the new key after refreshing`() {
        val first = keyService()
        val second = keyService()
        assertEquals(first.currentJwk(), second.currentJwk())
        advance(POLICY.maxKeyAge)
        first.rotateIfDue()
        advance(POLICY.stagePeriod)

        assertEquals(OAuthKeyRotationStep.PROMOTED, first.rotateIfDue())
        assertEquals(OAuthKeyRotationStep.NONE, second.rotateIfDue())

        advance(REFRESH_INTERVAL)
        assertEquals(first.currentJwk(), second.currentJwk())
        assertEquals(storage.keySet().active?.keyId, second.currentJwk().kid)
    }

    @Test
    fun `tokens naming an unknown key are rejected`() {
        val tokens = OAuthAccessTokenService(config = config, keyService = keyService(), clock = clock)
        val foreign =
            OAuthAccessTokenService(config = config, keyService = OAuthKeyService(clock = clock), clock = clock)
                .issueAccessToken("did:plc:alice123", "https://viewer.example.com/client.json", "atproto", "jkt")

        assertNull(tokens.validateAccessToken(foreign.token))
    }

    @Test
    fun `the maximum key age comes from the environment and is at least one day`() {
        assertEquals(30.days, OAuthKeyRotationPolicy.fromEnvironment { "30" }.maxKeyAge)
        assertEquals(1.days, OAuthKeyRotationPolicy.fromEnvironment { "0" }.maxKeyAge)
        assertEquals(OAuthKeyRotationPolicy().maxKeyAge, OAuthKeyRotationPolicy.fromEnvironment { null }.maxKeyAge)
    }

    private fun keyService() =
        OAuthKeyService(
            repository = storage,
            encryptionKeySeed = TEST_KEK,
            clock = clock,
            rotationPolicy = POLICY,
            refreshInterval = REFRESH_INTERVAL,
        )

    private fun advance(duration: Duration) {
        clock.nowValue += duration
    }

    private companion object {
        const val TEST_KEK = "oauth-key-rotation-test-key-encryption-key"
        val POLICY = OAuthKeyRotationPolicy(maxKeyAge = 30.days, stagePeriod = 1.hours, retireAfterPromotion = 65.minutes)
        val REFRESH_INTERVAL = 1.minutes
    }
}
