package app.logdate.client.device.crypto

import app.logdate.client.device.storage.SecureStorage
import kotlin.io.encoding.Base64

/** An existing-only observation; caller owns the journal-generation and transport admission checks. */
class ExistingIdentityKeySnapshot internal constructor(
    val accountId: String?,
    identityKey: ByteArray,
    mediaKey: ByteArray,
    val mediaKeySource: ExistingMediaKeySource,
) {
    val identityKey: ByteArray = identityKey.copyOf()
    val mediaKey: ByteArray = mediaKey.copyOf()

    override fun toString(): String = "ExistingIdentityKeySnapshot([REDACTED])"
}

enum class ExistingMediaKeySource { CACHED, RETAINED, DERIVED_EXISTING_ROOT }

internal suspend fun SecureStorage.readExistingTransferKey(name: String): ByteArray? {
    val raw = getString(name) ?: return null
    val bytes =
        try {
            Base64.decode(raw)
        } catch (_: IllegalArgumentException) {
            throw IllegalStateException("Existing encryption material could not be read")
        }
    check(bytes.size == 32) { "Existing encryption material has an invalid length" }
    return bytes
}

/** Root absence and cached material only; readable-original and journal-generation proof remain separate. */
class RootlessIdentityKeyObservation internal constructor(
    mediaKey: ByteArray?,
    val hasRetainedMedia: Boolean,
) {
    val mediaKey: ByteArray? = mediaKey?.copyOf()

    override fun toString(): String = "RootlessIdentityKeyObservation([REDACTED])"
}
