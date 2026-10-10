package app.logdate.client.sync.datalayer

import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The capture device travels in the note messages the watch sends. Watches that predate it send no
 * device, and a device a newer version names must not stop the note from being read.
 */
class NoteDataMapperCapturedOnTest {
    private val mapper = NoteDataMapper()
    private val at = Instant.fromEpochMilliseconds(1_000)
    private val note = JournalNote.Audio(mediaRef = "voice.m4a", creationTimestamp = at, lastUpdated = at, capturedOn = CaptureDevice.WATCH)

    @Test
    fun `a note captured on the watch keeps its device through a message`() {
        assertEquals(note, mapper.fromDataMap(mapper.toDataMap(note)))
    }

    @Test
    fun `the device is written as its wire name`() {
        val payload = mapper.toDataMap(note).getValue(NoteDataMapper.KEY_JSON_PAYLOAD)

        assertEquals(JsonPrimitive("watch"), Json.parseToJsonElement(payload).jsonObject["capturedOn"])
    }

    @Test
    fun `a note from a watch that predates the device has none`() {
        val decoded = mapper.fromDataMap(withPayload { it - "capturedOn" })

        assertNull(decoded.capturedOn)
    }

    @Test
    fun `a device this version does not know reads as unknown`() {
        val decoded = mapper.fromDataMap(withPayload { it + ("capturedOn" to JsonPrimitive("tablet")) })

        assertNull(decoded.capturedOn)
        assertTrue(decoded is JournalNote.Audio)
    }

    private fun withPayload(edit: (JsonObject) -> Map<String, JsonElement>): Map<String, String> {
        val map = mapper.toDataMap(note)
        val payload = Json.parseToJsonElement(map.getValue(NoteDataMapper.KEY_JSON_PAYLOAD)).jsonObject
        return map + (NoteDataMapper.KEY_JSON_PAYLOAD to JsonObject(edit(payload)).toString())
    }
}
