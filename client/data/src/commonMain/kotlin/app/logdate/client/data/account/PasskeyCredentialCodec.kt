package app.logdate.client.data.account

import app.logdate.shared.model.PasskeyAssertionResponse
import app.logdate.shared.model.PasskeyCredentialResponse
import kotlinx.serialization.json.Json

/**
 * Decodes the WebAuthn credential/assertion responses platform passkey and restore-credential
 * managers hand back as JSON. The exact JSON shape depends on the platform implementation.
 */
internal class PasskeyCredentialCodec(
    private val json: Json,
) {
    fun parseCredentialResponse(credentialJson: String): PasskeyCredentialResponse =
        json.decodeFromString(PasskeyCredentialResponse.serializer(), credentialJson)

    fun parseAssertionResponse(assertionJson: String): PasskeyAssertionResponse =
        json.decodeFromString(PasskeyAssertionResponse.serializer(), assertionJson)
}
