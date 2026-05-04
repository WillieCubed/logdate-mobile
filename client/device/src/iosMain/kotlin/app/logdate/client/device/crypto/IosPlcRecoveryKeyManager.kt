package app.logdate.client.device.crypto

/**
 * iOS [PlcRecoveryKeyManager] that fails loudly on every call. The deterministic recovery
 * flow needs P-256 ECDSA key derivation + signing, which the iOS app currently delegates to
 * Apple's Security framework via Swift interop — that bridge isn't wired yet. Until it is,
 * iOS users can read their identity status and PLC operation history, but the export /
 * rotate / recovery-import flows need to happen on Android or desktop.
 */
class IosPlcRecoveryKeyManager : PlcRecoveryKeyManager {
    override suspend fun deriveDidKey(recoveryPhrase: List<String>): String =
        throw UnsupportedOperationException(
            "PLC recovery key derivation is not yet wired on iOS. Use the Android or desktop " +
                "build to perform recovery-key flows until the iOS Security-framework bridge ships.",
        )

    override suspend fun signPayload(
        recoveryPhrase: List<String>,
        payload: ByteArray,
    ): String =
        throw UnsupportedOperationException(
            "PLC recovery key signing is not yet wired on iOS. Use the Android or desktop " +
                "build to perform recovery-key flows until the iOS Security-framework bridge ships.",
        )
}
