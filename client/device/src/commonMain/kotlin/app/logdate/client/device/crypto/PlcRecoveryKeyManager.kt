package app.logdate.client.device.crypto

/**
 * Derives and uses a deterministic PLC recovery signing key from the user's recovery phrase.
 *
 * The server never receives the recovery phrase or the derived private key. Only derived public
 * `did:key` values and client-produced signatures cross the network.
 */
interface PlcRecoveryKeyManager {
    /**
     * Derives the deterministic PLC recovery `did:key` for [recoveryPhrase].
     */
    suspend fun deriveDidKey(recoveryPhrase: List<String>): String

    /**
     * Signs [payload] with the deterministic PLC recovery key derived from [recoveryPhrase].
     *
     * The returned signature uses base64url encoding without padding and carries the raw `r || s`
     * ECDSA bytes expected by the hosted PLC recovery flow.
     */
    suspend fun signPayload(
        recoveryPhrase: List<String>,
        payload: ByteArray,
    ): String
}

/**
 * The P-256 operations behind the recovery key on this platform: BouncyCastle on Android and
 * desktop, CryptoKit on iOS. Every platform produces the same `did:key` for the same key material.
 */
internal expect fun platformPlcRecoveryKeySupport(): PlcRecoveryKeySupport

internal interface PlcRecoveryKeySupport {
    fun isValidPrivateKey(privateKeyMaterial: ByteArray): Boolean

    fun didKey(privateKeyMaterial: ByteArray): String

    fun signPayload(
        privateKeyMaterial: ByteArray,
        payload: ByteArray,
    ): String
}

/**
 * Derives the recovery key from the recovery phrase on its own branch of the phrase's BIP-39 seed.
 *
 * The full 64-byte seed goes through HKDF-SHA256: extract with an all-zero salt, then expand with
 * the label `logdate.atproto.plc.recovery.v1:<attempt>`, trying the next attempt until the output
 * is a valid P-256 scalar. The journal encryption key is the first half of the same seed, but this
 * derivation never starts from it, so nothing that holds the journal key can derive the recovery
 * key. Every intermediate secret is zeroed once it is no longer needed.
 */
internal class DeterministicPlcRecoveryKeyManager(
    private val cryptoManager: CryptoManager,
    private val keySupport: PlcRecoveryKeySupport,
) : PlcRecoveryKeyManager {
    override suspend fun deriveDidKey(recoveryPhrase: List<String>): String = withPrivateKey(recoveryPhrase) { keySupport.didKey(it) }

    override suspend fun signPayload(
        recoveryPhrase: List<String>,
        payload: ByteArray,
    ): String = withPrivateKey(recoveryPhrase) { keySupport.signPayload(it, payload) }

    private inline fun <T> withPrivateKey(
        recoveryPhrase: List<String>,
        use: (ByteArray) -> T,
    ): T {
        val privateKey = derivePrivateKey(recoveryPhrase)
        try {
            return use(privateKey)
        } finally {
            privateKey.fill(0)
        }
    }

    private fun derivePrivateKey(recoveryPhrase: List<String>): ByteArray {
        require(cryptoManager.validateRecoveryPhrase(recoveryPhrase)) { "Invalid recovery phrase" }
        val seed = Bip39.mnemonicToSeed(recoveryPhrase.joinToString(" "))
        val pseudorandomKey =
            try {
                cryptoManager.hmacSha256(key = ByteArray(HASH_LENGTH), data = seed)
            } finally {
                seed.fill(0)
            }
        try {
            repeat(MAX_DERIVATION_ATTEMPTS) { attempt ->
                // One HKDF-Expand block: T(1) = HMAC(PRK, info || 0x01).
                val info = "$RECOVERY_KEY_LABEL:$attempt".encodeToByteArray() + byteArrayOf(1)
                val candidate = cryptoManager.hmacSha256(key = pseudorandomKey, data = info)
                if (keySupport.isValidPrivateKey(candidate)) return candidate
                candidate.fill(0)
            }
        } finally {
            pseudorandomKey.fill(0)
        }
        throw IllegalStateException("Unable to derive a valid PLC recovery key")
    }

    private companion object {
        private const val RECOVERY_KEY_LABEL: String = "logdate.atproto.plc.recovery.v1"
        private const val MAX_DERIVATION_ATTEMPTS: Int = 32
        private const val HASH_LENGTH: Int = 32
    }
}
