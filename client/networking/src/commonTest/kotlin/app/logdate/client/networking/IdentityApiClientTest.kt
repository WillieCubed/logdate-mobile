package app.logdate.client.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdentityApiClientTest {
    private fun createClient(mockEngine: MockEngine): IdentityApiClient {
        val httpClient =
            HttpClient(mockEngine) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            encodeDefaults = false
                        },
                    )
                }
            }
        return IdentityApiClient(httpClient, TestConfigRepository())
    }

    @Test
    fun `getIdentityStatus uses identity path with bearer auth`() =
        runTest {
            val client =
                createClient(
                    MockEngine { request ->
                        assertEquals("/api/v1/identity", request.url.encodedPath)
                        assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
                        respond(
                            content =
                                """
                                {
                                  "success": true,
                                  "data": {
                                    "did": "did:plc:alice123",
                                    "handle": "alice.logdate.app",
                                    "signingKeyPublicMultibase": "zPublic",
                                    "signingKeyDidKey": "did:key:zPublic",
                                    "plcRecoveryDidKey": "did:key:zRecovery",
                                    "plcOperationCount": 2
                                  }
                                }
                                """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                )

            val result = client.getIdentityStatus("token-123")

            assertTrue(result.isSuccess)
            assertEquals("did:plc:alice123", result.getOrThrow().did)
            assertEquals(2, result.getOrThrow().plcOperationCount)
        }

    @Test
    fun `exportSigningKey uses export path`() =
        runTest {
            val client =
                createClient(
                    MockEngine { request ->
                        assertEquals("/api/v1/identity/signing-key/export", request.url.encodedPath)
                        respond(
                            content =
                                """
                                {
                                  "success": true,
                                  "data": {
                                    "did": "did:plc:alice123",
                                    "handle": "alice.logdate.app",
                                    "exportedKey": {
                                      "algorithm": "P-256",
                                      "publicKeyMultibase": "zPublic",
                                      "publicKeyDidKey": "did:key:zPublic",
                                      "encryptedPrivateKey": "ciphertext",
                                      "salt": "salt",
                                      "iv": "iv",
                                      "kdf": "PBKDF2WithHmacSHA256",
                                      "iterations": 120000
                                    }
                                  }
                                }
                                """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                )

            val result = client.exportSigningKey("token-123", "secret")

            assertTrue(result.isSuccess)
            assertEquals("did:key:zPublic", result.getOrThrow().exportedKey.publicKeyDidKey)
        }

    @Test
    fun `getHostedPlcOperations uses plc operations path`() =
        runTest {
            val client =
                createClient(
                    MockEngine { request ->
                        assertEquals("/api/v1/identity/plc/operations", request.url.encodedPath)
                        respond(
                            content =
                                """
                                {
                                  "success": true,
                                  "data": [
                                    {
                                      "did": "did:plc:alice123",
                                      "cid": "cid-1",
                                      "prevCid": null,
                                      "operationType": "plc_operation",
                                      "operationJson": "{}",
                                      "createdAt": "2026-03-10T00:00:00Z"
                                    }
                                  ]
                                }
                                """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                )

            val result = client.getHostedPlcOperations("token-123")

            assertTrue(result.isSuccess)
            assertEquals("cid-1", result.getOrThrow().single().cid)
        }

    @Test
    fun `prepareRecoverySigningKeyImport uses recovery prepare path`() =
        runTest {
            val client =
                createClient(
                    MockEngine { request ->
                        assertEquals("/api/v1/identity/signing-key/import/recovery/prepare", request.url.encodedPath)
                        respond(
                            content =
                                """
                                {
                                  "success": true,
                                  "data": {
                                    "did": "did:plc:alice123",
                                    "handle": "alice.logdate.app",
                                    "recoveryDidKey": "did:key:zRecovery",
                                    "nextPublicKeyDidKey": "did:key:zNext",
                                    "unsignedOperationJson": "{}",
                                    "signingPayloadBase64Url": "cGF5bG9hZA"
                                  }
                                }
                                """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                )

            val result =
                client.prepareRecoverySigningKeyImport(
                    accessToken = "tok-1",
                    passphrase = "secret",
                    exportedKey =
                        ExportedSigningKeyDto(
                            algorithm = "P-256",
                            publicKeyMultibase = "zPublic",
                            publicKeyDidKey = "did:key:zPublic",
                            encryptedPrivateKey = "ciphertext",
                            salt = "salt",
                            iv = "iv",
                        ),
                )

            assertTrue(result.isSuccess)
            assertEquals("did:key:zRecovery", result.getOrThrow().recoveryDidKey)
        }

    @Test
    fun `completeRecoverySigningKeyImport uses recovery complete path`() =
        runTest {
            val client =
                createClient(
                    MockEngine { request ->
                        assertEquals("/api/v1/identity/signing-key/import/recovery/complete", request.url.encodedPath)
                        respond(
                            content =
                                """
                                {
                                  "success": true,
                                  "data": {
                                    "did": "did:plc:alice123",
                                    "handle": "alice.logdate.app",
                                    "publicKeyDidKey": "did:key:zImported"
                                  }
                                }
                                """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                )

            val result =
                client.completeRecoverySigningKeyImport(
                    accessToken = "tok-1",
                    passphrase = "secret",
                    exportedKey =
                        ExportedSigningKeyDto(
                            algorithm = "P-256",
                            publicKeyMultibase = "zPublic",
                            publicKeyDidKey = "did:key:zPublic",
                            encryptedPrivateKey = "ciphertext",
                            salt = "salt",
                            iv = "iv",
                        ),
                    signature = "sig-1",
                )

            assertTrue(result.isSuccess)
            assertEquals("did:key:zImported", result.getOrThrow().publicKeyDidKey)
        }
}
