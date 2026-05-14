package app.logdate.feature.core.settings.ui

import app.logdate.shared.model.LogDateAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SettingsUiMappersTest {
    @Test
    fun `toUserProfile carries the verified email through`() {
        val verifiedAt = Instant.fromEpochSeconds(1_775_083_422)
        val account =
            LogDateAccount(
                username = "alice",
                displayName = "Alice",
                passkeyCredentialIds = listOf("cred-1"),
                email = "alice@example.com",
                emailVerified = true,
                emailVerifiedAt = verifiedAt,
            )

        val profile = account.toUserProfile()

        assertEquals("alice@example.com", profile.email)
        assertTrue(profile.emailVerified)
        assertEquals(verifiedAt, profile.emailVerifiedAt)
        // Existing fields stay intact.
        assertEquals("Alice", profile.name)
        assertEquals("alice", profile.username)
        assertTrue(profile.isAuthenticated)
    }

    @Test
    fun `toUserProfile leaves email fields empty when account has no verified email`() {
        val account =
            LogDateAccount(
                username = "bob",
                displayName = "Bob",
            )

        val profile = account.toUserProfile()

        assertNull(profile.email)
        assertFalse(profile.emailVerified)
        assertNull(profile.emailVerifiedAt)
    }
}
