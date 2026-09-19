package app.logdate.client.domain.export.archive

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The JSON Schemas (draft 2020-12) that describe the files in a 2.0 archive.
 *
 * A copy of each is written into the archive under `schema/`, so a program can check a file without
 * network access or the app. They are deliberately permissive about extra keys, because a minor
 * version only adds fields; the tests validate against a stricter copy to catch fields a schema
 * forgot.
 */
object ArchiveSchemas {
    private const val DIALECT = "https://json-schema.org/draft/2020-12/schema"
    private const val UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    private const val INSTANT_PATTERN = "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,9})?Z$"
    private const val SHA256_PATTERN = "^[0-9a-f]{64}$"
    private const val PATH_PATTERN = "^(?!/)(?!.*(^|/)\\.\\.?(/|$))[^\\\\:*?\"<>|\\u0000-\\u001f]+$"

    /** Schema text by the archive path it is written to. */
    val files: Map<ArchivePath, String> by lazy {
        mapOf(
            ArchiveLayout.SCHEMA_MANIFEST to render(manifest()),
            ArchiveLayout.SCHEMA_JOURNALS to render(journals()),
            ArchiveLayout.SCHEMA_NOTES to render(notes()),
            ArchiveLayout.SCHEMA_DRAFTS to render(drafts()),
            ArchiveLayout.SCHEMA_PLACES to render(places()),
            ArchiveLayout.SCHEMA_PROFILE to render(profile()),
            ArchiveLayout.SCHEMA_LOCATION_SAMPLE to render(locationSample()),
            ArchiveLayout.SCHEMA_MEDIA to render(media()),
        )
    }

    private fun render(schema: JsonObject): String = ArchiveJson.document.encodeToString(JsonElement.serializer(), schema)

    private fun manifest() =
        document(
            title = "LogDate export manifest",
            required =
                listOf(
                    "format",
                    "schemaVersion",
                    "exportedAt",
                    "exportTimeZone",
                    "generator",
                    "owner",
                    "scope",
                    "counts",
                    "contents",
                ),
            properties =
                mapOf(
                    "format" to buildJsonObject { put("const", "logdate-export") },
                    "schemaVersion" to
                        buildJsonObject {
                            put("type", "string")
                            put("pattern", "^2\\.[0-9]+$")
                        },
                    "exportedAt" to ref("instant"),
                    "exportTimeZone" to string(minLength = 1),
                    "generator" to obj(listOf("name", "version"), "name" to string(), "version" to string()),
                    "owner" to obj(emptyList(), "displayName" to string()),
                    "scope" to manifestScope(),
                    "counts" to manifestCounts(),
                    "contents" to manifestContents(),
                ),
            defs = defs("instant", "path"),
        )

    private fun manifestScope() =
        obj(
            listOf("complete"),
            "complete" to bool(),
            "dateRange" to obj(emptyList(), "from" to ref("instant"), "to" to ref("instant")),
            "omitted" to
                array(
                    obj(
                        listOf("category", "reason"),
                        "category" to enumOf(ArchiveCategory.serializer()),
                        "reason" to enumOf(ArchiveOmissionReason.serializer()),
                    ),
                ),
        )

    private fun manifestCounts() =
        obj(
            listOf("journals", "notes", "drafts", "media", "places", "locationSamples", "hasProfile"),
            "journals" to count(),
            "notes" to count(),
            "drafts" to count(),
            "media" to count(),
            "places" to count(),
            "locationSamples" to count(),
            "hasProfile" to bool(),
        )

    private fun manifestContents() =
        array(
            obj(
                listOf("role", "path", "mediaType"),
                "role" to enumOf(ArchiveRole.serializer()),
                "path" to ref("path"),
                "mediaType" to string(minLength = 1),
                "schema" to ref("path"),
            ),
        )

    private fun journals() =
        document(
            title = "LogDate export journals",
            required = listOf("journals"),
            properties =
                mapOf(
                    "journals" to
                        array(
                            obj(
                                listOf("id", "title", "description", "createdAt", "updatedAt"),
                                "id" to ref("uuid"),
                                "title" to string(),
                                "description" to string(),
                                "createdAt" to ref("instant"),
                                "updatedAt" to ref("instant"),
                            ),
                        ),
                ),
            defs = defs("uuid", "instant"),
        )

