package app.logdate.server.openapi

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Discriminator
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema

/**
 * A sealed class kotlinx.serialization encodes with a class discriminator. The schema generator
 * emits the subtypes but not the discriminator property clients must actually send, so the
 * post-build pass adds it here from the same `@SerialName` values the wire uses.
 */
internal data class SealedSchema(
    val parent: String,
    val discriminator: String,
    val subtypesBySerialName: Map<String, String>,
)

internal val publishedSealedSchemas =
    listOf(
        SealedSchema(
            parent = "VersionConstraint",
            discriminator = "type",
            subtypesBySerialName = mapOf("known" to "VersionConstraint.Known", "none" to "VersionConstraint.None"),
        ),
    )

/** Adds the `type` discriminator to every subtype and a discriminator mapping to the parent. */
internal fun describeSealedDiscriminators(
    api: OpenAPI,
    sealed: List<SealedSchema> = publishedSealedSchemas,
) {
    val schemas = api.components?.schemas ?: return
    sealed.forEach { hierarchy ->
        val parent = schemas[hierarchy.parent] ?: return@forEach
        parent.discriminator =
            Discriminator().propertyName(hierarchy.discriminator).mapping(
                hierarchy.subtypesBySerialName.mapValues { (_, schemaName) -> "#/components/schemas/$schemaName" },
            )
        hierarchy.subtypesBySerialName.forEach { (serialName, schemaName) ->
            val subtype = schemas[schemaName] ?: return@forEach
            val discriminatorProperty = StringSchema().also { it.setConst(serialName) }
            subtype.properties =
                linkedMapOf<String, Schema<*>>(hierarchy.discriminator to discriminatorProperty).also { merged ->
                    subtype.properties?.let(merged::putAll)
                }
            subtype.required = listOf(hierarchy.discriminator) + subtype.required.orEmpty().filter { it != hierarchy.discriminator }
        }
    }
}
