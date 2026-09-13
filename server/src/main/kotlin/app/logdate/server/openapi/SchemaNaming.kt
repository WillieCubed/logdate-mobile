package app.logdate.server.openapi

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import java.util.Collections
import java.util.IdentityHashMap

private const val SCHEMA_REF_PREFIX = "#/components/schemas/"

/**
 * Names sealed subtypes appear under when the kotlinx generator falls back to their
 * `@SerialName`. Readers should see them under their parent, not as bare lowercase words.
 */
private val serialNameAliases =
    mapOf(
        "known" to "VersionConstraint.Known",
        "none" to "VersionConstraint.None",
    )

/**
 * Rewrites component schema keys from fully qualified class names
 * (`app.logdate.shared.model.sync.ContentChange`) to the simple names a reader recognises
 * (`ContentChange`), updating every `$ref` in the document to match.
 *
 * A simple name is only used when it is unique across the document; otherwise the qualified
 * key is kept so two different classes can never collapse into one entry.
 */
internal fun useReadableSchemaNames(api: OpenAPI) {
    val schemas = api.components?.schemas ?: return
    val renames = readableNames(schemas.keys)
    if (renames.isEmpty()) return

    val renamed = LinkedHashMap<String, Schema<*>>()
    schemas.forEach { (key, schema) ->
        val newKey = renames[key] ?: key
        if (schema.title.isNullOrBlank()) schema.title = newKey
        renamed[newKey] = schema
    }
    api.components.schemas = renamed

    val visited: MutableSet<Schema<*>> = Collections.newSetFromMap(IdentityHashMap())

    fun rewrite(schema: Schema<*>?) {
        if (schema == null || !visited.add(schema)) return
        schema.`$ref`?.removePrefix(SCHEMA_REF_PREFIX)?.let { target ->
            renames[target]?.let { schema.`$ref` = SCHEMA_REF_PREFIX + it }
        }
        schema.properties?.values?.forEach(::rewrite)
        schema.items?.let(::rewrite)
        schema.anyOf?.forEach(::rewrite)
        schema.oneOf?.forEach(::rewrite)
        schema.allOf?.forEach(::rewrite)
        (schema.additionalProperties as? Schema<*>)?.let(::rewrite)
        schema.not?.let(::rewrite)
        schema.discriminator?.mapping?.let { mapping ->
            mapping.replaceAll { _, ref -> renames[ref.removePrefix(SCHEMA_REF_PREFIX)]?.let { SCHEMA_REF_PREFIX + it } ?: ref }
        }
    }

    renamed.values.forEach(::rewrite)
    api.paths?.values?.forEach { pathItem ->
        pathItem.readOperations().forEach { operation ->
            operation.parameters?.forEach { rewrite(it.schema) }
            operation.requestBody
                ?.content
                ?.values
                ?.forEach { rewrite(it.schema) }
            operation.responses?.values?.forEach { response ->
                response.content?.values?.forEach { rewrite(it.schema) }
                response.headers?.values?.forEach { rewrite(it.schema) }
            }
        }
    }
    api.components.parameters
        ?.values
        ?.forEach { rewrite(it.schema) }
    api.components.headers
        ?.values
        ?.forEach { rewrite(it.schema) }
    api.components.responses
        ?.values
        ?.forEach { response -> response.content?.values?.forEach { rewrite(it.schema) } }
}

private fun readableNames(keys: Collection<String>): Map<String, String> {
    val candidates = keys.associateWith { key -> serialNameAliases[key] ?: simpleName(key) }
    val collisions =
        candidates.values
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
    return candidates.filter { (key, simple) -> simple != key && simple !in collisions }
}

/**
 * `app.logdate.shared.model.sync.VersionConstraint.Known` → `VersionConstraint.Known`. Generic
 * wrappers are keyed `Outer_inner.qualified.Name` by the generator, so each `_`-separated part
 * is shortened on its own: `SimpleSuccessResponse_app…SyncStatusSnapshot` →
 * `SimpleSuccessResponse_SyncStatusSnapshot`.
 */
private fun simpleName(qualified: String): String = qualified.split('_').joinToString("_", transform = ::simpleTypeName)

private fun simpleTypeName(qualified: String): String {
    val segments = qualified.split('.')
    val firstTypeIndex = segments.indexOfFirst { it.firstOrNull()?.isUpperCase() == true }
    if (firstTypeIndex < 0) return qualified
    return segments.drop(firstTypeIndex).joinToString(".")
}
