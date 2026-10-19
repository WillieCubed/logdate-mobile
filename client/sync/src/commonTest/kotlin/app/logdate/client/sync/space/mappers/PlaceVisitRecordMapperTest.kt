package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.shared.model.location.ManualVisit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class PlaceVisitRecordMapperTest {
    private val mapper = PlaceVisitRecordMapper

    private val visit =
        ManualVisit(
            id = "3d1f6a2b-7c8e-4f90-a1b2-c3d4e5f6a7b8",
            start = Instant.parse("2026-10-19T09:30:00Z"),
            end = Instant.parse("2026-10-19T11:15:30.5Z"),
            placeId = "0b6e1c4d-2f3a-4b5c-8d9e-0f1a2b3c4d5e",
        )

    @Test
    fun `a visit round-trips`() {
        assertEquals(visit, mapper.roundTrip(visit))
    }

    @Test
    fun `an instant visit and a visit across years round-trip`() {
        listOf(
            visit.copy(end = visit.start),
            visit.copy(start = Instant.parse("2025-12-31T23:59:59.999999999Z"), end = Instant.parse("2026-01-01T00:00:00.000000001Z")),
        ).forEach { assertEquals(it, mapper.roundTrip(it)) }
    }

    @Test
    fun `the record refers to the place by its key`() {
        val record = mapper.toRecord(visit)
        val place = record.getValue("place").jsonObject
        assertEquals("app.logdate.place.place", place["collection"]?.jsonPrimitive?.content)
        assertEquals(visit.placeId, place["rkey"]?.jsonPrimitive?.content)
        assertEquals(visit.id, mapper.rkeyOf(visit))
    }

    @Test
    fun `a visit with its own location and a creation time round-trips`() {
        val located = visit.copy(latitude = -33.8688, longitude = 151.2093, createdAt = Instant.parse("2026-10-19T12:00:00.500Z"))

        assertEquals(located, mapper.roundTrip(located))
        val geo = mapper.toRecord(located).getValue("location").jsonObject
        assertEquals("-33.8688", geo["latitude"]?.jsonPrimitive?.content)
        assertEquals("2026-10-19T12:00:00.500Z", mapper.toRecord(located)["createdAt"]?.jsonPrimitive?.content)
    }

    @Test
    fun `visits the record can't hold are refused`() {
        assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(visit.copy(end = Instant.parse("2026-10-19T09:00:00Z"))) }
        assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(visit.copy(placeId = "a place/with a slash")) }
        assertFailsWith<SpaceRecordMappingException> { mapper.rkeyOf(visit.copy(id = "")) }
    }

    @Test
    fun `records that don't describe a manual visit are refused`() {
        val record = mapper.toRecord(visit)
        val geo =
            buildJsonObject {
                put("latitude", "1.5")
                put("longitude", "2.5")
                put("altitude", "9")
            }
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(visit.id, JsonObject(record + ("location" to geo))) }
        assertFailsWith<SpaceRecordMappingException> {
            mapper.fromRecord(
                visit.id,
                JsonObject(
                    record + ("endsAt" to JsonPrimitive("2026-10-19T09:00:00Z")),
                ),
            )
        }
        val wrongRef =
            buildJsonObject {
                put("collection", "app.logdate.journal.entry")
                put("rkey", visit.placeId)
            }
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(visit.id, JsonObject(record + ("place" to wrongRef))) }
    }
}
