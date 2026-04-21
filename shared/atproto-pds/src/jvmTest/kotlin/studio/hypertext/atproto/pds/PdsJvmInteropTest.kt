package studio.hypertext.atproto.pds

import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies the binary compatibility and JVM-level interoperability of ATProto PDS models.
 *
 * This test suite ensures that Kotlin properties in PDS data classes are correctly
 * exposed via standard JVM getter conventions. This is critical for consumers
 * interacting with these models from Java or other JVM languages that rely on
 * stable method signatures for property access, especially when dealing with
 * property renaming or aliasing.
 */
class PdsJvmInteropTest {
    @Test
    fun `jvm consumers can read aliased oauth response properties`() {
        val promptResponse =
            AuthorizationPromptResponse(
                clientId = "https://viewer.example.com/client.json",
                clientName = "Viewer",
                redirectUri = "https://viewer.example.com/callback",
                scope = "atproto",
                state = "state-123",
                loginHint = "alice.logdate.app",
                did = "did:plc:alice123",
                handle = "alice.logdate.app",
            )
        val pushedAuthorizationBody =
            PushedAuthorizationBody(
                requestUri = "urn:ietf:params:oauth:request_uri:test",
                expiresInSeconds = 300L,
            )

        assertEquals("alice.logdate.app", invokeJvmGetter(promptResponse, "getLoginHint"))
        assertEquals("Viewer", invokeJvmGetter(promptResponse, "getClientName"))
        assertEquals("https://viewer.example.com/callback", invokeJvmGetter(promptResponse, "getRedirectUri"))
        assertEquals("https://viewer.example.com/client.json", invokeJvmGetter(promptResponse, "getClientId"))
        assertEquals("urn:ietf:params:oauth:request_uri:test", invokeJvmGetter(pushedAuthorizationBody, "getRequestUri"))
    }

    @Test
    fun `jvm consumers can read record keys from delete requests`() {
        val deleteRecordRequest =
            DeleteRecordRequest(
                repo = AtprotoDid.require("did:web:alice.logdate.app"),
                collection = Nsid.require("com.atproto.repo.createRecord"),
                recordKey = RecordKey.require("entry-1"),
            )

        assertEquals("entry-1", invokeJvmGetter(deleteRecordRequest, "getRecordKey-"))
    }

    private fun invokeJvmGetter(
        target: Any,
        methodNamePrefix: String,
    ): Any? =
        target.javaClass
            .declaredMethods
            .single { method ->
                method.parameterCount == 0 &&
                    !method.name.endsWith("\$annotations") &&
                    method.name.startsWith(methodNamePrefix)
            }.apply { isAccessible = true }
            .invoke(target)
}
