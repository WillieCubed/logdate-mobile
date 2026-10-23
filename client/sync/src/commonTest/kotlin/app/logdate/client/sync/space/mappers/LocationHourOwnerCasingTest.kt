package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.shared.model.location.ActivityObservation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class LocationHourOwnerCasingTest {
    private val raw = "A8A3400A-9F3C-4FCA-9A7A-7C8CBE5CA24E"
    private val hour = Instant.parse("2026-10-19T09:00:00Z")
    private val device = "separate-device"

    @Test
    fun `sample conflict choice depends on its recorded fields rather than account letter casing`() {
        val first = sampleAt(1, hour, device).copy(latitude = 1.0, ownerId = raw)
        val second = first.copy(latitude = 0.0, ownerId = raw.lowercase())
        val left = LocationHour(device, hour, listOf(HourSample(first.id, first.timestamp, first)))
        val right = LocationHour(device, hour, listOf(HourSample(second.id, second.timestamp, second)))
        assertEquals(
            first,
            left
                .merge(right)
                .samples
                .single()
                .observation,
        )
        assertEquals(
            first,
            right
                .merge(left)
                .samples
                .single()
                .observation,
        )
        assertEquals(raw, first.ownerId)
        assertEquals(raw.lowercase(), second.ownerId)
    }

    @Test
    fun `activity conflict choice preserves recorded fields across account letter casing`() {
        val first = ActivityObservation("activity", raw, device, hour, "WALKING", "ENTER")
        val second = first.copy(ownerId = raw.lowercase(), activityType = "STILL")
        val left = LocationHour(device, hour, transitions = listOf(HourTransition(first.id, hour, first)))
        val right = LocationHour(device, hour, transitions = listOf(HourTransition(second.id, hour, second)))
        assertEquals(
            first,
            left
                .merge(right)
                .transitions
                .single()
                .activity,
        )
        assertEquals(
            first,
            right
                .merge(left)
                .transitions
                .single()
                .activity,
        )
    }

    @Test
    fun `history belonging to different accounts cannot be merged into one hour`() {
        val first = sampleAt(1, hour, device).copy(ownerId = raw)
        val second = first.copy(ownerId = "b930bf49-d1e9-4a65-b84b-8e74af45012b")
        val left = LocationHour(device, hour, listOf(HourSample(first.id, first.timestamp, first)))
        val right = LocationHour(device, hour, listOf(HourSample(second.id, second.timestamp, second)))
        assertFailsWith<SpaceRecordMappingException> { left.merge(right) }
    }
}
