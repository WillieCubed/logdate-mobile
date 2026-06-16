package app.logdate.feature.onboarding.ui

import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.storage.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryPhraseViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `prepareRecoveryPhrase creates and exposes a new stored phrase`() =
        runTest {
            val identityKeyManager = IdentityKeyManager(InMemorySecureStorage(), FakeCryptoManager())
            val viewModel = RecoveryPhraseViewModel(identityKeyManager)

            advanceUntilIdle()

            val state = viewModel.setupState.value
            assertEquals(false, state.isLoading)
            assertEquals((1..12).map { "word$it" }, state.words)
            assertEquals(state.words, identityKeyManager.getStoredRecoveryPhrase()?.words)
            assertTrue(identityKeyManager.hasIdentityKey())
        }

    @Test
    fun `recoverIdentity stores the entered phrase for later settings reference`() =
        runTest {
            val identityKeyManager = IdentityKeyManager(InMemorySecureStorage(), FakeCryptoManager())
            val viewModel = RecoveryPhraseViewModel(identityKeyManager)
            val recoveredPhrase = (1..12).map { "restore$it" }
            advanceUntilIdle()

            val result = viewModel.recoverIdentity(recoveredPhrase)

            assertTrue(result.isSuccess)
            assertEquals(recoveredPhrase, identityKeyManager.getStoredRecoveryPhrase()?.words)
            assertEquals(recoveredPhrase, viewModel.setupState.value.words)
        }

    private class InMemorySecureStorage : SecureStorage {
        private val storage = MutableStateFlow<Map<String, String>>(emptyMap())

        override suspend fun getString(key: String): String? = storage.value[key]

        override suspend fun putString(
            key: String,
            value: String,
        ) {
            storage.value = storage.value + (key to value)
        }

        override suspend fun remove(key: String) {
            storage.value = storage.value - key
        }

        override suspend fun clear() {
            storage.value = emptyMap()
        }

        override fun observeString(key: String): Flow<String?> = storage.map { values -> values[key] }

        override fun observeAll(): Flow<Map<String, String>> = storage

        override suspend fun encrypt(data: ByteArray): ByteArray = data

        override suspend fun decrypt(data: ByteArray): ByteArray? = data
    }

    private class FakeCryptoManager : CryptoManager {
        override suspend fun generateRecoveryPhrase(): List<String> = (1..12).map { "word$it" }

        override suspend fun deriveMasterKey(phrase: List<String>): ByteArray {
            val phraseBytes = phrase.joinToString(" ").encodeToByteArray()
            return ByteArray(32) { index -> phraseBytes[index % phraseBytes.size] }
        }

        override fun validateRecoveryPhrase(phrase: List<String>): Boolean = phrase.size == 12 && phrase.all { it.isNotBlank() }

        override fun encryptSink(
            sink: okio.Sink,
            key: ByteArray,
            iv: ByteArray,
        ): okio.Sink = sink

        override fun decryptSource(
            source: okio.Source,
            key: ByteArray,
            iv: ByteArray,
        ): okio.Source = source

        override fun generateRandomBytes(size: Int): ByteArray = ByteArray(size) { it.toByte() }

        override fun hmacSha256(
            key: ByteArray,
            data: ByteArray,
        ): ByteArray = ByteArray(32)

        override fun aesGcmEncrypt(
            key: ByteArray,
            iv: ByteArray,
            aad: ByteArray,
            plaintext: ByteArray,
        ): ByteArray = plaintext

        override fun aesGcmDecrypt(
            key: ByteArray,
            iv: ByteArray,
            aad: ByteArray,
            ciphertext: ByteArray,
        ): ByteArray = ciphertext
    }
}
