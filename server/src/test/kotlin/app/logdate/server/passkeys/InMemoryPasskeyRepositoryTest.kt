package app.logdate.server.passkeys

import app.logdate.shared.model.PasskeyInfo
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class InMemoryPasskeyRepositoryTest {
    @Test
    fun `in-memory passkey repository supports full lifecycle`() =
        runBlocking {
            val repository = InMemoryPasskeyRepository()
            val userId = Uuid.random()
            val info = samplePasskey("cred-1")

            assertTrue(
                repository.storePasskey(
                    userId = userId,
                    credentialId = "cred-1",
                    publicKey = byteArrayOf(1, 2, 3),
                    signCount = 1,
                    info = info,
                ),
            )

            assertTrue(repository.credentialExists("cred-1"))
            assertFalse(repository.credentialExists("missing"))

            val stored = repository.getPasskeyByCredentialId("cred-1")
            assertNotNull(stored)
            assertEquals(userId, stored.first)
            assertTrue(repository.credentialBelongsToUser("cred-1", userId))
            assertFalse(repository.credentialBelongsToUser("cred-1", Uuid.random()))

            val forUser = repository.getPasskeysForUser(userId)
            assertEquals(1, forUser.size)
            assertEquals("cred-1", forUser.first().credentialId)
            assertEquals(listOf("cred-1"), repository.getCredentialIdsForUser(userId))

            assertTrue(repository.updateSignCount("cred-1", 10))
            assertFalse(repository.updateSignCount("missing", 1))
            val updated = repository.getPasskeyByCredentialId("cred-1")
            assertNotNull(updated)
            assertEquals(10L, updated.second.signCount)
            assertNotNull(updated.second.info.lastUsedAt)

            assertFalse(repository.deactivatePasskey("cred-1", Uuid.random()))
            assertTrue(repository.deactivatePasskey("cred-1", userId))
            assertNull(repository.getPasskeyByCredentialId("cred-1"))
            assertTrue(repository.getPasskeysForUser(userId).isEmpty())
            assertTrue(repository.getCredentialIdsForUser(userId).isEmpty())
        }

    @Test
    fun `stored passkey data equality and hash code compare public key bytes`() {
        val info = samplePasskey("cred-eq")
        val first =
            StoredPasskeyData(
                credentialId = "cred-eq",
                publicKey = byteArrayOf(1, 2, 3),
                signCount = 7,
                info = info,
                userId = Uuid.random(),
            )
        val second =
            StoredPasskeyData(
                credentialId = "cred-eq",
                publicKey = byteArrayOf(1, 2, 3),
                signCount = 7,
                info = info,
                userId = first.userId,
            )
        val third = second.copy(publicKey = byteArrayOf(9, 9, 9))

        assertTrue(first == first)
        assertFalse(first.equals("not-a-passkey"))
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertFalse(first == third)
    }

    private fun samplePasskey(credentialId: String): PasskeyInfo =
        PasskeyInfo(
            id = Uuid.random(),
            credentialId = credentialId,
            nickname = "phone",
            deviceType = "platform",
            createdAt = Clock.System.now(),
            lastUsedAt = null,
            isActive = true,
        )
}
