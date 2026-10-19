package app.logdate.client.device.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The same 12 words must give the same recovery key on Android, desktop and iOS, or a key turned on
 * from one device would not match the phrase typed into another. This runs the real platform
 * [CryptoManager] and P-256 support on each target, from the phrase all the way to the `did:key`.
 *
 * The vector was computed independently of this code: PBKDF2-HMAC-SHA512 for the BIP-39 seed (it
 * matches the published BIP-39 test vector for this phrase), HKDF-SHA256 over the whole seed with an
 * all-zero salt and the info `logdate.atproto.plc.recovery.v1:0`, and P-256 point multiplication.
 */
class PlcRecoveryKeyDerivationTest {
    private val manager = DeterministicPlcRecoveryKeyManager(testPlatformCryptoManager(), platformPlcRecoveryKeySupport())

    @Test
    fun `the phrase seed is the published bip39 vector`() {
        assertEquals(EXPECTED_SEED_HEX, Bip39.mnemonicToSeed(PHRASE.joinToString(" ")).toHex())
    }

    @Test
    fun `a fixed phrase derives the pinned recovery did key`() =
        runTest {
            assertEquals(EXPECTED_DID_KEY, manager.deriveDidKey(PHRASE))
        }

    @Test
    fun `the recovery key does not come from the journal key`() =
        runTest {
            val journalKey = testPlatformCryptoManager().deriveMasterKey(PHRASE)
            val fromJournalKey =
                platformPlcRecoveryKeySupport().didKey(
                    testPlatformCryptoManager().hmacSha256(journalKey, "logdate.atproto.plc.recovery.v1:0".encodeToByteArray()),
                )

            assertNotEquals(fromJournalKey, manager.deriveDidKey(PHRASE))
        }

    @Test
    fun `the private key is zeroed after use`() =
        runTest {
            val recording = RecordingKeySupport(platformPlcRecoveryKeySupport())
            val recordingManager = DeterministicPlcRecoveryKeyManager(testPlatformCryptoManager(), recording)

            val didKey = recordingManager.deriveDidKey(PHRASE)
            recordingManager.signPayload(PHRASE, "payload".encodeToByteArray())

            assertEquals(EXPECTED_DID_KEY, didKey)
            assertEquals(2, recording.used.size)
            recording.used.forEach { key -> assertContentEquals(ByteArray(32), key) }
        }

    /** Keeps a reference to every private key it is handed, to check it is wiped afterwards. */
    private class RecordingKeySupport(
        private val delegate: PlcRecoveryKeySupport,
    ) : PlcRecoveryKeySupport {
        val used = mutableListOf<ByteArray>()

        override fun isValidPrivateKey(privateKeyMaterial: ByteArray): Boolean = delegate.isValidPrivateKey(privateKeyMaterial)

        override fun didKey(privateKeyMaterial: ByteArray): String {
            used += privateKeyMaterial
            return delegate.didKey(privateKeyMaterial)
        }

        override fun signPayload(
            privateKeyMaterial: ByteArray,
            payload: ByteArray,
        ): String {
            used += privateKeyMaterial
            return delegate.signPayload(privateKeyMaterial, payload)
        }
    }

    private companion object {
        val PHRASE: List<String> = List(11) { "abandon" } + "about"
        const val EXPECTED_SEED_HEX: String =
            "5eb00bbddcf069084889a8ab9155568165f5c453ccb85e70811aaed6f6da5fc1" +
                "9a5ac40b389cd370d086206dec8aa6c43daea6690f20ad3d8d48b2d2ce9e38e4"
        const val EXPECTED_DID_KEY: String = "did:key:zDnaeuWU6bh5EPQhMEV3S1ezZuDmbVf9Aorwv5UVxrFytjX79"

        fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}

/** The real [CryptoManager] of the platform the tests run on. */
internal expect fun testPlatformCryptoManager(): CryptoManager
