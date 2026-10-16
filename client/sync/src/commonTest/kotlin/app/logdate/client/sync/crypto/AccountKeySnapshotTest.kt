package app.logdate.client.sync.crypto

import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.TestCryptoManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountKeySnapshotTest {
    @Test
    fun `publishing snapshots one root and media generation while recovery waits`() =
        runTest {
            val storage = InMemorySecureStorage()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var pauseNextRootRead = false
            val gated =
                object : SecureStorage by storage {
                    override suspend fun getString(key: String): String? {
                        val value = storage.getString(key)
                        if (key == "identity_key_v1" && pauseNextRootRead) {
                            pauseNextRootRead = false
                            entered.complete(Unit)
                            release.await()
                        }
                        return value
                    }
                }
            val crypto = TestCryptoManager()
            val identity = IdentityKeyManager(gated, crypto)
            identity.setupNewIdentity()
            val provider = MediaPayloadKeyProvider(gated, crypto, identity, KeyDerivation(crypto))
            val originalRoot = identity.getIdentityKey()
            val originalMedia = provider.getOrCreateKey()
            pauseNextRootRead = true
            val publish = async { provider.getAccountKeys("account-a") }
            entered.await()
            val recovery = launch { identity.recoverIdentity(List(12) { "recovered-$it" }) }
            runCurrent()
            assertFalse(recovery.isCompleted)
            release.complete(Unit)
            val snapshot = publish.await()
            recovery.join()
            assertContentEquals(originalRoot, snapshot.key)
            assertContentEquals(originalMedia, snapshot.mediaKey)
        }
}
