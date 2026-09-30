package app.logdate.client.domain.location

import app.logdate.client.domain.location.history.ApplyHistoryEdits
import app.logdate.client.domain.location.history.ReconstructLocationDay
import app.logdate.shared.model.location.HistoryCorrection
import app.logdate.shared.model.location.HistoryField
import app.logdate.shared.model.location.HistoryGap
import app.logdate.shared.model.location.JourneyLeg
import app.logdate.shared.model.location.LocationObservation
import app.logdate.shared.model.location.PlaceVisit
import app.logdate.shared.model.location.TravelMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ReconstructLocationDayTest {
    private val reconstruct = ReconstructLocationDay()
    private val base = Instant.parse("2026-09-27T16:00:00Z")

    @Test
    fun `a stay requires two minutes of evidence`() {
        val short = reconstruct(listOf(point(0), point(1))).single() as PlaceVisit
        assertFalse(short.confirmedStay)
        val stay = reconstruct(listOf(point(0), point(2))).single() as PlaceVisit
        assertTrue(stay.confirmedStay)
    }

    @Test
    fun `a gap is not turned into a stay or route`() {
        val items = reconstruct(listOf(point(0), point(2), point(30)))
        assertEquals(3, items.size)
        assertEquals(base + 2.minutes, (items[1] as HistoryGap).start)
        assertEquals(base + 30.minutes, items[1].end)
    }

    @Test
    fun `walking across a neighborhood is a journey rather than a chained stay`() {
        val items = reconstruct((0..8).map { point(it, it * 0.0004, TravelMode.WALKING) })
        assertEquals(1, items.size)
        assertEquals(TravelMode.WALKING, (items.single() as JourneyLeg).mode)
        assertEquals(9, items.single().evidenceIds.size)
    }

    @Test
    fun `vehicle evidence does not claim driving and transitions split legs`() {
        val items =
            reconstruct(
                listOf(
                    point(0, 0.0, TravelMode.WALKING),
                    point(1, .001, TravelMode.WALKING),
                    point(2, .002, TravelMode.VEHICLE),
                    point(3, .004, TravelMode.VEHICLE),
                ),
            )
        assertEquals(listOf(TravelMode.WALKING, TravelMode.VEHICLE), items.filterIsInstance<JourneyLeg>().map { it.mode })
    }

    @Test
    fun `duplicates and input ordering do not change identities`() {
        val evidence = listOf(point(0), point(2), point(5, .01), point(7, .01))
        assertEquals(reconstruct(evidence), reconstruct(evidence.reversed() + evidence.first()))
    }

    @Test
    fun `different devices never form one stay`() {
        val items = reconstruct(listOf(point(0), point(2).copy(deviceId = "other")))
        assertEquals(2, items.size)
        assertTrue(items.filterIsInstance<PlaceVisit>().none { it.confirmedStay })
    }

    @Test
    fun `poor accuracy cannot establish a confirmed stay`() {
        val items = reconstruct(listOf(point(0).copy(accuracyMeters = 800f), point(3).copy(accuracyMeters = 800f)))
        assertTrue(items.filterIsInstance<PlaceVisit>().none { it.confirmedStay })
    }

    @Test
    fun `returning to a place is a separate visit`() {
        val items = reconstruct(listOf(point(0), point(2), point(4, .01), point(6, .01), point(8), point(10)))
        assertEquals(3, items.filterIsInstance<PlaceVisit>().size)
        assertEquals(
            3,
            items
                .filterIsInstance<PlaceVisit>()
                .map { it.id }
                .distinct()
                .size,
        )
    }

    @Test
    fun `correcting a synthetic connector does not change either adjacent visit`() {
        val evidence = listOf(point(0), point(2), point(5, .01), point(7, .01))
        val items = reconstruct(evidence)
        val connector = items.filterIsInstance<JourneyLeg>().single()
        val visits = items.filterIsInstance<PlaceVisit>()
        val corrected =
            ApplyHistoryEdits()(
                items,
                listOf(HistoryCorrection("edit", connector.evidenceIds.first(), HistoryField.END, (base + 4.minutes).toString())),
                emptyList(),
            )
        assertEquals(visits, corrected.filterIsInstance<PlaceVisit>())
        assertEquals(base + 4.minutes, corrected.filterIsInstance<JourneyLeg>().single().end)
        assertTrue(connector.evidenceIds.none { id -> visits.any { id in it.evidenceIds } })
        val otherDevice = reconstruct(evidence.map { it.copy(deviceId = "tablet") }).filterIsInstance<JourneyLeg>().single()
        assertTrue(connector.evidenceIds.none { it in otherDevice.evidenceIds })
        assertEquals(connector, reconstruct(evidence.reversed()).filterIsInstance<JourneyLeg>().single())
    }

    private fun point(
        minute: Int,
        offset: Double = 0.0,
        mode: TravelMode = TravelMode.STILL,
    ) = LocationObservation(
        id = "sample-$minute",
        ownerId = "user",
        deviceId = "phone",
        timestamp = base + minute.minutes,
        latitude = 36.17 + offset,
        longitude = -115.14,
        accuracyMeters = 10f,
        activity = mode,
    )
}
