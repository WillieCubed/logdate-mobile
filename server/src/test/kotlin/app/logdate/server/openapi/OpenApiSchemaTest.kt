package app.logdate.server.openapi

import app.logdate.server.module
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The published schemas must describe the JSON the server really sends and accepts, which is
 * whatever kotlinx.serialization produces: `@SerialName` renames, sealed-class discriminators,
 * value classes flattened to their payload, and contextual types rendered as strings.
 */
class OpenApiSchemaTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun withSchemas(block: (Map<String, JsonObject>) -> Unit) =
        testApplication {
            application { module() }
            val document = json.parseToJsonElement(client.get("/openapi.json").bodyAsText()).jsonObject
            val schemas = assertNotNull(document["components"]?.jsonObject?.get("schemas")?.jsonObject)
            block(schemas.mapValues { it.value.jsonObject })
        }

    private fun Map<String, JsonObject>.named(simpleName: String): JsonObject =
        entries.singleOrNull { (key, _) -> key == simpleName || key.endsWith(".$simpleName") }?.value
            ?: fail("No schema named $simpleName in ${keys.sorted()}")

    private fun JsonObject.properties(): JsonObject = assertNotNull(this["properties"]?.jsonObject, "schema has no properties: $this")

    @Test
    fun `sealed version constraints carry the type discriminator clients must send`() =
        withSchemas { schemas ->
            val known = schemas.named("VersionConstraint.Known").properties()
            assertEquals(
                "known",
                known["type"]
                    ?.jsonObject
                    ?.get("const")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertTrue(known.containsKey("serverVersion"))
            val none = schemas.named("VersionConstraint.None").properties()
            assertEquals(
                "none",
                none["type"]
                    ?.jsonObject
                    ?.get("const")
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `value classes are flattened to their payload type`() =
        withSchemas { schemas ->
            val deviceId = schemas.named("ContentUpdateRequest").properties()["deviceId"]?.jsonObject
            assertEquals("string", assertNotNull(deviceId)["type"]?.jsonPrimitive?.content)
        }

    @Test
    fun `oauth token form fields use the snake_case names the token endpoint reads`() =
        withSchemas { schemas ->
            val form = schemas.named("OAuthTokenForm").properties().keys
            val expected =
                setOf(
                    "grant_type",
                    "code",
                    "redirect_uri",
                    "client_id",
                    "code_verifier",
                    "refresh_token",
                    "client_assertion_type",
                    "client_assertion",
                )
            assertEquals(expected, form)
        }

    @Test
    fun `nullable fields are marked nullable rather than required`() =
        withSchemas { schemas ->
            val update = schemas.named("ContentUpdateRequest")
            val required = update["required"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            assertTrue("lastUpdated" in required, "lastUpdated is mandatory on the wire: $required")
            assertTrue("caption" !in required, "caption defaults to null and must not be required: $required")
        }
}