    private fun notes() =
        document(
            title = "LogDate export notes",
            required = listOf("notes"),
            properties = mapOf("notes" to array(ref("note"))),
            defs =
                defs("uuid", "instant", "path", "location", "mediaRef") +
                    (
                        "note" to
                            obj(
                                listOf("id", "type", "createdAt", "updatedAt", "journalIds"),
                                "id" to ref("uuid"),
                                "type" to enumOf(ArchiveNoteType.serializer()),
                                "createdAt" to ref("instant"),
                                "updatedAt" to ref("instant"),
                                "timeZone" to string(minLength = 1),
                                "createdAtLocal" to string(minLength = 1),
                                "text" to string(),
                                "textFormat" to enumOf(ArchiveTextFormat.serializer()),
                                "caption" to string(),
                                "media" to ref("mediaRef"),
                                "durationMs" to count(),
                                "location" to ref("location"),
                                "journalIds" to array(ref("uuid")),
                            )
                    ),
        )

    private fun drafts() =
        document(
            title = "LogDate export drafts",
            required = listOf("drafts"),
            properties = mapOf("drafts" to array(ref("draft"))),
            defs =
                defs("uuid", "instant", "path", "location", "mediaRef") +
                    (
                        "draft" to
                            obj(
                                listOf("id", "journalIds", "createdAt", "updatedAt", "blocks"),
                                "id" to ref("uuid"),
                                "journalIds" to array(ref("uuid")),
                                "createdAt" to ref("instant"),
                                "updatedAt" to ref("instant"),
                                "blocks" to array(ref("block")),
                            )
                    ) +
                    (
                        "block" to
                            obj(
                                listOf("id", "type", "timestamp"),
                                "id" to ref("uuid"),
                                "type" to enumOf(ArchiveBlockType.serializer()),
                                "timestamp" to ref("instant"),
                                "text" to string(),
                                "media" to ref("mediaRef"),
                                "caption" to string(),
                                "durationMs" to count(),
                                "transcription" to string(),
                                "location" to ref("location"),
                            )
                    ),
        )

    private fun places() =
        document(
            title = "LogDate export places",
            required = listOf("places"),
            properties =
                mapOf(
                    "places" to
                        array(
                            obj(
                                listOf("id", "name", "latitude", "longitude", "radiusMeters"),
                                "id" to ref("uuid"),
                                "name" to string(),
                                "latitude" to number(-90.0, 90.0),
                                "longitude" to number(-180.0, 180.0),
                                "radiusMeters" to number(0.0, null),
                                "description" to string(),
                            ),
                        ),
                ),
            defs = defs("uuid"),
        )

    private fun profile() =
        document(
            title = "LogDate export profile",
            required = listOf("profile"),
            properties =
                mapOf(
                    "profile" to
                        obj(
                            emptyList(),
                            "displayName" to string(),
                            "birthday" to ref("instant"),
                            "bio" to string(),
                            "originalBio" to string(),
                            "createdAt" to ref("instant"),
                            "updatedAt" to ref("instant"),
                        ),
                ),
            defs = defs("instant"),
        )

    /** Describes one line of `location-history.jsonl`. */
    private fun locationSample() =
        document(
            title = "LogDate export location sample (one line of location-history.jsonl)",
            required =
                listOf(
                    "timestamp",
                    "loggedAt",
                    "latitude",
                    "longitude",
                    "altitudeMeters",
                    "confidence",
                    "isGenuine",
                    "isMock",
                    "capturePipeline",
                    "captureSource",
                ),
            properties =
                mapOf(
                    "timestamp" to ref("instant"),
                    "loggedAt" to ref("instant"),
                    "latitude" to number(-90.0, 90.0),
                    "longitude" to number(-180.0, 180.0),
                    "altitudeMeters" to number(null, null),
                    "accuracyMeters" to number(0.0, null),
                    "speedMetersPerSecond" to number(0.0, null),
                    "bearingDegrees" to number(0.0, 360.0),
                    "confidence" to number(0.0, 1.0),
                    "isGenuine" to bool(),
                    "isMock" to bool(),
                    "capturePipeline" to string(minLength = 1),
                    "captureSource" to string(minLength = 1),
                ),
            defs = defs("instant"),
        )

