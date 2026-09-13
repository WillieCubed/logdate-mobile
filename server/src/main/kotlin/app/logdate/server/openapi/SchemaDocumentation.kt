package app.logdate.server.openapi

import app.logdate.server.openapi.schemadocs.AtProtoSchemaDocs
import app.logdate.server.openapi.schemadocs.AuthSchemaDocs
import app.logdate.server.openapi.schemadocs.CloudSchemaDocs
import app.logdate.server.openapi.schemadocs.SyncSchemaDocs
import io.swagger.v3.oas.models.OpenAPI

/**
 * Prose for one published schema: what the object is, and one line per property keyed by its
 * wire name. Enum schemas describe each value instead of properties.
 */
internal data class SchemaDoc(
    val description: String,
    val properties: Map<String, String> = emptyMap(),
    val enumValues: Map<String, String> = emptyMap(),
)

/**
 * Descriptions for every schema in the published contract, keyed by the readable schema name
 * (`ContentChange`, `VersionConstraint.Known`). The DTOs live in multiplatform modules where the
 * schema generator's annotations are unavailable, so the text is attached here at build time.
 */
internal object SchemaDocumentation {
    val registry: Map<String, SchemaDoc> = AuthSchemaDocs.docs + SyncSchemaDocs.docs + CloudSchemaDocs.docs + AtProtoSchemaDocs.docs
}

/**
 * Writes [registry] into the schemas of [api]: the schema description, one description per
 * property, and `x-enumDescriptions` for enums. `SchemaDocumentationTest` asserts there are no
 * gaps in either direction.
 */
internal fun applySchemaDocumentation(
    api: OpenAPI,
    registry: Map<String, SchemaDoc> = SchemaDocumentation.registry,
) {
    api.components?.schemas.orEmpty().forEach { (name, schema) ->
        val doc = registry[name] ?: return@forEach
        schema.description = doc.description.trimIndent()
        schema.properties.orEmpty().forEach { (property, propertySchema) ->
            doc.properties[property]?.let { propertySchema.description = it.trimIndent() }
        }
        if (!schema.enum.isNullOrEmpty() && doc.enumValues.isNotEmpty()) {
            schema.addExtension("x-enumDescriptions", doc.enumValues)
        }
    }
}
