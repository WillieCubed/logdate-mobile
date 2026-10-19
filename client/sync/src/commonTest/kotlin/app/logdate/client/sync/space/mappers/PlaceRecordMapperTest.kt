package app.logdate.client.sync.space.mappers

import app.logdate.client.database.entities.UserPlaceEntity
import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.shared.model.location.SemanticPlace
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class PlaceRecordMapperTest {
    private val mapper = PlaceRecordMapper
    private val id = "0b6e1c4d-2f3a-4b5c-8d9e-0f1a2b3c4d5e"

    private val saved =
        UserPlaceEntity(
            id = id,
            name = "Grandma's house",
            latitude = 41.8781136,
            longitude = -87.6297982,
            radiusMeters = 75.5,
            description = "The blue door 🚪",
            createdAt = 1_760_000_000_123,
            updatedAt = 1_760_100_000_456,
        )

    private val history =
        SemanticPlace(
            id = id,
            name = "Kowalski residence",
            latitude = 41.87812,
            longitude = -87.62979,
            locality = "1200 W Example St, Chicago",
            externalId = "ChIJ-example",
            userConfirmed = false,
        )

    @Test
    fun `a place that is saved and in history round-trips`() {
        val both = MergedPlace.of(saved, history)

        assertEquals(both, mapper.roundTrip(both))
    }

    @Test
    fun `a saved place and a history place each round-trip on their own`() {
        listOf(MergedPlace.of(saved, null), MergedPlace.of(null, history), MergedPlace.of(null, history.copy(userConfirmed = true)))
            .forEach { assertEquals(it, mapper.roundTrip(it)) }
    }

    @Test
    fun `the saved place's name coordinates and radius win`() {
        val both = MergedPlace.of(saved, history)

        assertEquals(saved.name, both.name)
        assertEquals(saved.latitude, both.latitude)
        assertEquals(saved.longitude, both.longitude)
        assertEquals(75.5, both.radiusMeters)
        assertEquals(history.locality, both.locality)
        assertEquals(history.externalId, both.externalId)
        assertTrue(both.userConfirmed)
        assertEquals(saved, both.savedPlace())
        assertEquals(
            history.copy(name = saved.name, latitude = saved.latitude, longitude = saved.longitude, userConfirmed = true),
            both.historyPlace(),
        )
    }

    @Test
    fun `a history place has no saved place and keeps its own values`() {
        val only = MergedPlace.of(null, history)

        assertNull(only.savedPlace())
        assertEquals(history, only.historyPlace())
        val record = mapper.toRecord(only)
        listOf("radiusCentimeters", "description", "createdAt", "updatedAt", "userConfirmed").forEach { assertFalse(it in record, it) }
    }

    @Test
    fun `the record uses the lexicon's names and units`() {
        val merged = MergedPlace.of(saved, history)
        val record = mapper.toRecord(merged)

        assertEquals(JsonPrimitive(7_550), record["radiusCentimeters"])
        assertEquals(
            "41.8781136",
            record
                .getValue("location")
                .jsonObject["latitude"]
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(JsonPrimitive(true), record["userConfirmed"])
        assertEquals("2025-10-09T08:53:20.123Z", record["createdAt"]?.jsonPrimitive?.content)
        assertEquals(id, mapper.rkeyOf(merged))
    }

    @Test
    fun `a radius converts as the shared vectors say`() {
        conversionVectors("centimeters").forEach { case ->
            val expected = case.expected
            if (expected == null) {
                assertFailsWith<SpaceRecordMappingException>("$case") { MergedPlace.of(saved.copy(radiusMeters = case.input), null) }
            } else {
                val place = MergedPlace.of(saved.copy(radiusMeters = case.input), null)
                assertEquals(JsonPrimitive(expected), mapper.toRecord(place)["radiusCentimeters"], "$case")
                assertEquals(place, mapper.roundTrip(place), "$case")
            }
        }
    }

    @Test
    fun `a radius is kept to the centimeter and a negative radius is zero`() {
        assertEquals(12.35, MergedPlace.of(saved.copy(radiusMeters = 12.3456), null).radiusMeters)
        assertEquals(0.0, MergedPlace.of(saved.copy(radiusMeters = -4.0), null).radiusMeters)
        val huge = MergedPlace.of(saved.copy(radiusMeters = 40_075_000.0), null)
        assertEquals(huge, mapper.roundTrip(huge))
    }

    @Test
    fun `edge values round-trip`() {
        listOf(
            MergedPlace.of(saved.copy(latitude = -90.0, longitude = 180.0, name = "", description = null), null),
            MergedPlace.of(
                null,
                history.copy(latitude = 0.000001, longitude = -0.0, name = "日本語の場所 ".repeat(500), locality = null, externalId = null),
            ),
        ).forEach { assertEquals(it, mapper.roundTrip(it)) }
    }

    @Test
    fun `a saved place written without a radius reads with the usual radius`() {
        val record = JsonObject(mapper.toRecord(MergedPlace.of(saved, null)) - "radiusCentimeters")
        val read = mapper.fromRecord(id, record)

        assertNull(read.radiusMeters)
        assertEquals(MergedPlace.DEFAULT_RADIUS_METERS, read.savedPlace()?.radiusMeters)
    }

    @Test
    fun `a saved place written without userConfirmed is still confirmed`() {
        val record = JsonObject(mapper.toRecord(MergedPlace.of(saved, null)) - "userConfirmed")

        assertTrue(mapper.fromRecord(id, record).userConfirmed)
    }

    @Test
    fun `a record without a name or a center is not read as a place with an empty name or a made-up center`() {
        val record = mapper.toRecord(MergedPlace.of(saved, null))

        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(id, JsonObject(record - "name")) }
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(id, JsonObject(record - "location")) }
        assertFailsWith<SpaceRecordMappingException> {
            mapper.fromRecord(id, JsonObject(record + ("location" to JsonObject(record.getValue("location").jsonObject - "latitude"))))
        }
        assertTrue(lexiconIssues("app.logdate.place.place", JsonObject(record - "name")).isNotEmpty(), "the lexicon requires a name")
    }

    @Test
    fun `places the device can't store are refused without naming where they are`() {
        assertFailsWith<IllegalArgumentException> { MergedPlace.of(null, null) }
        assertFailsWith<IllegalArgumentException> { MergedPlace.of(saved, history.copy(id = "other")) }
        assertFailsWith<SpaceRecordMappingException> { MergedPlace(id, "Home", 0.0, 0.0, savedAt = Instant.parse("2026-01-01T00:00:00Z")) }
        val record = mapper.toRecord(MergedPlace.of(saved, null))
        val center = record.getValue("location").jsonObject
        listOf("altitude" to "12.0", "name" to "Front door").forEach { (field, value) ->
            val failure =
                assertFailsWith<SpaceRecordMappingException> {
                    mapper.fromRecord(id, JsonObject(record + ("location" to JsonObject(center + (field to JsonPrimitive(value))))))
                }
            assertFalse("41.87" in failure.message.orEmpty())
        }
        assertFailsWith<SpaceRecordMappingException> { mapper.rkeyOf(MergedPlace.of(null, history.copy(id = "evidence:a b"))) }
        assertFailsWith<SpaceRecordMappingException> { mapper.rkeyOf(MergedPlace.of(null, history.copy(id = "Upper-Case"))) }
        assertFailsWith<SpaceRecordMappingException> { MergedPlace.of(saved.copy(latitude = Double.NaN), null).let { mapper.toRecord(it) } }
    }

    @Test
    fun `a place with an id other than a LogDate id still round-trips when the id is a valid key`() {
        val place = MergedPlace.of(null, history.copy(id = "evidence:6f1c2b0e-8a4d-4f3e-9b21-5c7d8e9f0a1b"))

        assertEquals(place, mapper.roundTrip(place))
    }
}
