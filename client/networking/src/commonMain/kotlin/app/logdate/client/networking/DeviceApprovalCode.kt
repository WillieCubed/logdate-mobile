package app.logdate.client.networking

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

@Serializable
class DeviceApprovalCode private constructor(
    val id: String,
    private val accountId: String,
    private val deviceName: String,
    private val publicKey: String,
    private val confirmationCode: String,
) {
    companion object {
        private const val PREFIX = "logdate-device-enrollment:v2:"

        fun parse(value: String): DeviceApprovalCode {
            require(value.length <= 4096 && value.startsWith(PREFIX)) { "Scan a current LogDate device code" }
            val encoded = value.removePrefix(PREFIX)
            val bytes = Base64.UrlSafe.decode(encoded + "=".repeat((4 - encoded.length % 4) % 4))
            require(Base64.UrlSafe.encode(bytes).trimEnd('=') == encoded) { "Invalid LogDate device code" }
            val code = Json.decodeFromString<DeviceApprovalCode>(bytes.decodeToString())
            Uuid.parse(code.id)
            Uuid.parse(code.accountId)
            require(code.confirmationCode.matches(Regex("[0-9]{6}"))) { "Invalid confirmation code" }
            require(code.publicKey.matches(Regex("[A-Za-z0-9_-]{43}"))) { "Invalid recipient key" }
            val key = Base64.UrlSafe.decode(code.publicKey + "=")
            require(key.size == 32 && Base64.UrlSafe.encode(key).trimEnd('=') == code.publicKey) { "Invalid recipient key" }
            return code
        }
    }

    fun verify(
        request: DeviceEnrollmentRequest,
        signedInAccountId: String,
    ) {
        require(
            Uuid.parse(accountId) == Uuid.parse(signedInAccountId) &&
                Uuid.parse(id) == Uuid.parse(request.id) &&
                publicKey == request.publicKey &&
                confirmationCode == request.confirmationCode &&
                deviceName == request.deviceName,
        ) {
            "Device request does not match the scanned code"
        }
    }
}
