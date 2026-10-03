package app.logdate.client.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.utils.EmptyContent
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountKeyEnvelopeApiTest {
    @Test
    fun `client stores and fetches only ciphertext for the expected credential`() =
        runTest {
            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("/server/info")) {
                        respond("""{"data":{"protocolFeatures":["encryptedAccountKeysV1"]}}""")
                    } else {
                        assertEquals("/api/v1/account/key-envelopes/AQ", request.url.encodedPath)
                        assertEquals("Bearer access", request.headers["Authorization"])
                        if (request.method.value == "PUT") {
                            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                            assertEquals(setOf("ciphertext"), body.keys)
                            assertEquals("opaque", body.getValue("ciphertext").jsonPrimitive.content)
                            respond("", HttpStatusCode.NoContent)
                        } else {
                            assertTrue(request.body is EmptyContent)
                            respond("""{"ciphertext":"opaque"}""")
                        }
                    }
                }
            val api = DefaultAccountKeyEnvelopeApi(HttpClient(engine))
            val base = "https://cloud.logdate.app/api/v1"
            assertTrue(api.isSupported(base).getOrThrow())
            api.store(base, "access", "AQ", "opaque").getOrThrow()
            assertEquals("opaque", api.fetch(base, "access", "AQ").getOrThrow())
        }

    @Test
    fun `missing envelope differs from unsupported server and network failure`() =
        runTest {
            val base = "https://cloud.logdate.app/api/v1"
            val api =
                DefaultAccountKeyEnvelopeApi(
                    HttpClient(
                        MockEngine { request ->
                            if (request.url.encodedPath.endsWith("/server/info")) {
                                respond("""{"data":{"protocolFeatures":["accountKeyVaultV1"]}}""")
                            } else {
                                respond("", HttpStatusCode.NotFound)
                            }
                        },
                    ),
                )
            assertFalse(api.isSupported(base).getOrThrow())
            assertNull(api.fetch(base, "access", "AQ").getOrThrow())
            val unavailable = DefaultAccountKeyEnvelopeApi(HttpClient(MockEngine { respond("", HttpStatusCode.ServiceUnavailable) }))
            assertTrue(unavailable.fetch(base, "access", "AQ").isFailure)
            assertTrue(unavailable.store(base, "access", "AQ", "opaque").isFailure)
            assertTrue(api.fetch(base, "access", "../keys").isFailure)
        }
}
