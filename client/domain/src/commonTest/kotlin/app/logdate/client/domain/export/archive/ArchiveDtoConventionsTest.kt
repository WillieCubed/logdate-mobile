package app.logdate.client.domain.export.archive

import app.logdate.client.domain.export.archive.support.ArchiveLeakScanner
import app.logdate.client.domain.export.archive.support.ArchiveSamples
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the rules every 2.0 file follows, so a new field cannot quietly break them for the programs
 * that read archives.
 */
class ArchiveDtoConventionsTest {
    private fun <T> encoded(
        serializer: KSerializer<T>,
        value: T,
    ): JsonElement = Json.parseToJsonElement(ArchiveJson.document.encodeToString(serializer, value))

    private val samples: Map<String, JsonElement> =
        mapOf(
            "manifest" to encoded(ArchiveManifest.serializer(), ArchiveSamples.manifest),
            "journals" to encoded(ArchiveJournalFile.serializer(), ArchiveSamples.journalFile),
            "notes" to encoded(ArchiveNoteFile.serializer(), ArchiveSamples.noteFile),
            "drafts" to encoded(ArchiveDraftFile.serializer(), ArchiveSamples.draftFile),
            "places" to encoded(ArchivePlaceFile.serializer(), ArchiveSamples.placeFile),
            "profile" to encoded(ArchiveProfileFile.serializer(), ArchiveSamples.profileFile),
            "locationSample" to encoded(ArchiveLocationSample.serializer(), ArchiveSamples.locationSample),
            "media" to encoded(ArchiveMediaFile.serializer(), ArchiveSamples.mediaFile),
        )

    private fun keys(element: JsonElement): List<String> =
        when (element) {
            is JsonObject -> element.keys.toList() + element.values.flatMap(::keys)
            is JsonArray -> element.flatMap(::keys)
            else -> emptyList()
        }

    private fun strings(element: JsonElement): List<String> =
        when (element) {
            is JsonObject -> element.values.flatMap(::strings)
            is JsonArray -> element.flatMap(::strings)
            is JsonPrimitive -> if (element.isString) listOf(element.content) else emptyList()
        }

    private fun hasNull(element: JsonElement): Boolean =
        when (element) {
            JsonNull -> true
            is JsonObject -> element.values.any(::hasNull)
            is JsonArray -> element.any(::hasNull)
            else -> false
        }

    @Test
    fun `every key is lower camel case`() {
        val camelCase = Regex("^[a-z][A-Za-z0-9]*$")

        samples.forEach { (file, json) ->
            val bad = keys(json).filterNot { camelCase.matches(it) }
            assertTrue(bad.isEmpty(), "$file has keys that are not camelCase: $bad")
        }
    }

    @Test
    fun `no key exposes a device reference or sync internals`() {
        val internal = setOf("syncVersion", "deviceId", "userId", "sampleId", "mediaPath", "sourceUri")

        samples.forEach { (file, json) ->
            val bad = keys(json).filter { it in internal || it.equals("uri", ignoreCase = true) || it.endsWith("Uri") }
            assertTrue(bad.isEmpty(), "$file has internal-looking keys: $bad")
        }
    }

    @Test
    fun `no value is a device path, a content uri or a class name`() {
        samples.forEach { (file, json) ->
            val leaks = ArchiveLeakScanner.findInJson(json.toString())
            assertTrue(leaks.isEmpty(), "$file leaks device references: $leaks")
            val classNames = strings(json).filter { it.startsWith("app.logdate.") }
            assertTrue(classNames.isEmpty(), "$file has Kotlin class names as values: $classNames")
        }
    }

    @Test
    fun `nulls are left out instead of written`() {
        samples.forEach { (file, json) -> assertTrue(!hasNull(json), "$file writes a null") }
    }

    @Test
    fun `every instant is RFC 3339 in UTC`() {
        val instant = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,9})?Z$")
        val timestampKeys = setOf("createdAt", "updatedAt", "exportedAt", "timestamp", "loggedAt", "from", "to", "birthday")

        fun check(
            file: String,
            element: JsonElement,
        ) {
            if (element is JsonObject) {
                element.forEach { (key, value) ->
                    if (key in timestampKeys && value is JsonPrimitive) {
                        assertTrue(instant.matches(value.content), "$file.$key is not RFC 3339 UTC: ${value.content}")
                    }
                    check(file, value)
                }
            }
            if (element is JsonArray) element.forEach { check(file, it) }
        }
        samples.forEach { (file, json) -> check(file, json) }
    }

    @Test
    fun `the schema version is written as a major dot minor string`() {
        val manifest = samples.getValue("manifest") as JsonObject

        assertEquals("2.0", (manifest.getValue("schemaVersion") as JsonPrimitive).content)
    }

    @Test
    fun `every sample survives a write and a read unchanged`() {
        fun <T> roundTrip(
            serializer: KSerializer<T>,
            value: T,
        ) = assertEquals(value, ArchiveJson.document.decodeFromString(serializer, ArchiveJson.document.encodeToString(serializer, value)))

        roundTrip(ArchiveManifest.serializer(), ArchiveSamples.manifest)
        roundTrip(ArchiveJournalFile.serializer(), ArchiveSamples.journalFile)
        roundTrip(ArchiveNoteFile.serializer(), ArchiveSamples.noteFile)
        roundTrip(ArchiveDraftFile.serializer(), ArchiveSamples.draftFile)
        roundTrip(ArchivePlaceFile.serializer(), ArchiveSamples.placeFile)
        roundTrip(ArchiveProfileFile.serializer(), ArchiveSamples.profileFile)
        roundTrip(ArchiveLocationSample.serializer(), ArchiveSamples.locationSample)
        roundTrip(ArchiveMediaFile.serializer(), ArchiveSamples.mediaFile)
    }

    @Test
    fun `an unsafe media path in a file makes it unreadable`() {
        val hostile = """{"status":"included","path":"../../etc/passwd","mediaType":"image/jpeg"}"""

        val failure = runCatching { ArchiveJson.document.decodeFromString(ArchiveMediaRef.serializer(), hostile) }.exceptionOrNull()

        assertTrue(failure != null, "an archive with a path outside the archive must not be readable")
    }
}
