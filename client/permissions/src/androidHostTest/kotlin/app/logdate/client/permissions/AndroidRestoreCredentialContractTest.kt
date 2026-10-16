package app.logdate.client.permissions

import androidx.credentials.exceptions.restorecredential.E2eeUnavailableException
import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.PasskeyRegistrationOptions
import app.logdate.shared.model.PasskeyUser
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AndroidRestoreCredentialContractTest {
    @Test
    fun `restore authentication describes allowed credentials and permits silent verification`() {
        val request =
            Json
                .parseToJsonElement(
                    buildRestoreAuthenticationJson(
                        PasskeyAuthenticationOptions("AQ", "logdate.app", allowCredentials = listOf("Ag")),
                    ),
                ).jsonObject
        val credential =
            request
                .getValue("allowCredentials")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("public-key", credential.getValue("type").jsonPrimitive.content)
        assertEquals("Ag", credential.getValue("id").jsonPrimitive.content)
        assertEquals("discouraged", request.getValue("userVerification").jsonPrimitive.content)
    }

    @Test
    fun `restore registration uses credential descriptors and preserves escaped user names`() {
        val request =
            Json
                .parseToJsonElement(
                    buildRestoreRegistrationJson(
                        PasskeyRegistrationOptions(
                            challenge = "AQ",
                            rpId = "logdate.app",
                            rpName = "LogDate",
                            user = PasskeyUser("Ag", "fixture", "A \"quoted\" name"),
                            excludeCredentials = listOf("Aw"),
                        ),
                    ),
                ).jsonObject
        val credential =
            request
                .getValue("excludeCredentials")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("public-key", credential.getValue("type").jsonPrimitive.content)
        assertEquals("Aw", credential.getValue("id").jsonPrimitive.content)
        assertEquals(
            "A \"quoted\" name",
            request
                .getValue("user")
                .jsonObject
                .getValue("displayName")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `cloud encryption unavailable still creates a credential for direct transfer`() =
        runTest {
            val modes = mutableListOf<Boolean>()
            val result =
                createRestoreCredentialWithFallback { cloudBackup ->
                    modes += cloudBackup
                    if (cloudBackup) throw E2eeUnavailableException("Cloud encryption unavailable")
                    "local-credential"
                }
            assertEquals("local-credential", result)
            assertEquals(listOf(true, false), modes)
        }

    @Test
    fun `other creation failures do not downgrade cloud protection`() =
        runTest {
            val modes = mutableListOf<Boolean>()
            assertFailsWith<IllegalArgumentException> {
                createRestoreCredentialWithFallback<String> { cloudBackup ->
                    modes += cloudBackup
                    throw IllegalArgumentException("Invalid credential request")
                }
            }
            assertEquals(listOf(true), modes)
        }
}
