package app.logdate.feature.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthenticationChallengeTest {
    @Test
    fun `success before resume remains pending until consumed once`() {
        val challenge = AuthenticationChallenge()
        val id = challenge.begin()
        assertTrue(challenge.recordSuccess(id))
        assertTrue(challenge.consumeSuccess())
        assertFalse(challenge.consumeSuccess())
        assertFalse(challenge.recordSuccess(id))
    }

    @Test
    fun `new challenge rejects old callbacks and pending success`() {
        val challenge = AuthenticationChallenge()
        val old = challenge.begin()
        challenge.recordSuccess(old)
        val current = challenge.begin()
        assertFalse(challenge.isCurrent(old))
        assertFalse(challenge.recordSuccess(old))
        assertFalse(challenge.consumeSuccess())
        assertTrue(challenge.recordSuccess(current))
        assertTrue(challenge.consumeSuccess())
    }

    @Test
    fun `replacing or destroying the host invalidates its delayed result`() {
        val challenge = AuthenticationChallenge()
        val old = challenge.begin()
        challenge.recordSuccess(old)
        challenge.invalidate()
        assertFalse(challenge.isCurrent(old))
        assertFalse(challenge.consumeSuccess())
        assertFalse(challenge.recordSuccess(old))
        assertTrue(challenge.recordSuccess(challenge.begin()))
    }

    @Test
    fun `backgrounded biometric result is rejected while current device credential can await resume`() {
        val challenge = AuthenticationChallenge()
        val abandoned = challenge.begin()
        assertFalse(challenge.recordSuccess(abandoned, hostResumed = false, deviceCredential = false))
        assertFalse(challenge.consumeSuccess())
        val credential = challenge.begin()
        assertTrue(challenge.recordSuccess(credential, hostResumed = false, deviceCredential = true))
        assertTrue(challenge.consumeSuccess())
    }

    @Test
    fun `biometric completion queued until after resume cannot reuse a paused challenge`() {
        val challenge = AuthenticationChallenge()
        val old = challenge.begin()
        challenge.markHostPaused()
        assertFalse(challenge.recordSuccess(old, hostResumed = true, deviceCredential = false))
        assertFalse(challenge.consumeSuccess())
        val fresh = challenge.begin()
        assertTrue(challenge.recordSuccess(fresh, hostResumed = true, deviceCredential = false))
        assertTrue(challenge.consumeSuccess())
    }

    @Test
    fun `an available authenticator alone never proves authentication`() {
        val challenge = AuthenticationChallenge()
        challenge.begin()
        assertFalse(challenge.consumeSuccess())
    }
}
