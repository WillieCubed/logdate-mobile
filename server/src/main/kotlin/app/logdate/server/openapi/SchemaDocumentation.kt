package app.logdate.server.openapi

import app.logdate.server.openapi.schemadocs.AuthSchemaDocs
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

/** What [applySchemaDocumentation] could not match up, so a test can fail loudly. */
internal data class SchemaDocumentationGaps(
    val undocumentedSchemas: Set<String>,
    val undocumentedProperties: Set<String>,
    val staleEntries: Set<String>,
) {
    val isEmpty: Boolean get() = undocumentedSchemas.isEmpty() && undocumentedProperties.isEmpty() && staleEntries.isEmpty()
}

/**
 * Descriptions for every schema in the published contract, keyed by the readable schema name
 * (`ContentChange`, `VersionConstraint.Known`). The DTOs live in multiplatform modules where the
 * schema generator's annotations are unavailable, so the text is attached here at build time.
 */
internal object SchemaDocumentation {
    val registry: Map<String, SchemaDoc> = AuthSchemaDocs.docs
}

/**
 * Writes [registry] into the schemas of [api] and reports every gap in both directions:
 * published schemas or properties without prose, and prose without a matching schema.
 */
internal fun applySchemaDocumentation(
    api: OpenAPI,
    registry: Map<String, SchemaDoc> = SchemaDocumentation.registry,
): SchemaDocumentationGaps {
    val schemas = api.components?.schemas.orEmpty()
    val undocumentedSchemas = mutableSetOf<String>()
    val undocumentedProperties = mutableSetOf<String>()
    val stale = mutableSetOf<String>()

    registry.keys.filter { it !in schemas }.forEach { stale += it }

    schemas.forEach { (name, schema) ->
        val doc = registry[name]
        if (doc == null) {
            undocumentedSchemas += name
            return@forEach
        }
        schema.description = doc.description.trimIndent()
        val properties = schema.properties.orEmpty()
        doc.properties.keys
            .filter { it !in properties }
            .forEach { stale += "$name.$it" }
        properties.forEach { (property, propertySchema) ->
            val text = doc.properties[property]
            if (text == null) {
                undocumentedProperties += "$name.$property"
            } else {
                propertySchema.description = text.trimIndent()
            }
        }
        val enumValues = schema.enum.orEmpty().map { it.toString() }
        if (enumValues.isNotEmpty()) {
            doc.enumValues.keys
                .filter { it !in enumValues }
                .forEach { stale += "$name.$it" }
            enumValues.filter { it !in doc.enumValues }.forEach { undocumentedProperties += "$name.$it" }
            if (doc.enumValues.isNotEmpty()) schema.addExtension("x-enumDescriptions", doc.enumValues)
        }
    }
    return SchemaDocumentationGaps(undocumentedSchemas, undocumentedProperties, stale)
}