    private fun media() =
        document(
            title = "LogDate export media inventory",
            required = listOf("files"),
            properties =
                mapOf(
                    "files" to
                        array(
                            obj(
                                listOf("path", "mediaType", "bytes", "sha256"),
                                "path" to ref("path"),
                                "mediaType" to string(minLength = 1),
                                "bytes" to count(),
                                "sha256" to
                                    buildJsonObject {
                                        put("type", "string")
                                        put("pattern", SHA256_PATTERN)
                                    },
                            ),
                        ),
                ),
            defs = defs("path"),
        )

    private fun document(
        title: String,
        required: List<String>,
        properties: Map<String, JsonElement>,
        defs: Map<String, JsonElement>,
    ): JsonObject =
        buildJsonObject {
            put("\$schema", DIALECT)
            put("title", title)
            put("type", "object")
            put("required", JsonArray(required.map { JsonPrimitive(it) }))
            put("properties", JsonObject(properties))
            if (defs.isNotEmpty()) put("\$defs", JsonObject(defs))
        }

    private fun defs(vararg names: String): Map<String, JsonElement> = names.associateWith(::shared)

    private fun shared(name: String): JsonElement =
        when (name) {
            "uuid" ->
                buildJsonObject {
                    put("type", "string")
                    put("pattern", UUID_PATTERN)
                }
            "instant" ->
                buildJsonObject {
                    put("type", "string")
                    put("pattern", INSTANT_PATTERN)
                }
            "path" ->
                buildJsonObject {
                    put("type", "string")
                    put("minLength", 1)
                    put("maxLength", ArchivePath.MAX_PATH_LENGTH)
                    put("pattern", PATH_PATTERN)
                }
            "location" ->
                obj(
                    listOf("latitude", "longitude"),
                    "latitude" to number(-90.0, 90.0),
                    "longitude" to number(-180.0, 180.0),
                    "altitudeMeters" to number(null, null),
                    "accuracyMeters" to number(0.0, null),
                    "placeName" to string(),
                )
            "mediaRef" -> mediaRef()
            else -> error("Unknown shared schema $name")
        }

    /** A media reference: an included file has a path and type, an omitted one has a reason. */
    private fun mediaRef(): JsonElement =
        buildJsonObject {
            put("type", "object")
            put("required", JsonArray(listOf(JsonPrimitive("status"))))
            put(
                "properties",
                JsonObject(
                    mapOf(
                        "status" to enumOf(ArchiveMediaStatus.serializer()),
                        "path" to ref("path"),
                        "mediaType" to string(minLength = 1),
                        "omittedReason" to enumOf(ArchiveOmissionReason.serializer()),
                    ),
                ),
            )
            put(
                "allOf",
                JsonArray(
                    listOf(
                        conditional("included", listOf("path", "mediaType")),
                        conditional("omitted", listOf("omittedReason")),
                    ),
                ),
            )
        }

    private fun conditional(
        status: String,
        required: List<String>,
    ): JsonElement =
        buildJsonObject {
            put("if", buildJsonObject { put("properties", buildJsonObject { put("status", buildJsonObject { put("const", status) }) }) })
            put("then", buildJsonObject { put("required", JsonArray(required.map { JsonPrimitive(it) })) })
        }

    private fun obj(
        required: List<String>,
        vararg properties: Pair<String, JsonElement>,
    ): JsonObject =
        buildJsonObject {
            put("type", "object")
            if (required.isNotEmpty()) put("required", JsonArray(required.map { JsonPrimitive(it) }))
            put("properties", JsonObject(properties.toMap()))
        }

    private fun array(items: JsonElement): JsonObject =
        buildJsonObject {
            put("type", "array")
            put("items", items)
        }

    private fun string(minLength: Int? = null): JsonObject =
        buildJsonObject {
            put("type", "string")
            if (minLength != null) put("minLength", minLength)
        }

    private fun bool(): JsonObject = buildJsonObject { put("type", "boolean") }

    private fun count(): JsonObject =
        buildJsonObject {
            put("type", "integer")
            put("minimum", 0)
        }

    private fun number(
        minimum: Double?,
        maximum: Double?,
    ): JsonObject =
        buildJsonObject {
            put("type", "number")
            if (minimum != null) put("minimum", minimum)
            if (maximum != null) put("maximum", maximum)
        }

    private fun ref(name: String): JsonObject = buildJsonObject { put("\$ref", "#/\$defs/$name") }

    private fun enumOf(serializer: KSerializer<*>): JsonObject =
        buildJsonObject {
            put("type", "string")
            put("enum", JsonArray(serializer.descriptor.elementNames.map { JsonPrimitive(it) }))
        }
}
