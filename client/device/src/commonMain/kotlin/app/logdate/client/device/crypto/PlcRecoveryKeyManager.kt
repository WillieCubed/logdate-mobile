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

internal interface PlcRecoveryKeySupport {
    fun isValidPrivateKey(privateKeyMaterial: ByteArray): Boolean

    fun didKey(privateKeyMaterial: ByteArray): String

    fun signPayload(
        privateKeyMaterial: ByteArray,
        payload: ByteArray,
    ): String
}

internal class DeterministicPlcRecoveryKeyManager(
    private val cryptoManager: CryptoManager,
    private val keySupport: PlcRecoveryKeySupport,
) : PlcRecoveryKeyManager {
    override suspend fun deriveDidKey(recoveryPhrase: List<String>): String = keySupport.didKey(derivePrivateKey(recoveryPhrase))

    override suspend fun signPayload(
        recoveryPhrase: List<String>,
        payload: ByteArray,
    ): String = keySupport.signPayload(derivePrivateKey(recoveryPhrase), payload)

    private suspend fun derivePrivateKey(recoveryPhrase: List<String>): ByteArray {
        require(cryptoManager.validateRecoveryPhrase(recoveryPhrase)) { "Invalid recovery phrase" }
        val masterKey = cryptoManager.deriveMasterKey(recoveryPhrase)
        repeat(MAX_DERIVATION_ATTEMPTS) { attempt ->
            val candidate =
                cryptoManager.hmacSha256(
                    key = masterKey,
                    data = "$RECOVERY_KEY_CONTEXT:$attempt".encodeToByteArray(),
                )
            if (keySupport.isValidPrivateKey(candidate)) {
                return candidate
            }
        }
        throw IllegalStateException("Unable to derive a valid PLC recovery key")
    }

    private companion object {
        private const val RECOVERY_KEY_CONTEXT: String = "logdate.atproto.plc.recovery.v1"
        private const val MAX_DERIVATION_ATTEMPTS: Int = 32
    }
}
