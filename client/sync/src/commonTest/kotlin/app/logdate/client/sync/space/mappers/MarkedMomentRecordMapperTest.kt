package app.logdate.client.sync.space.mappers

import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.sync.space.SpaceRecordMappingException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MarkedMomentRecordMapperTest {
    private val mapper = MarkedMomentRecordMapper

    private val full =
        MarkedMoment(
            id = Uuid.parse("6f1c2b0e-8a4d-4f3e-9b21-5c7d8e9f0a1b"),
            capturedAt = Instant.parse("2026-10-19T14:03:27.481Z"),
            timeZoneId = "America/Chicago",
            location = MarkLocation(latitude = 41.8781136, longitude = -87.6297982, accuracyMeters = 12.34f),
            heartRateBpm = 92,
            state = MarkState.FILLED,
            caption = "Saw the heron again",
            resolvedNoteIds = setOf(Uuid.parse("00000000-0000-4000-8000-000000000002"), Uuid.parse("00000000-0000-4000-8000-000000000001")),
            originDeviceId = "watch-7f3a",
            lastUpdated = Instant.parse("2026-10-19T18:00:00Z"),
            syncVersion = 0,
            capturedDuring = CaptureActivity.IN_VEHICLE,
        )

    @Test
    fun `a mark with every field round-trips`() {
        assertEquals(full, mapper.roundTrip(full))
    }

    @Test
    fun `a bare open mark round-trips and leaves its optional fields out`() {
        val bare =
            full.copy(
                location = null,
                heartRateBpm = null,
                state = MarkState.OPEN,
                caption = "",
                resolvedNoteIds = emptySet(),
                capturedDuring = null,
            )

        assertEquals(bare, mapper.roundTrip(bare))
        val record = mapper.toRecord(bare)
        listOf("caption", "resolvedEntries", "location", "heartRateBpm", "capturedDuring").forEach {
            assertFalse(it in record, "$it should be left out")
        }
    }

    @Test
    fun `every state and activity round-trips`() {
        MarkState.entries.forEach { state ->
            (CaptureActivity.entries + null).forEach { activity ->
                val mark = full.copy(state = state, capturedDuring = activity)
                assertEquals(mark, mapper.roundTrip(mark))
            }
        }
    }

    @Test
    fun `the record uses the lexicon's names and units`() {
        val record = mapper.toRecord(full)

        assertEquals("app.logdate.journal.markedMoment", record["\$type"]?.jsonPrimitive?.content)
        assertEquals("filled", record["state"]?.jsonPrimitive?.content)
        assertEquals("inVehicle", record["capturedDuring"]?.jsonPrimitive?.content)
        assertEquals("watch-7f3a", record["originDevice"]?.jsonPrimitive?.content)
        assertEquals("2026-10-19T14:03:27.481Z", record["createdAt"]?.jsonPrimitive?.content)
        assertEquals("2026-10-19T18:00:00Z", record["updatedAt"]?.jsonPrimitive?.content)
        val location = record.getValue("location").jsonObject
        assertEquals(JsonPrimitive(1234), location["accuracyCentimeters"])
        val geo = location.getValue("geo").jsonObject
        assertEquals("41.8781136", geo["latitude"]?.jsonPrimitive?.content)
        assertEquals("-87.6297982", geo["longitude"]?.jsonPrimitive?.content)
        val entries = record.getValue("resolvedEntries") as JsonArray
        assertEquals(
            listOf("00000000-0000-4000-8000-000000000001", "00000000-0000-4000-8000-000000000002"),
            entries.map { it.jsonObject["rkey"]?.jsonPrimitive?.content },
        )
        assertEquals("app.logdate.journal.entry", entries[0].jsonObject["collection"]?.jsonPrimitive?.content)
        assertEquals("6f1c2b0e-8a4d-4f3e-9b21-5c7d8e9f0a1b", mapper.rkeyOf(full))
    }

    @Test
    fun `edge values round-trip`() {
        val edges =
            listOf(
                full.copy(location = MarkLocation(-90.0, -180.0, 0f), heartRateBpm = 0),
                full.copy(location = MarkLocation(0.00001, 1.0E-7, null), heartRateBpm = 400),
                full.copy(location = MarkLocation(-0.0, 179.99999999999997, 65_000.5f)),
                full.copy(caption = "Ünïcödé 🐦‍⬛ 日本語 ".repeat(100), timeZoneId = "Asia/Kathmandu"),
                full.copy(
                    capturedAt = Instant.parse("2026-03-08T08:00:00.123456789Z"),
                    lastUpdated = Instant.parse("2026-03-08T08:00:01Z"),
                ),
                full.copy(resolvedNoteIds = List(200) { Uuid.random() }.toSet()),
            )

        edges.forEach { assertEquals(it, mapper.roundTrip(it)) }
    }

    @Test
    fun `accuracy is kept to the nearest centimeter`() {
        val precise = full.copy(location = MarkLocation(1.0, 2.0, 3.14159f))

        assertEquals(MarkLocation(1.0, 2.0, 3.14f), mapper.roundTrip(precise).location)
    }

    @Test
    fun `accuracy converts as the shared vectors say`() {
        val cases = conversionVectors("centimeters").filter { it.floatExact() }

        assertTrue(cases.size >= 5, "the vectors should cover the float cases")
        cases.forEach { case ->
            val mark = full.copy(location = MarkLocation(1.0, 2.0, case.input.toFloat()))
            val expected = case.expected
            if (expected == null) {
                assertFailsWith<SpaceRecordMappingException>("$case") { mapper.toRecord(mark) }
            } else {
                val written = mapper.toRecord(mark).getValue("location").jsonObject["accuracyCentimeters"]
                assertEquals(JsonPrimitive(expected), written, "$case")
            }
        }
    }

    @Test
    fun `a mark read from a record has no sync version until the kind gives it one`() {
        assertEquals(0, mapper.roundTrip(full.copy(syncVersion = 41)).syncVersion)
    }

    @Test
    fun `a negative accuracy is stored as zero`() {
        val record = mapper.toRecord(full.copy(location = MarkLocation(1.0, 2.0, -3f)))

        assertEquals(JsonPrimitive(0), record.getValue("location").jsonObject["accuracyCentimeters"])
    }

    @Test
    fun `values the record can't hold are refused without naming them`() {
        val refusals =
            listOf(
                full.copy(heartRateBpm = 401),
                full.copy(heartRateBpm = -1),
                full.copy(resolvedNoteIds = List(201) { Uuid.random() }.toSet()),
                full.copy(location = MarkLocation(Double.NaN, 0.0, null)),
                full.copy(location = MarkLocation(41.8781136, -87.6297982, Float.POSITIVE_INFINITY)),
            )

        refusals.forEach { mark ->
            val failure = assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(mark) }
            assertFalse(
                "41.87" in failure.message.orEmpty() || "87.62" in failure.message.orEmpty(),
                "A message must not hold a location: ${failure.message}",
            )
        }
    }

    @Test
    fun `records that don't describe a mark are refused`() {
        val record = mapper.toRecord(full)
        val rkey = mapper.rkeyOf(full)

        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord("not-a-uuid", record) }
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(rkey, record.with("\$type", "app.logdate.journal.entry")) }
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(rkey, record.with("state", "snoozed")) }
        assertFailsWith<SpaceRecordMappingException> {
            mapper.fromRecord(
                rkey,
                JsonObject(record + ("heartRateBpm" to JsonPrimitive(900))),
            )
        }
    }

    @Test
    fun `a location the mark has nowhere to keep is refused rather than reduced`() {
        val record = mapper.toRecord(full)
        val place =
            buildJsonObject {
                put("collection", "app.logdate.place.place")
                put("rkey", "home")
            }
        val geo =
            record
                .getValue("location")
                .jsonObject
                .getValue("geo")
                .jsonObject
        val locations =
            listOf(
                buildJsonObject { put("place", place) },
                buildJsonObject {
                    put("geo", geo)
                    put("place", place)
                },
                buildJsonObject { put("geo", JsonObject(geo + ("altitude" to JsonPrimitive("12.5")))) },
                buildJsonObject { put("geo", JsonObject(geo + ("name" to JsonPrimitive("Home")))) },
            )

        locations.forEach { location ->
            assertFailsWith<SpaceRecordMappingException>("$location") {
                mapper.fromRecord(mapper.rkeyOf(full), JsonObject(record + ("location" to location)))
            }
        }
    }

    @Test
    fun `an activity or state a newer app names is refused so it is not lost on the next write`() {
        val record = mapper.toRecord(full)

        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(mapper.rkeyOf(full), record.with("capturedDuring", "cycling")) }
    }

    @Test
    fun `a record without updatedAt was last updated when it was made`() {
        val record = JsonObject(mapper.toRecord(full) - "updatedAt")

        assertEquals(full.capturedAt, mapper.fromRecord(mapper.rkeyOf(full), record).lastUpdated)
    }
}
