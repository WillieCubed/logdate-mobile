package app.logdate.client.permissions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

/** Local-only unlock material is deliberately not serializable or included in toString. */
class PasskeyAuthenticationResult(
    val credentialJson: String,
    val unlockSecret: ByteArray?,
)

internal object PasskeyUnlockInput {
    val first: String = Base64.UrlSafe.encode("studio.hypertext.logdate:account-key-envelope:v1".encodeToByteArray()).trimEnd('=')
}

internal fun parsePasskeyAuthenticationResult(credentialJson: String): PasskeyAuthenticationResult {
    val credential = Json.parseToJsonElement(credentialJson).jsonObject
    val encoded =
        credential["clientExtensionResults"]
            ?.jsonObject
            ?.get("prf")
            ?.jsonObject
            ?.get("results")
            ?.jsonObject
            ?.get("first")
            ?.jsonPrimitive
            ?.content
    val secret =
        encoded?.let {
            require(it.matches(Regex("[A-Za-z0-9_-]{43}"))) { "Invalid local passkey unlock output" }
            Base64.UrlSafe.decode("$it=").also { bytes ->
                require(bytes.size == 32 && Base64.UrlSafe.encode(bytes).trimEnd('=') == it) { "Invalid local passkey unlock output" }
            }
        }
    val assertion = JsonObject(credential.filterKeys { it != "clientExtensionResults" })
    return PasskeyAuthenticationResult(assertion.toString(), secret)
}
