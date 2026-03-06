package app.logdate.server.e2e.auth

import app.logdate.server.module
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end coverage for auth v1 endpoints.
 */
class AuthV1E2ETest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `username availability endpoint works`() =
        testApplication {
            application { module() }

            val ok = client.get("/api/v1/auth/signup/username/testuser/available")
            assertEquals(HttpStatusCode.OK, ok.status)

            val bad = client.get("/api/v1/auth/signup/username/ab/available")
            assertEquals(HttpStatusCode.BadRequest, bad.status)
        }

    @Test
    fun `passkey signup and signin flow returns auth tokens`() =
        testApplication {
            application { module() }

            val beginSignup =
                client.post("/api/v1/auth/signup/passkey/begin") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "username": "authv1_user",
                          "displayName": "Auth V1 User"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, beginSignup.status)
            val beginSignupPayload = json.parseToJsonElement(beginSignup.bodyAsText()).jsonObject
            val sessionToken =
                beginSignupPayload["data"]
                    ?.jsonObject
                    ?.get("sessionToken")
                    ?.jsonPrimitive
                    ?.content
            assertNotNull(sessionToken)

            val completeSignup =
                client.post("/api/v1/auth/signup/passkey/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "sessionToken": "$sessionToken",
                          "credential": {
                            "id": "cred-authv1-1",
                            "rawId": "cred-authv1-1",
                            "response": {
                              "clientDataJSON": "client",
                              "attestationObject": "attestation"
                            },
                            "type": "public-key"
                          }
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Created, completeSignup.status)

            val beginSignin =
                client.post("/api/v1/auth/signin/passkey/begin") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"authv1_user"}""")
                }
            assertEquals(HttpStatusCode.OK, beginSignin.status)
            val beginSigninPayload = json.parseToJsonElement(beginSignin.bodyAsText()).jsonObject
            val challenge =
                beginSigninPayload["data"]
                    ?.jsonObject
                    ?.get("challenge")
                    ?.jsonPrimitive
                    ?.content
            assertNotNull(challenge)

            val completeSignin =
                client.post("/api/v1/auth/signin/passkey/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "challenge": "$challenge",
                          "credential": {
                            "id": "cred-authv1-1",
                            "rawId": "cred-authv1-1",
                            "response": {
                              "clientDataJSON": "client",
                              "authenticatorData": "auth",
                              "signature": "sig",
                              "userHandle": "authv1_user"
                            },
                            "type": "public-key"
                          }
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, completeSignin.status)
            val signinPayload = json.parseToJsonElement(completeSignin.bodyAsText()).jsonObject
            val tokens = signinPayload["data"]?.jsonObject?.get("tokens")?.jsonObject
            assertNotNull(tokens)
            assertTrue(tokens["accessToken"]?.jsonPrimitive?.content?.isNotBlank() == true)
            assertTrue(tokens["refreshToken"]?.jsonPrimitive?.content?.isNotBlank() == true)
        }

    @Test
    fun `google signin fails with invalid token`() =
        testApplication {
            application { module() }

            val response =
                client.post("/api/v1/auth/signin/google") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"idToken":"not-a-real-token"}""")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
}
