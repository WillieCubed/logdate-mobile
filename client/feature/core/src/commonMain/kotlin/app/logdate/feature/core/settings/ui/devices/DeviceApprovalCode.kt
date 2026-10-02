package app.logdate.feature.core.settings.ui.devices

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

/** What a scanned connection QR code asks this phone to do. */
internal sealed interface DeviceApprovalCode {
    /** A signed-in device already created the request; this phone looks it up by [id]. */
    data class ExistingRequest(
        val id: Uuid,
    ) : DeviceApprovalCode

    /** A signed-out device showed its one-time key; this phone creates the request for it. */
    @Serializable
    data class NewDevice(
        val deviceName: String,
        val publicKey: String,
        val claimSecret: String,
        val confirmationCode: String,
    ) : DeviceApprovalCode

    companion object {
        private const val ENROLLMENT_PREFIX = "logdate-device-enrollment:"
        private const val CONNECTION_PREFIX = "logdate-device-connect:"
        private const val MAX_CONNECTION_BYTES = 512
        private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

        /** Returns null for anything that isn't a well-formed LogDate connection code. */
        fun parse(value: String?): DeviceApprovalCode? =
            when {
                value == null -> null
                value.startsWith(ENROLLMENT_PREFIX) -> parseExistingRequest(value.removePrefix(ENROLLMENT_PREFIX))
                value.startsWith(CONNECTION_PREFIX) -> parseNewDevice(value.removePrefix(CONNECTION_PREFIX))
                else -> null
            }

        private fun parseExistingRequest(value: String): ExistingRequest? {
            val id = runCatching { Uuid.parse(value) }.getOrNull() ?: return null
            return ExistingRequest(id).takeIf { id.toString() == value }
        }

        private fun parseNewDevice(value: String): NewDevice? {
            val bytes = runCatching { base64.decode(value) }.getOrNull() ?: return null
            if (bytes.size > MAX_CONNECTION_BYTES) return null
            return runCatching { Json.decodeFromString<NewDevice>(bytes.decodeToString()) }.getOrNull()
        }
    }
}
