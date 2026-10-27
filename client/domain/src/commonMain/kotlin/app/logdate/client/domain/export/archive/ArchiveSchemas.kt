package app.logdate.client.domain.export.archive

import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.repository.transcription.TranscriptSource
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The JSON Schemas (draft 2020-12) that describe the files in a 2.x archive.
 *
 * A copy of each is written into the archive under `schema/`, so a program can check a file without
 * network access or the app. They are deliberately permissive about extra keys, because a minor
 * version only adds fields; the tests validate against a stricter copy to catch fields a schema
 * forgot.
 */
object ArchiveSchemas {
    private const val SHA256_PATTERN = "^[0-9a-f]{64}$"

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
                                "favorite" to bool(),
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
                                "handwriting" to handwriting(),
                                "unreadableHandwriting" to string(),
                                "textFormat" to enumOf(ArchiveTextFormat.serializer()),
                                "caption" to string(),
                                "photoPresentation" to string(),
                                "capturedOn" to string(minLength = 1),
                                "capturedDuring" to string(minLength = 1),
                                "transcript" to transcript(),
                                "transcription" to string(),
                                "media" to ref("mediaRef"),
                                "durationMs" to count(),
                                "location" to ref("location"),
                                "locationDetails" to noteLocationDetails(),
                                "journalIds" to array(ref("uuid")),
                            )
                    ),
        )

    private fun handwriting() =
        obj(
            listOf("strokes"),
            "version" to count(),
            "width" to number(0.0, null),
            "height" to number(0.0, null),
            "strokes" to
                array(
                    obj(
                        listOf("id", "tool", "color", "width", "points"),
                        "id" to ref("uuid"),
                        "tool" to string(),
                        "color" to string(),
                        "width" to number(0.0, null),
                        "x" to number(null, null),
                        "y" to number(null, null),
                        "rotation" to number(null, null),
                        "scaleX" to number(null, null),
                        "scaleY" to number(null, null),
                        "zIndex" to count(),
                        "points" to
                            array(
                                obj(
                                    listOf("x", "y"),
                                    "x" to number(null, null),
                                    "y" to number(null, null),
                                    "pressure" to number(0.0, 1.0),
                                ),
                            ),
                    ),
                ),
        )

    private fun transcript() =
        obj(
            emptyList(),
            "revision" to count(),
            "status" to enumOf(TranscriptDocumentStatus.serializer()),
            "language" to string(),
            "createdAt" to ref("instant"),
            "updatedAt" to ref("instant"),
            "soundTags" to
                array(
                    obj(
                        listOf("name", "startMs"),
                        "name" to string(),
                        "startMs" to count(),
                        "durationMs" to count(),
                        "confidence" to number(minimum = 0.0, maximum = 1.0),
                    ),
                ),
            "engine" to obj(listOf("name"), "name" to string(), "modelId" to string(), "version" to string()),
            "speakers" to array(obj(listOf("speakerId", "label"), "speakerId" to string(), "label" to string(), "colorToken" to string())),
            "segments" to
                array(
                    obj(
                        listOf("segmentId", "text", "startMs", "endMs"),
                        "segmentId" to string(),
                        "text" to string(),
                        "startMs" to count(),
                        "endMs" to count(),
                        "speakerId" to string(),
                        "confidence" to number(minimum = 0.0, maximum = 1.0),
                        "source" to enumOf(TranscriptSource.serializer()),
                        "isFinal" to bool(),
                        "words" to
                            array(
                                obj(
                                    listOf("text", "normalizedText", "startMs", "endMs"),
                                    "text" to string(),
                                    "normalizedText" to string(),
                                    "startMs" to count(),
                                    "endMs" to count(),
                                    "confidence" to number(minimum = 0.0, maximum = 1.0),
                                    "speakerId" to string(),
                                ),
                            ),
                    ),
                ),
        )

    private fun noteLocationDetails() =
        obj(
            emptyList(),
            "coordinates" to
                obj(
                    listOf("latitude", "longitude"),
                    "latitude" to number(minimum = -90.0, maximum = 90.0),
                    "longitude" to number(minimum = -180.0, maximum = 180.0),
                    "altitudeMeters" to number(minimum = null, maximum = null),
                    "accuracyMeters" to number(minimum = 0.0, maximum = null),
                ),
            "place" to
                JsonObject(
                    obj(
                        listOf("id"),
                        "id" to ref("uuid"),
                        "name" to string(),
                        "latitude" to number(minimum = -90.0, maximum = 90.0),
                        "longitude" to number(minimum = -180.0, maximum = 180.0),
                    ) +
                        (
                            "dependentRequired" to
                                buildJsonObject {
                                    put("latitude", buildJsonArray { add(JsonPrimitive("longitude")) })
                                    put("longitude", buildJsonArray { add(JsonPrimitive("latitude")) })
                                }
                        ),
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
                                "handwriting" to handwriting(),
                                "unreadableHandwriting" to string(),
                                "media" to ref("mediaRef"),
                                "thumbnail" to ref("mediaRef"),
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
                            "birthday" to
                                buildJsonObject {
                                    put(
                                        "anyOf",
                                        buildJsonArray {
                                            add(
                                                buildJsonObject {
                                                    put("type", "string")
                                                    put("pattern", "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
                                                },
                                            )
                                            add(ref("instant"))
                                        },
                                    )
                                },
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
}
