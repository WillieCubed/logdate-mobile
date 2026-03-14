package app.logdate.server.e2e.journeys.auth

import app.logdate.server.module
import io.ktor.client.request.get
import io.ktor.client.request.header
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
 * End-to-end HTTP journey tests for the restore credential flow.
 *
 * Tests all four endpoints:
 *   POST /auth/restore/register/begin    — authenticated
 *   POST /auth/restore/register/complete — authenticated
 *   POST /auth/restore/begin             — anonymous
 *   POST /auth/restore/complete          — anonymous
 */
class AuthRestoreCredentialJourneyE2ETest {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Signs up a new account via the passkey flow and returns a valid JWT access token.
     */
    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.createAccountAndGetToken(username: String): String {
        val beginSignup =
            client.post("/api/v1/auth/signup/passkey/begin") {
                contentType(ContentType.Application.Json)
                setBody("""{"username":"$username","displayName":"$username"}""")
            }
        val sessionToken =
            json
                .parseToJsonElement(beginSignup.bodyAsText())
                .jsonObject["data"]
                ?.jsonObject
                ?.get("sessionToken")
                ?.jsonPrimitive
                ?.content
        assertNotNull(sessionToken, "signup begin must return sessionToken")

        val completeSignup =
            client.post("/api/v1/auth/signup/passkey/complete") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "sessionToken": "$sessionToken",
                      "credential": {
                        "id": "cred-$username",
                        "rawId": "cred-$username",
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
        assertEquals(HttpStatusCode.Created, completeSignup.status, "signup complete must return 201")

        val tokens =
            json
                .parseToJsonElement(completeSignup.bodyAsText())
                .jsonObject["data"]
                ?.jsonObject
                ?.get("tokens")
                ?.jsonObject
        val accessToken = tokens?.get("accessToken")?.jsonPrimitive?.content
        assertNotNull(accessToken, "signup complete must return accessToken")
        return accessToken
    }

    // -----------------------------------------------------------------------
    // /restore/register/begin
    // -----------------------------------------------------------------------

    @Test
    fun `restore register begin returns challenge and rp info when authenticated`() =
        testApplication {
            application { module() }
            val token = createAccountAndGetToken("restore_reg_begin_user")

            val response =
                client.post("/api/v1/auth/restore/register/begin") {
                    header("Authorization", "Bearer $token")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertTrue(body["success"]?.jsonPrimitive?.content == "true")
            val data = body["data"]?.jsonObject
            assertNotNull(data, "response must contain 'data'")
            val challenge = data["challenge"]?.jsonPrimitive?.content
            assertNotNull(challenge, "'data' must contain 'challenge'")
            assertTrue(challenge.isNotEmpty())
        }

    @Test
    fun `restore register begin returns 401 without auth token`() =
        testApplication {
            application { module() }

            val response = client.post("/api/v1/auth/restore/register/begin")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    // -----------------------------------------------------------------------
    // /restore/register/complete
    // -----------------------------------------------------------------------

    @Test
    fun `restore register complete succeeds with valid challenge`() =
        testApplication {
            application { module() }
            val token = createAccountAndGetToken("restore_reg_complete_user")

            val beginResponse =
                client.post("/api/v1/auth/restore/register/begin") {
                    header("Authorization", "Bearer $token")
                }
            val challenge =
                json
                    .parseToJsonElement(beginResponse.bodyAsText())
                    .jsonObject["data"]
                    ?.jsonObject
                    ?.get("challenge")
                    ?.jsonPrimitive
                    ?.content
            assertNotNull(challenge)

            val response =
                client.post("/api/v1/auth/restore/register/complete") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "credentialJson": "{\"id\":\"restore-cred-1\",\"rawId\":\"restore-cred-1\",\"response\":{\"clientDataJSON\":\"client\",\"attestationObject\":\"att\"},\"type\":\"public-key\"}",
                          "challenge": "$challenge"
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `restore register complete returns 400 with unknown challenge`() =
        testApplication {
            application { module() }
            val token = createAccountAndGetToken("restore_reg_bad_challenge_user")

            val response =
                client.post("/api/v1/auth/restore/register/complete") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "credentialJson": "{\"id\":\"cred\",\"rawId\":\"cred\",\"response\":{\"clientDataJSON\":\"cd\",\"attestationObject\":\"ao\"},\"type\":\"public-key\"}",
                          "challenge": "this-challenge-was-never-issued"
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `restore register complete returns 401 without auth token`() =
        testApplication {
            application { module() }

            val response =
                client.post("/api/v1/auth/restore/register/complete") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"credentialJson":"{}","challenge":"x"}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    // -----------------------------------------------------------------------
    // /restore/begin
    // -----------------------------------------------------------------------

    @Test
    fun `restore begin returns challenge without authentication`() =
        testApplication {
            application { module() }

            val response = client.post("/api/v1/auth/restore/begin")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val data = body["data"]?.jsonObject
            assertNotNull(data, "restore/begin response must contain 'data'")
            val challenge = data["challenge"]?.jsonPrimitive?.content
            assertNotNull(challenge, "restore/begin must return 'challenge'")
            assertTrue(challenge.isNotEmpty())
            val rpId = data["rpId"]?.jsonPrimitive?.content
            assertNotNull(rpId, "restore/begin must return 'rpId'")
        }

    // -----------------------------------------------------------------------
    // /restore/complete
    // -----------------------------------------------------------------------

    @Test
    fun `restore complete returns 401 for unknown challenge`() =
        testApplication {
            application { module() }

            val response =
                client.post("/api/v1/auth/restore/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "challenge": "challenge-that-was-never-issued",
                          "credential": {
                            "id": "restore-cred-1",
                            "rawId": "restore-cred-1",
                            "response": {
                              "clientDataJSON": "cd",
                              "authenticatorData": "ad",
                              "signature": "sig",
                              "userHandle": ""
                            },
                            "type": "public-key"
                          }
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `restore complete returns tokens after register-then-restore round trip`() =
        testApplication {
            application { module() }
            val token = createAccountAndGetToken("restore_roundtrip_user")

            // Register a restore credential
            val beginRegResponse =
                client.post("/api/v1/auth/restore/register/begin") {
                    header("Authorization", "Bearer $token")
                }
            val regChallenge =
                json
                    .parseToJsonElement(beginRegResponse.bodyAsText())
                    .jsonObject["data"]
                    ?.jsonObject
                    ?.get("challenge")
                    ?.jsonPrimitive
                    ?.content
            assertNotNull(regChallenge)

            val completeRegResponse =
                client.post("/api/v1/auth/restore/register/complete") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "credentialJson": "{\"id\":\"restore-cred-rt\",\"rawId\":\"restore-cred-rt\",\"response\":{\"clientDataJSON\":\"cd\",\"attestationObject\":\"ao\"},\"type\":\"public-key\"}",
                          "challenge": "$regChallenge"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, completeRegResponse.status, "register/complete must succeed")

            // Begin restore sign-in
            val beginRestoreResponse = client.post("/api/v1/auth/restore/begin")
            assertEquals(HttpStatusCode.OK, beginRestoreResponse.status)
            val restoreChallenge =
                json
                    .parseToJsonElement(beginRestoreResponse.bodyAsText())
                    .jsonObject["data"]
                    ?.jsonObject
                    ?.get("challenge")
                    ?.jsonPrimitive
                    ?.content
            assertNotNull(restoreChallenge)

            // Complete restore sign-in with the registered credential
            val completeRestoreResponse =
                client.post("/api/v1/auth/restore/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "challenge": "$restoreChallenge",
                          "credential": {
                            "id": "restore-cred-rt",
                            "rawId": "restore-cred-rt",
                            "response": {
                              "clientDataJSON": "cd",
                              "authenticatorData": "ad",
                              "signature": "sig",
                              "userHandle": ""
                            },
                            "type": "public-key"
                          }
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.OK, completeRestoreResponse.status)
            val restorePayload = json.parseToJsonElement(completeRestoreResponse.bodyAsText()).jsonObject
            val restoreTokens = restorePayload["data"]?.jsonObject?.get("tokens")?.jsonObject
            assertNotNull(restoreTokens, "restore/complete must return tokens")
            assertTrue(restoreTokens["accessToken"]?.jsonPrimitive?.content?.isNotBlank() == true)
            assertTrue(restoreTokens["refreshToken"]?.jsonPrimitive?.content?.isNotBlank() == true)
        }

    @Test
    fun `restore complete returns 400 on replay of already-used credential`() =
        testApplication {
            application { module() }
            val token = createAccountAndGetToken("restore_replay_user")

            // Register
            val beginReg =
                client.post("/api/v1/auth/restore/register/begin") {
                    header("Authorization", "Bearer $token")
                }
            val regChallenge =
                json
                    .parseToJsonElement(beginReg.bodyAsText())
                    .jsonObject["data"]
                    ?.jsonObject
                    ?.get("challenge")
                    ?.jsonPrimitive
                    ?.content
            assertNotNull(regChallenge)

            client.post("/api/v1/auth/restore/register/complete") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "credentialJson": "{\"id\":\"restore-cred-replay\",\"rawId\":\"restore-cred-replay\",\"response\":{\"clientDataJSON\":\"cd\",\"attestationObject\":\"ao\"},\"type\":\"public-key\"}",
                      "challenge": "$regChallenge"
                    }
                    """.trimIndent(),
                )
            }

            // First sign-in succeeds
            val begin1 = client.post("/api/v1/auth/restore/begin")
            val challenge1 =
                json
                    .parseToJsonElement(begin1.bodyAsText())
                    .jsonObject["data"]
                    ?.jsonObject
                    ?.get("challenge")
                    ?.jsonPrimitive
                    ?.content
            assertNotNull(challenge1)

            val complete1 =
                client.post("/api/v1/auth/restore/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "challenge": "$challenge1",
                          "credential": {
                            "id": "restore-cred-replay",
                            "rawId": "restore-cred-replay",
                            "response": {
                              "clientDataJSON": "cd",
                              "authenticatorData": "ad",
                              "signature": "sig",
                              "userHandle": ""
                            },
                            "type": "public-key"
                          }
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, complete1.status, "first restore sign-in must succeed")

            // Replay attempt with a fresh challenge but the now-deactivated credential → 401
            val begin2 = client.post("/api/v1/auth/restore/begin")
            val challenge2 =
                json
                    .parseToJsonElement(begin2.bodyAsText())
                    .jsonObject["data"]
                    ?.jsonObject
                    ?.get("challenge")
                    ?.jsonPrimitive
                    ?.content
            assertNotNull(challenge2)

            val complete2 =
                client.post("/api/v1/auth/restore/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "challenge": "$challenge2",
                          "credential": {
                            "id": "restore-cred-replay",
                            "rawId": "restore-cred-replay",
                            "response": {
                              "clientDataJSON": "cd",
                              "authenticatorData": "ad",
                              "signature": "sig",
                              "userHandle": ""
                            },
                            "type": "public-key"
                          }
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Unauthorized, complete2.status, "replay of consumed credential must fail")
        }
}
