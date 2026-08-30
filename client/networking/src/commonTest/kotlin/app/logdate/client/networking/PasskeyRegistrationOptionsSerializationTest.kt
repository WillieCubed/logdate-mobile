package app.logdate.client.networking

import app.logdate.shared.model.PasskeyRegistrationOptions
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the shape of the signup begin response against the deployed server.
 *
 * Account creation failed for every user with "Network error. Please check your connection and
 * try again." while the connection was fine: `pubKeyCredParams` was a required field the server
 * does not send, so parsing threw before the passkey ceremony began and the failure was reported
 * as a connectivity problem. A required field the server omits is indistinguishable from an
 * outage from the user's side, so the contract is pinned here.
 */
class PasskeyRegistrationOptionsSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses the registration options the server actually sends`() {
        // Recorded verbatim from cloud.logdate.app POST /api/v1/auth/signup/passkey/begin.
        val payload =
            """
            {
              "challenge": "4-VHj53nGhXC1_NSNCQ6yJbzz5lDd7XpR-zSUDXoLDU",
              "rpId": "logdate.app",
              "rpName": "LogDate",
              "user": {
                "id": "ZjQyNmUzNTgtYWRjOC00YWE2LWE0YzQtZmY2NDNmODQyZDQ1",
                "name": "someone",
                "displayName": "Someone"
              }
            }
            """.trimIndent()

        val options = json.decodeFromString<PasskeyRegistrationOptions>(payload)

        assertEquals("logdate.app", options.rpId)
        assertEquals("someone", options.user.name)
        assertTrue(
            options.pubKeyCredParams.isNotEmpty(),
            "WebAuthn requires a non-empty pubKeyCredParams, so an omitted field must fall back " +
                "to defaults rather than produce an empty list",
        )
    }

    @Test
    fun `keeps the algorithms a server states for itself`() {
        val payload =
            """
            {
              "challenge": "c",
              "rpId": "logdate.app",
              "rpName": "LogDate",
              "user": { "id": "i", "name": "n", "displayName": "d" },
              "pubKeyCredParams": [ { "type": "public-key", "alg": -8 } ]
            }
            """.trimIndent()

        val options = json.decodeFromString<PasskeyRegistrationOptions>(payload)

        assertEquals(1, options.pubKeyCredParams.size)
        assertEquals(-8, options.pubKeyCredParams.first().alg)
    }
}
