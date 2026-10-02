package app.logdate.server.accountkeys

import app.logdate.server.crypto.EncryptionKey
import app.logdate.server.crypto.EncryptionKeyring
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class AccountKeyVaultTest {
    private val keyring =
        object : EncryptionKeyring {
            private val key = EncryptionKey("test-key", ByteArray(32) { it.toByte() })

            override fun getActiveKey(): EncryptionKey = key

            override fun getKey(keyId: String): EncryptionKey? = key.takeIf { keyId == it.keyId }
        }

    @Test
    fun `account key is encrypted at rest and retrievable only under its account`() =
        runTest {
            val repository = InMemoryAccountKeyRepository()
            val vault = AccountKeyVault(repository, keyring)
            val account = UUID.randomUUID()
            val otherAccount = UUID.randomUUID()
            val material = AccountKeyMaterial(ByteArray(32) { 0x31 }, ByteArray(32) { 0x44 })

            assertEquals(AccountKeySaveResult.CREATED, vault.save(account, material))
            val stored = assertNotNull(repository.get(account))
            assertFalse(stored.containsSequence(material.identityKey))
            assertFalse(stored.containsSequence(material.mediaKey))
            assertContentEquals(material.identityKey, vault.get(account)?.identityKey)
            assertContentEquals(material.mediaKey, vault.get(account)?.mediaKey)
            assertEquals(null, vault.get(otherAccount))

            repository.insertIfAbsent(otherAccount, stored)
            assertFailsWith<Exception> { vault.get(otherAccount) }
        }

    @Test
    fun `same account key can be retried but a different key cannot replace it`() =
        runTest {
            val vault = AccountKeyVault(InMemoryAccountKeyRepository(), keyring)
            val account = UUID.randomUUID()
            val material = AccountKeyMaterial(ByteArray(32) { 0x31 }, ByteArray(32) { 0x44 })

            assertEquals(AccountKeySaveResult.CREATED, vault.save(account, material))
            assertEquals(AccountKeySaveResult.ALREADY_PRESENT, vault.save(account, material))
            assertEquals(
                AccountKeySaveResult.CONFLICT,
                vault.save(account, AccountKeyMaterial(ByteArray(32) { 0x32 }, material.mediaKey)),
            )
            assertContentEquals(material.identityKey, vault.get(account)?.identityKey)
        }

    private fun ByteArray.containsSequence(candidate: ByteArray): Boolean =
        asList().windowed(candidate.size).any { it.toByteArray().contentEquals(candidate) }
}
