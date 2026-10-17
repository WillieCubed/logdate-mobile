package app.logdate.client.sync.datalayer

import app.logdate.client.repository.journals.CaptureActivity
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
 * What the person was doing at capture travels in the note messages the watch sends. Watches that
 * predate it send none, and an activity a newer version names must not stop the note from being read.
 */
class NoteDataMapperCapturedDuringTest {
    private val mapper = NoteDataMapper()
    private val at = Instant.fromEpochMilliseconds(1_000)
    private val note =
        JournalNote.Audio(
            mediaRef = "voice.m4a",
            creationTimestamp = at,
            lastUpdated = at,
            capturedDuring = CaptureActivity.MOVING,
        )

    @Test
    fun `a note captured while moving keeps its activity through a message`() {
        assertEquals(note, mapper.fromDataMap(mapper.toDataMap(note)))
    }

    @Test
    fun `the activity is written as its wire name`() {
        val payload = mapper.toDataMap(note).getValue(NoteDataMapper.KEY_JSON_PAYLOAD)

        assertEquals(JsonPrimitive("moving"), Json.parseToJsonElement(payload).jsonObject["capturedDuring"])
    }

    @Test
    fun `a note from a watch that predates the activity has none`() {
        val decoded = mapper.fromDataMap(withPayload { it - "capturedDuring" })

        assertNull(decoded.capturedDuring)
    }

    @Test
    fun `an activity this version does not know reads as unknown`() {
        val decoded = mapper.fromDataMap(withPayload { it + ("capturedDuring" to JsonPrimitive("swimming")) })

        assertNull(decoded.capturedDuring)
        assertTrue(decoded is JournalNote.Audio)
    }

    @Test
    fun `a note without an activity writes no activity key so its message and fingerprint are unchanged`() {
        val payload = mapper.toDataMap(note.copy(capturedDuring = null)).getValue(NoteDataMapper.KEY_JSON_PAYLOAD)

        assertTrue("capturedDuring" !in Json.parseToJsonElement(payload).jsonObject)
    }

    private fun withPayload(edit: (JsonObject) -> Map<String, JsonElement>): Map<String, String> {
        val map = mapper.toDataMap(note)
        val payload = Json.parseToJsonElement(map.getValue(NoteDataMapper.KEY_JSON_PAYLOAD)).jsonObject
        return map + (NoteDataMapper.KEY_JSON_PAYLOAD to JsonObject(edit(payload)).toString())
    }
}
