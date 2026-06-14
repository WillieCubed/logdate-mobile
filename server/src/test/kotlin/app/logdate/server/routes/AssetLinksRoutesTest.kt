package app.logdate.server.routes

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the Android Digital Asset Links endpoint that authorizes the LogDate app to use passkeys
 * for hosts where this server is itself the WebAuthn relying party (staging and self-hosted deploys).
 */
class AssetLinksRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serves assetlinks json with login-creds relation, package, and fingerprints`() =
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    assetLinksRoutes(
                        AssetLinksConfig(
                            packageName = "co.reasonabletech.logdate",
                            sha256CertFingerprints = listOf("AB:CD:EF"),
                        ),
                    )
                }
            }

            val response = client.get("/.well-known/assetlinks.json")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.contentType()?.match(ContentType.Application.Json) == true)

            val statement =
                json
                    .parseToJsonElement(response.bodyAsText())
                    .jsonArray
                    .first()
                    .jsonObject
            val relations = statement["relation"]!!.jsonArray.map { it.jsonPrimitive.content }
            assertTrue(relations.contains("delegate_permission/common.get_login_creds"))

            val target = statement["target"]!!.jsonObject
            assertEquals("android_app", target["namespace"]!!.jsonPrimitive.content)
            assertEquals("co.reasonabletech.logdate", target["package_name"]!!.jsonPrimitive.content)
            assertEquals(
                listOf("AB:CD:EF"),
                target["sha256_cert_fingerprints"]!!.jsonArray.map { it.jsonPrimitive.content },
            )
        }

    @Test
    fun `fromEnvironment falls back to the default package and empty fingerprints`() {
        val config = AssetLinksConfig.fromEnvironment(packageName = null, certFingerprints = null)

        assertEquals(AssetLinksConfig.DEFAULT_PACKAGE_NAME, config.packageName)
        assertTrue(config.sha256CertFingerprints.isEmpty())
    }

    @Test
    fun `fromEnvironment parses a comma-separated fingerprint list and drops blanks`() {
        val config =
            AssetLinksConfig.fromEnvironment(
                packageName = "co.reasonabletech.logdate",
                certFingerprints = "AB:CD , , EF:01",
            )

        assertEquals(listOf("AB:CD", "EF:01"), config.sha256CertFingerprints)
    }
}
