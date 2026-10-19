package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceRecordMappingException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class LocationHourTest {
    private fun LocationHour.sampleIds() = samples.map { it.id }

    /** A record lists a deleted id without the time of the sample, so a hour read from records has none for its deleted entries. */
    private fun LocationHour.forgettingDeletedTimes() =
        copy(
            samples = samples.map { if (it.deleted) it.copy(at = hourStart) else it }.sortedWith(LocationHour.sampleOrder),
            transitions = transitions.map { if (it.deleted) it.copy(at = hourStart) else it }.sortedWith(LocationHour.transitionOrder),
        )

    @Test
    fun `an hour is identified by its device and its start`() {
        assertEquals("2026-10-19t14:$TEST_DEVICE", LocationHour.itemId(TEST_DEVICE, TEST_HOUR))
        assertEquals("2026-10-19t14:$TEST_DEVICE:2", LocationHour.itemId(TEST_DEVICE, TEST_HOUR, part = 2))
        assertEquals(TEST_HOUR, LocationHour.hourOf(Instant.parse("2026-10-19T14:59:59.999Z")))
        assertEquals(Instant.parse("2026-10-19T14:00:00Z"), LocationHour.hourOf(Instant.parse("2026-10-19T14:00:00Z")))
        assertEquals(Instant.parse("1969-12-31T23:00:00Z"), LocationHour.hourOf(Instant.parse("1969-12-31T23:30:00Z")))
    }

    @Test
    fun `an hour refuses entries that don't belong to it`() {
        val sample = sampleAt(1)

        assertFailsWith<SpaceRecordMappingException> {
            LocationHour(TEST_DEVICE, TEST_HOUR + 1.hours, listOf(HourSample(sample.id, sample.timestamp, sample)))
        }
        assertFailsWith<SpaceRecordMappingException> {
            LocationHour("other-device", TEST_HOUR, listOf(HourSample(sample.id, sample.timestamp, sample)))
        }
        assertFailsWith<SpaceRecordMappingException> {
            LocationHour(
                TEST_DEVICE,
                TEST_HOUR,
                listOf(HourSample(sample.id, sample.timestamp, sample), HourSample(sample.id, sample.timestamp)),
            )
        }
        assertFailsWith<SpaceRecordMappingException> {
            LocationHour(TEST_DEVICE, TEST_HOUR, listOf(HourSample("b", TEST_HOUR), HourSample("a", TEST_HOUR)))
        }
        assertFailsWith<SpaceRecordMappingException> { LocationHour(TEST_DEVICE, Instant.parse("2026-10-19T14:30:00Z")) }
    }

    @Test
    fun `a small hour is one part holding everything`() {
        val hour = hourOf(samples = 3, changes = 2, deletedSamples = 1, deletedChanges = 1)

        val parts = hour.parts()

        assertEquals(1, parts.size)
        assertEquals(1, parts.single().number)
        assertEquals(2, parts.single().observations.size)
        assertEquals(listOf("sample-00000"), parts.single().deletedSampleIds)
        assertEquals(1, parts.single().activities.size)
        assertEquals(listOf("change-0000"), parts.single().deletedTransitionIds)
        assertEquals(hour.forgettingDeletedTimes(), LocationHour.of(parts))
    }

    @Test
    fun `an empty hour is still one part`() {
        assertEquals(1, LocationHour(TEST_DEVICE, TEST_HOUR).parts().size)
    }

    @Test
    fun `a part holds 5000 samples counting deleted ones and the next sample starts the next part`() {
        val full = hourOf(samples = 5_000)
        val over = hourOf(samples = 5_001)

        assertEquals(1, full.parts().size)
        assertEquals(
            5_000,
            full
                .parts()
                .single()
                .observations.size,
        )
        val parts = over.parts()
        assertEquals(listOf(5_000, 1), parts.map { it.observations.size })
        assertEquals(listOf(1, 2), parts.map { it.number })
        assertEquals(over, LocationHour.of(parts))
    }

    @Test
    fun `a deleted sample is listed in the part that held it and the part stays full`() {
        val before = hourOf(samples = 5_001)
        val after = hourOf(samples = 5_001, deletedSamples = 1)

        val firstPartBefore = before.parts().first()
        val firstPartAfter = after.parts().first()

        assertEquals(5_000, firstPartBefore.observations.size + firstPartBefore.deletedSampleIds.size)
        assertEquals(5_000, firstPartAfter.observations.size + firstPartAfter.deletedSampleIds.size)
        assertEquals(listOf("sample-00000"), firstPartAfter.deletedSampleIds)
        assertEquals(4_999, firstPartAfter.observations.size)
        assertEquals(before.parts().last(), after.parts().last())
        assertEquals(2, after.parts().size)
    }

    @Test
    fun `activity changes fill parts of 500 counting deleted ones`() {
        val parts = hourOf(samples = 10, changes = 1_001, deletedChanges = 3).parts()

        assertEquals(3, parts.size)
        assertEquals(listOf(500, 500, 1), parts.map { it.activities.size + it.deletedTransitionIds.size })
        assertEquals(listOf(10, 0, 0), parts.map { it.observations.size })
        assertEquals(listOf(3, 0, 0), parts.map { it.deletedTransitionIds.size })
    }

    @Test
    fun `every part stays within the limits whatever mix of samples and deletions it holds`() {
        val hour = hourOf(samples = 12_345, changes = 1_234, deletedSamples = 6_000, deletedChanges = 700)

        hour.parts().forEach { part ->
            assertTrue(part.observations.size + part.deletedSampleIds.size <= 5_000, "part ${part.number}")
            assertTrue(part.activities.size + part.deletedTransitionIds.size <= 500, "part ${part.number}")
        }
        assertEquals(hour.forgettingDeletedTimes(), LocationHour.of(hour.parts()))
    }

    @Test
    fun `an hour too big for its parts is refused instead of cut short`() {
        val huge = hourOf(samples = LocationHour.MAX_PARTS * 5_000 + 1)

        assertFailsWith<SpaceRecordMappingException> { huge.parts() }
        assertEquals(LocationHour.MAX_PARTS, hourOf(samples = LocationHour.MAX_PARTS * 5_000).parts().size)
    }

    @Test
    fun `joining parts that were written at different times merges them`() {
        val hour = hourOf(samples = 5_001)
        val inSecondPart =
            hour
                .parts()[1]
                .observations
                .single()
                .id
        val deletedLater = hour.copy(samples = hour.samples.map { if (it.id == inSecondPart) it.copy(observation = null) else it })

        val joined = LocationHour.of(listOf(hour.parts()[0], deletedLater.parts()[1]))

        assertTrue(joined.samples.single { it.id == inSecondPart }.deleted)
        assertEquals(5_001, joined.samples.size)
    }

    @Test
    fun `merging is the union of the samples with every deletion winning`() {
        val mine = hourOf(samples = 5)
        val theirs = hourOf(samples = 3, deletedSamples = 2)

        val merged = mine.merge(theirs)

        assertEquals(mine.sampleIds(), merged.sampleIds())
        assertEquals(listOf(true, true, false, false, false), merged.samples.map { it.deleted })
    }

    @Test
    fun `merging gives the same hour whichever copy comes first and in any grouping`() {
        val a = hourOf(samples = 4, deletedSamples = 1)
        val b = hourOf(samples = 6, deletedSamples = 0, changes = 2)
        val c = hourOf(samples = 5, deletedSamples = 3, changes = 4, deletedChanges = 2)

        assertEquals(a.merge(b), b.merge(a))
        assertEquals(a.merge(a), a)
        assertEquals(a.merge(b).merge(c), a.merge(b.merge(c)))
        assertEquals(c.merge(b).merge(a), a.merge(b).merge(c))
    }

    @Test
    fun `a deleted sample never comes back whichever copy still holds it`() {
        val deleted = hourOf(samples = 3, deletedSamples = 3)
        val stillHolds = hourOf(samples = 3)

        assertTrue(deleted.merge(stillHolds).samples.all { it.deleted })
        assertTrue(stillHolds.merge(deleted).samples.all { it.deleted })
    }

    @Test
    fun `a sample both copies hold keeps this copy's more precise values`() {
        val precise = sampleAt(0).copy(accuracyMeters = 12.3456f)
        val stored = precise.copy(accuracyMeters = 12.35f)
        val mine = LocationHour(TEST_DEVICE, TEST_HOUR, listOf(HourSample(precise.id, precise.timestamp, precise)))
        val theirs = LocationHour(TEST_DEVICE, TEST_HOUR, listOf(HourSample(stored.id, stored.timestamp, stored)))

        assertEquals(
            precise,
            mine
                .merge(theirs)
                .samples
                .single()
                .observation,
        )
        assertEquals(
            stored,
            theirs
                .merge(mine)
                .samples
                .single()
                .observation,
        )
        assertEquals(LocationHour.atRecordPrecision(precise), stored)
    }

    @Test
    fun `two different samples under one id merge to the same sample on every device`() {
        val one = sampleAt(0).copy(latitude = 10.0)
        val other = sampleAt(0).copy(latitude = 20.0)
        val first = LocationHour(TEST_DEVICE, TEST_HOUR, listOf(HourSample(one.id, one.timestamp, one)))
        val second = LocationHour(TEST_DEVICE, TEST_HOUR, listOf(HourSample(other.id, other.timestamp, other)))

        assertEquals(first.merge(second).samples, second.merge(first).samples)
    }

    @Test
    fun `a deletion that arrived without its sample keeps the time of the sample once it is known`() {
        val sample = sampleAt(1_800)
        val known = LocationHour(TEST_DEVICE, TEST_HOUR, listOf(HourSample(sample.id, sample.timestamp)))
        val unknownTime = LocationHour(TEST_DEVICE, TEST_HOUR, listOf(HourSample(sample.id, TEST_HOUR)))

        assertEquals(
            sample.timestamp,
            known
                .merge(unknownTime)
                .samples
                .single()
                .at,
        )
        assertEquals(
            sample.timestamp,
            unknownTime
                .merge(known)
                .samples
                .single()
                .at,
        )
        assertNotEquals(
            TEST_HOUR,
            known
                .merge(unknownTime)
                .samples
                .single()
                .at,
        )
    }

    @Test
    fun `only copies of the same hour merge`() {
        assertFailsWith<SpaceRecordMappingException> { hourOf(1).merge(hourOf(1, hour = TEST_HOUR + 1.hours)) }
        assertFailsWith<SpaceRecordMappingException> { hourOf(1).merge(hourOf(1, device = "another-device")) }
    }
}
