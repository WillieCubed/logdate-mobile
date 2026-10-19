package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.lexicon.app.logdate.place.Sample
import app.logdate.shared.model.location.TravelMode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class LocationSampleRecordMapperTest {
    private var signedIn: String? = TEST_OWNER
    private val mapper = LocationSampleRecordMapper { signedIn }

    private val sample =
        sampleAt(723, id = "4b8e0d2a-1c3f-4e5a-9b7c-0d1e2f3a4b5c").copy(
            latitude = 41.8781136,
            longitude = -87.6297982,
            accuracyMeters = 12.5f,
            speedMetersPerSecond = 1.42f,
            bearingDegrees = 271.35f,
            activity = TravelMode.DRIVING,
            source = "FOREGROUND_STREAM",
            isMock = true,
            altitudeMeters = 182.5,
        )

    private val change =
        changeAt(719, id = "9f2c4e6a8b0d1f3e5a7c9b1d3f5e7a9c0b2d4f6e8a0c2e4f6a8b0d2f4e6a8c0b").copy(
            activityType = "IN_VEHICLE",
            transitionType = "ENTER",
        )

    private fun part(
        observations: List<app.logdate.shared.model.location.LocationObservation> = listOf(sample),
        activities: List<app.logdate.shared.model.location.ActivityObservation> = listOf(change),
        deletedSamples: List<String> = listOf("deleted-sample"),
        deletedChanges: List<String> = listOf("deleted-change"),
        number: Int = 1,
    ) = LocationHourPart(number, TEST_DEVICE, TEST_HOUR, observations, activities, deletedSamples, deletedChanges)

    @Test
    fun `a part with every field round-trips`() {
        val full = part()

        assertEquals(full, mapper.roundTrip(full))
    }

    @Test
    fun `a part with only deletions round-trips and leaves its lists out`() {
        val deletionsOnly = part(emptyList(), emptyList())

        assertEquals(deletionsOnly, mapper.roundTrip(deletionsOnly))
        val record = mapper.toRecord(deletionsOnly)
        assertFalse("samples" in record || "transitions" in record)
    }

    @Test
    fun `the record uses the lexicon's names units and key`() {
        val record = mapper.toRecord(part())

        assertEquals("2026-10-19T14:$TEST_DEVICE", mapper.rkeyOf(part()))
        assertFalse("part" in record)
        assertEquals(Sample.TYPE, record["\$type"]?.jsonPrimitive?.content)
        assertEquals(TEST_DEVICE, record["device"]?.jsonPrimitive?.content)
        assertEquals("2026-10-19T14:00:00Z", record["startsAt"]?.jsonPrimitive?.content)
        val point = (record.getValue("samples") as JsonArray).single().jsonObject
        assertEquals("41.8781136", point["latitude"]?.jsonPrimitive?.content)
        assertEquals("-87.6297982", point["longitude"]?.jsonPrimitive?.content)
        assertEquals("182.5", point["altitudeMeters"]?.jsonPrimitive?.content)
        assertEquals(JsonPrimitive(1250), point["accuracyCentimeters"])
        assertEquals(JsonPrimitive(142), point["speedCentimetersPerSecond"])
        assertEquals(JsonPrimitive(27135), point["bearingCentidegrees"])
        assertEquals("driving", point["activity"]?.jsonPrimitive?.content)
        assertEquals("foregroundStream", point["source"]?.jsonPrimitive?.content)
        assertEquals(JsonPrimitive(true), point["mock"])
        val transition = (record.getValue("transitions") as JsonArray).single().jsonObject
        assertEquals("inVehicle", transition["activity"]?.jsonPrimitive?.content)
        assertEquals("enter", transition["transition"]?.jsonPrimitive?.content)
        assertEquals(listOf("deleted-sample"), (record.getValue("deletedSampleIds") as JsonArray).map { it.jsonPrimitive.content })
        assertEquals(listOf("deleted-change"), (record.getValue("deletedTransitionIds") as JsonArray).map { it.jsonPrimitive.content })
    }

    @Test
    fun `later parts have a numbered key and say which part they are`() {
        val second = part(number = 2)
        val record = mapper.toRecord(second)

        assertEquals("2026-10-19T14:$TEST_DEVICE:2", mapper.rkeyOf(second))
        assertEquals(JsonPrimitive(2), record["part"])
        assertEquals(second, mapper.roundTrip(second))
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord("2026-10-19t14:$TEST_DEVICE:3", record) }
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord("2026-10-19t14:$TEST_DEVICE", record) }
        assertFailsWith<SpaceRecordMappingException> {
            mapper.fromRecord(
                "2026-10-19t14:$TEST_DEVICE:21",
                JsonObject(
                    record + ("part" to JsonPrimitive(21)),
                ),
            )
        }
    }

    @Test
    fun `values a sample leaves at their default are left out`() {
        val plain =
            sample.copy(
                accuracyMeters = null,
                speedMetersPerSecond = null,
                bearingDegrees = null,
                activity = TravelMode.UNKNOWN,
                timeZoneId = null,
                source = "UNKNOWN",
                isMock = false,
                altitudeMeters = null,
            )
        val record = mapper.toRecord(part(listOf(plain)))

        val point = (record.getValue("samples") as JsonArray).single().jsonObject
        assertEquals(setOf("id", "at", "latitude", "longitude"), point.keys)
        assertEquals(plain, mapper.roundTrip(part(listOf(plain))).observations.single())
    }

    @Test
    fun `every travel mode source and activity the device has round-trips`() {
        val sources =
            listOf(
                "BACKGROUND_PERIODIC",
                "PASSIVE_UPDATE",
                "ACTIVITY_TRANSITION",
                "GEOFENCE_TRANSITION",
                "SERVER_NUDGE",
                "FOREGROUND_STREAM",
                "JOURNAL_ENTRY",
                "TIMELINE_REVIEW",
                "MANUAL",
                "RETRY",
                "UNKNOWN",
            )
        val samples =
            (0 until maxOf(TravelMode.entries.size, sources.size)).map { index ->
                sampleAt(index).copy(
                    activity = TravelMode.entries[index % TravelMode.entries.size],
                    source = sources[index % sources.size],
                )
            }
        val types = listOf("STILL", "WALKING", "RUNNING", "ON_FOOT", "ON_BICYCLE", "IN_VEHICLE", "UNKNOWN")
        val changes =
            types
                .flatMapIndexed { index, type ->
                    listOf("ENTER", "EXIT").map { transition ->
                        changeAt(index).copy(id = "$type-$transition", activityType = type, transitionType = transition)
                    }
                }.sortedWith(compareBy({ it.timestamp }, { it.id }))
        val everything = part(samples, changes)

        assertEquals(everything, mapper.roundTrip(everything))
    }

    @Test
    fun `a source activity or travel mode the device doesn't know is refused rather than lost`() {
        val unknownSource = part(listOf(sample.copy(source = "A_SOURCE_FROM_LATER")))
        val unknownActivity = part(activities = listOf(change.copy(activityType = "TILTING")))
        val unknownTransition = part(activities = listOf(change.copy(transitionType = "PAUSE")))

        listOf(unknownSource, unknownActivity, unknownTransition).forEach { value ->
            assertFailsWith<SpaceRecordMappingException> { mapper.toRecord(value) }
        }
        val record = mapper.toRecord(part())
        val point = (record.getValue("samples") as JsonArray).single().jsonObject
        assertFailsWith<SpaceRecordMappingException> {
            mapper.fromRecord(
                mapper.rkeyOf(part()),
                JsonObject(
                    record + ("samples" to JsonArray(listOf(JsonObject(point + ("source" to JsonPrimitive("later")))))),
                ),
            )
        }
        assertFailsWith<SpaceRecordMappingException> {
            mapper.fromRecord(
                mapper.rkeyOf(part()),
                JsonObject(
                    record + ("samples" to JsonArray(listOf(JsonObject(point + ("activity" to JsonPrimitive("flying")))))),
                ),
            )
        }
    }

    @Test
    fun `edge coordinates and times round-trip`() {
        val edges =
            listOf(
                sampleAt(0).copy(id = "a", latitude = -90.0, longitude = -180.0),
                sampleAt(1).copy(id = "b", latitude = 1.0E-7, longitude = -0.00001, timestamp = TEST_HOUR + 59.minutes),
                sampleAt(
                    2,
                ).copy(
                    id = "c",
                    latitude = -0.0,
                    longitude = 179.99999999999997,
                    accuracyMeters = 0f,
                    speedMetersPerSecond = 0f,
                    bearingDegrees = 0f,
                ),
                sampleAt(3).copy(id = "d", timeZoneId = "Asia/Kathmandu", bearingDegrees = 359.99f, accuracyMeters = 65_000.5f),
            ).sortedWith(compareBy({ it.timestamp }, { it.id }))
        val value = part(edges, emptyList(), emptyList(), emptyList())

        assertEquals(value, mapper.roundTrip(value))
    }

    @Test
    fun `measurements are kept at the precision of the record`() {
        val precise = sampleAt(0).copy(accuracyMeters = 3.14159f, speedMetersPerSecond = 0.004f, bearingDegrees = 359.996f)

        val read = mapper.roundTrip(part(listOf(precise), emptyList(), emptyList(), emptyList())).observations.single()

        assertEquals(3.14f, read.accuracyMeters)
        assertEquals(0.0f, read.speedMetersPerSecond)
        assertEquals(0.0f, read.bearingDegrees)
        assertEquals(LocationHour.atRecordPrecision(precise), read)
    }

    @Test
    fun `accuracy and speed convert as the shared centimeter vectors say`() {
        conversionVectors("centimeters").filter { it.floatExact() }.forEach { case ->
            val value =
                part(
                    listOf(sample.copy(accuracyMeters = case.input.toFloat(), speedMetersPerSecond = case.input.toFloat())),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                )
            val expected = case.expected
            if (expected == null) {
                assertFailsWith<SpaceRecordMappingException>("$case") { mapper.toRecord(value) }
            } else {
                val point = (mapper.toRecord(value).getValue("samples") as JsonArray).single().jsonObject
                assertEquals(JsonPrimitive(expected), point["accuracyCentimeters"], "$case")
                assertEquals(JsonPrimitive(expected), point["speedCentimetersPerSecond"], "$case")
            }
        }
    }

    @Test
    fun `bearing converts as the shared vectors say`() {
        conversionVectors("bearing").filter { it.floatExact() }.forEach { case ->
            val value = part(listOf(sample.copy(bearingDegrees = case.input.toFloat())), emptyList(), emptyList(), emptyList())
            val expected = case.expected
            if (expected == null) {
                assertFailsWith<SpaceRecordMappingException>("$case") { mapper.toRecord(value) }
            } else {
                val point = (mapper.toRecord(value).getValue("samples") as JsonArray).single().jsonObject
                assertEquals(JsonPrimitive(expected), point["bearingCentidegrees"], "$case")
            }
        }
    }

    @Test
    fun `a full part is a valid record`() {
        val hour = hourOf(samples = 5_000, changes = 500)

        val value = hour.parts().single()

        assertEquals(5_000, value.observations.size)
        assertEquals(value, mapper.roundTrip(value))
    }

    @Test
    fun `records that don't describe a part of an hour are refused`() {
        val record = mapper.toRecord(part())
        val rkey = mapper.rkeyOf(part())

        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord("2026-10-19t15:$TEST_DEVICE", record) }
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(rkey, record.with("startsAt", "2026-10-19T14:30:00Z")) }
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(rkey, record.with("\$type", "app.logdate.place.visit")) }
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(rkey, record.with("device", "Phone")) }
        signedIn = null
        assertFailsWith<SpaceRecordMappingException> { mapper.fromRecord(rkey, record) }
    }

    @Test
    fun `a device id that can't be part of a key is refused`() {
        assertFailsWith<SpaceRecordMappingException> { LocationSampleRecordMapper.rkey("Phone", TEST_HOUR) }
        assertFailsWith<SpaceRecordMappingException> { LocationSampleRecordMapper.rkey("a:b", TEST_HOUR) }
        assertFailsWith<SpaceRecordMappingException> { LocationSampleRecordMapper.rkey("device", TEST_HOUR + 5.minutes) }
        assertFailsWith<SpaceRecordMappingException> { LocationSampleRecordMapper.rkey("device", TEST_HOUR, part = 0) }
        assertTrue(LocationSampleRecordMapper.rkey("device", TEST_HOUR + 1.hours).endsWith("15:device"))
    }
}
