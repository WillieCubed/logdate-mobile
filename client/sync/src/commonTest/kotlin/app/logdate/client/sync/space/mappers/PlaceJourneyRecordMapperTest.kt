package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.shared.model.location.HistoryJourney
import app.logdate.shared.model.location.TravelMode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Instant

class PlaceJourneyRecordMapperTest {
    private val mapper = PlaceJourneyRecordMapper

    private val journey =
        HistoryJourney(
            id = "9c2e7d41-3b5a-4c8f-8e1d-6a7b8c9d0e1f",
            start = Instant.parse("2026-10-19T09:30:00Z"),
            createdAt = Instant.parse("2026-10-19T12:00:00.5Z"),
            fromVisitId = "3d1f6a2b-7c8e-4f90-a1b2-c3d4e5f6a7b8",
            toVisitId = "4e2a7b3c-8d9f-4a01-b2c3-d4e5f6a7b8c9",
            end = Instant.parse("2026-10-19T10:05:12Z"),
            mode = TravelMode.TRAIN,
            distanceMeters = 41_250,
        )

    @Test
    fun `a journey with every field round-trips`() {
        assertEquals(journey, mapper.roundTrip(journey))
    }

    @Test
    fun `a journey with only what is required round-trips and leaves the rest out`() {
        val bare = HistoryJourney(journey.id, journey.start, journey.createdAt)

        assertEquals(bare, mapper.roundTrip(bare))
        val record = mapper.toRecord(bare)
        listOf("from", "to", "endsAt", "mode", "distanceMeters").forEach { assertFalse(it in record, it) }
    }

    @Test
    fun `every travel mode round-trips`() {
        TravelMode.entries.forEach { assertEquals(journey.copy(mode = it), mapper.roundTrip(journey.copy(mode = it))) }
    }

    @Test
    fun `the record names visits by their keys and the mode by its lexicon name`() {
        val record = mapper.toRecord(journey)

        assertEquals("train", record["mode"]?.jsonPrimitive?.content)
        assertEquals(JsonPrimitive(41_250), record["distanceMeters"])
        val from = record.getValue("from").jsonObject
        assertEquals("app.logdate.place.visit", from["collection"]?.jsonPrimitive?.content)
        assertEquals(journey.fromVisitId, from["rkey"]?.jsonPrimitive?.content)
        assertEquals(journey.id, mapper.rkeyOf(journey))
    }

    @Test
    fun `journeys the record can't hold are refused`() {
        assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(journey.copy(end = Instant.parse("2026-10-19T09:00:00Z"))) }
        assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(journey.copy(distanceMeters = -1)) }
        assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(journey.copy(fromVisitId = "not a key")) }
        assertFailsWith<SpaceRecordMappingException> { mapper.rkeyOf(journey.copy(id = "Journey-1")) }
    }

    @Test
    fun `records that don't describe a journey are refused`() {
        val record = mapper.toRecord(journey)

        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(journey.id, record.with("mode", "teleport")) }
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(journey.id, record.with("endsAt", "2026-10-19T09:00:00Z")) }
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(journey.id, record.with("\$type", "app.logdate.place.visit")) }
        val wrongRef = JsonObject(mapOf("collection" to JsonPrimitive("app.logdate.place.place"), "rkey" to JsonPrimitive("home")))
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(journey.id, JsonObject(record + ("from" to wrongRef))) }
    }
}
