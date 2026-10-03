package app.logdate.client.permissions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PasskeyUnlockTest {
    @Test
    fun `credential provider PRF output remains local`() {
        val secret = ByteArray(32) { (it + 64).toByte() }
        val encoded = Base64.UrlSafe.encode(secret).trimEnd('=')
        val credential =
            """
            {"id":"AQ","rawId":"AQ","type":"public-key",
             "response":{"clientDataJSON":"e30","authenticatorData":"AQ","signature":"Ag"},
             "clientExtensionResults":{"prf":{"results":{"first":"$encoded"}}}}
            """.trimIndent()
        val result = parsePasskeyAuthenticationResult(credential)
        assertContentEquals(secret, result.unlockSecret)
        assertFalse(result.credentialJson.contains(encoded))
        assertFalse(Json.parseToJsonElement(result.credentialJson).jsonObject.containsKey("clientExtensionResults"))
    }

    @Test
    fun `provider without PRF can still authenticate and malformed PRF is rejected`() {
        assertNull(parsePasskeyAuthenticationResult("""{"id":"AQ","response":{}}""").unlockSecret)
        assertFailsWith<IllegalArgumentException> {
            parsePasskeyAuthenticationResult("""{"id":"AQ","clientExtensionResults":{"prf":{"results":{"first":"AQ"}}}}""")
        }
    }
}
