package app.logdate.client.domain.location

import app.logdate.client.domain.location.history.ApplyHistoryEdits
import app.logdate.client.domain.location.history.HistoryReconstructionParameters
import app.logdate.client.domain.location.history.ReconstructLocationDay
import app.logdate.client.domain.location.history.geographicDistance
import app.logdate.shared.model.location.HistoryCorrection
import app.logdate.shared.model.location.HistoryField
import app.logdate.shared.model.location.HistoryGap
import app.logdate.shared.model.location.JourneyLeg
import app.logdate.shared.model.location.LocationDayItem
import app.logdate.shared.model.location.LocationObservation
import app.logdate.shared.model.location.PlaceVisit
import app.logdate.shared.model.location.TravelMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ReconstructLocationDayTest {
    private val reconstruct = ReconstructLocationDay()
    private val base = Instant.parse("2026-09-27T16:00:00Z")

    @Test
    fun `a stay requires five minutes of evidence`() {
        val short = reconstruct(listOf(point(0), point(4))).single() as PlaceVisit
        assertFalse(short.confirmedStay)
        val stay = reconstruct(listOf(point(0), point(5))).single() as PlaceVisit
        assertTrue(stay.confirmedStay)
    }

    @Test
    fun `the minimum stay is a parameter`() {
        val shortStays = ReconstructLocationDay(parameters = HistoryReconstructionParameters(minimumStay = 3.minutes))
        assertTrue((shortStays(listOf(point(0), point(3))).single() as PlaceVisit).confirmedStay)
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
        val evidence = jitteryStay(Random(7), from = 0.minutes, until = 60.minutes) + stayAt(9_000.0, 75.minutes, 120.minutes)
        assertEquals(reconstruct(evidence), reconstruct(evidence.shuffled(Random(3)) + evidence.first()))
    }

    @Test
    fun `different devices never form one stay`() {
        val items = reconstruct(listOf(point(0), point(5).copy(deviceId = "other")))
        assertEquals(2, items.size)
        assertTrue(items.filterIsInstance<PlaceVisit>().none { it.confirmedStay })
    }

    @Test
    fun `poor accuracy cannot establish a confirmed stay`() {
        val items = reconstruct(listOf(point(0).copy(accuracyMeters = 800f), point(6).copy(accuracyMeters = 800f)))
        assertTrue(items.filterIsInstance<PlaceVisit>().none { it.confirmedStay })
    }

    @Test
    fun `returning to a place after a real trip is a separate visit`() {
        val items =
            reconstruct(
                stayAt(0.0, 0.minutes, 6.minutes) + stayAt(1_100.0, 8.minutes, 14.minutes) + stayAt(0.0, 16.minutes, 22.minutes),
            )
        val visits = items.filterIsInstance<PlaceVisit>()
        assertEquals(3, visits.size)
        assertTrue(visits.all { it.confirmedStay })
        assertEquals(3, visits.map { it.id }.distinct().size)
    }

    @Test
    fun `correcting a synthetic connector does not change either adjacent visit`() {
        val evidence = stayAt(0.0, 0.minutes, 6.minutes) + stayAt(1_100.0, 9.minutes, 15.minutes)
        val items = reconstruct(evidence)
        val connector = items.filterIsInstance<JourneyLeg>().single()
        val visits = items.filterIsInstance<PlaceVisit>()
        val corrected =
            ApplyHistoryEdits()(
                items,
                listOf(HistoryCorrection("edit", connector.evidenceIds.first(), HistoryField.END, (base + 8.minutes).toString())),
                emptyList(),
            )
        assertEquals(visits, corrected.filterIsInstance<PlaceVisit>())
        assertEquals(base + 8.minutes, corrected.filterIsInstance<JourneyLeg>().single().end)
        assertTrue(connector.evidenceIds.none { id -> visits.any { id in it.evidenceIds } })
        val otherDevice = reconstruct(evidence.map { it.copy(deviceId = "tablet") }).filterIsInstance<JourneyLeg>().single()
        assertTrue(connector.evidenceIds.none { it in otherDevice.evidenceIds })
        assertEquals(connector, reconstruct(evidence.reversed()).filterIsInstance<JourneyLeg>().single())
    }

    @Test
    fun `wifi jitter and a single spike stay one visit at home`() {
        repeat(20) { seed ->
            val samples =
                jitteryStay(Random(seed), from = 0.minutes, until = 180.minutes, every = 3.minutes)
                    .map { if (it.timestamp == base + 90.minutes) it.moved(eastMeters = 400.0, accuracy = 30f) else it }
            val items = reconstruct(samples)
            val visit = items.singleOrNull() as? PlaceVisit ?: fail("seed $seed: ${items.describe()}")
            assertTrue(visit.confirmedStay, "seed $seed")
            assertEquals(samples.size, visit.evidenceIds.size, "seed $seed")
            assertTrue(distanceFromHome(visit) < 40.0, "seed $seed")
        }
    }

    @Test
    fun `speed noise while still does not invent movement`() {
        val samples =
            jitteryStay(Random(11), from = 0.minutes, until = 30.minutes, every = 1.minutes, accuracies = 10..20)
                .mapIndexed { index, sample -> if (index % 3 == 0) sample.copy(speedMetersPerSecond = 1.5f) else sample }
        assertTrue((reconstruct(samples).single() as PlaceVisit).confirmedStay)
    }

    @Test
    fun `coarse and unrated fixes are kept as evidence without moving the visit`() {
        val stay = stayAt(0.0, 0.minutes, 40.minutes)
        val coarse = sample("coarse", 12.minutes, northMeters = 1_000.0, accuracy = 800f)
        val unrated = sample("unrated", 24.minutes, northMeters = 300.0, accuracy = null)
        val visit = reconstruct(stay + coarse + unrated).single() as PlaceVisit
        assertTrue(visit.confirmedStay)
        assertTrue("coarse" in visit.evidenceIds && "unrated" in visit.evidenceIds)
        assertEquals(stay.first().id, visit.evidenceIds.first())
        assertTrue(distanceFromHome(visit) < 5.0)
    }

    @Test
    fun `a recording made only of unrated fixes still shows stays and trips`() {
        val unrated =
            (stayAt(0.0, 0.minutes, 10.minutes) + drive(10.minutes, 15.minutes, fromMeters = 0.0) + stayAt(3_000.0, 15.minutes, 25.minutes))
                .map { it.copy(accuracyMeters = null) }
        val items = reconstruct(unrated)
        assertEquals(listOf(PlaceVisit::class, JourneyLeg::class, PlaceVisit::class), items.map { it::class })
        assertTrue(items.filterIsInstance<PlaceVisit>().all { it.confirmedStay })
    }

    @Test
    fun `unrated fixes shape a recording when they make up most of it`() {
        val unrated = (stayAt(0.0, 0.minutes, 30.minutes) + stayAt(2_000.0, 40.minutes, 70.minutes)).map { it.copy(accuracyMeters = null) }
        val rated = listOf(sample("rated", 15.minutes, accuracy = 20f))
        val visits = reconstruct(unrated + rated).filterIsInstance<PlaceVisit>()
        assertEquals(2, visits.size)
        assertTrue(visits.all { it.confirmedStay })
    }

    @Test
    fun `a brief drift away is absorbed into the stay`() {
        val samples =
            stayAt(0.0, 0.minutes, 40.minutes).map {
                if (it.timestamp in (base + 20.minutes)..(base + 22.minutes)) it.moved(northMeters = 250.0, accuracy = 20f) else it
            }
        assertEquals(1, reconstruct(samples).filterIsInstance<PlaceVisit>().size)
        assertTrue(reconstruct(samples).none { it is JourneyLeg })
    }

    @Test
    fun `a longer drift that never leaves the neighborhood joins the stays on either side`() {
        val samples =
            stayAt(0.0, 0.minutes, 60.minutes).map {
                if (it.timestamp in (base + 20.minutes)..(base + 28.minutes)) it.moved(northMeters = 200.0, accuracy = 20f) else it
            }
        val items = reconstruct(samples)
        assertTrue((items.single() as PlaceVisit).confirmedStay)
    }

    @Test
    fun `a drive between two places is one journey`() {
        val samples =
            stayAt(0.0, 0.minutes, 30.minutes) + drive(30.minutes, 45.minutes, fromMeters = 0.0) + stayAt(9_000.0, 45.minutes, 75.minutes)
        val items = reconstruct(samples)
        assertEquals(listOf(PlaceVisit::class, JourneyLeg::class, PlaceVisit::class), items.map { it::class })
        assertEquals(TravelMode.VEHICLE, (items[1] as JourneyLeg).mode)
        assertTrue(items.filterIsInstance<PlaceVisit>().all { it.confirmedStay })
    }

    @Test
    fun `a short stop at a light stays part of the drive`() {
        val samples =
            drive(0.minutes, 10.minutes, fromMeters = 0.0) +
                (0..5).map { sample("light-$it", 10.minutes + (it * 10).seconds, northMeters = 6_000.0, speed = 0f) } +
                drive(11.minutes, 20.minutes, fromMeters = 6_000.0)
        val items = reconstruct(samples)
        assertTrue(items.none { it is PlaceVisit })
        assertEquals(1, items.size)
    }

    @Test
    fun `a traffic jam is not a visit`() {
        val jam =
            (0..36).map {
                sample("jam-$it", 10.minutes + (it * 10).seconds, northMeters = 6_000.0 + it, speed = 0.5f, mode = TravelMode.VEHICLE)
            }
        val items = reconstruct(drive(0.minutes, 10.minutes, fromMeters = 0.0) + jam + drive(16.minutes, 25.minutes, fromMeters = 6_036.0))
        assertTrue(items.none { it is PlaceVisit })
    }

    @Test
    fun `wandering around a store is a visit`() {
        val random = Random(5)
        val samples =
            (0..40).map {
                sample(
                    "aisle-$it",
                    (it * 30).seconds,
                    northMeters = random.nextDouble(-60.0, 60.0),
                    eastMeters = random.nextDouble(-60.0, 60.0),
                    speed = 1f,
                    mode = TravelMode.WALKING,
                )
            }
        assertTrue((reconstruct(samples).single() as PlaceVisit).confirmedStay)
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
        latitude = HOME_LATITUDE + offset,
        longitude = HOME_LONGITUDE,
        accuracyMeters = 10f,
        activity = mode,
    )

    private fun sample(
        id: String,
        at: Duration,
        northMeters: Double = 0.0,
        eastMeters: Double = 0.0,
        accuracy: Float? = 10f,
        speed: Float? = null,
        mode: TravelMode = TravelMode.UNKNOWN,
    ) = LocationObservation(
        id = id,
        ownerId = "user",
        deviceId = "phone",
        timestamp = base + at,
        latitude = HOME_LATITUDE + northMeters / METERS_PER_DEGREE,
        longitude = HOME_LONGITUDE + eastMeters / (METERS_PER_DEGREE * cos(HOME_LATITUDE * PI / 180)),
        accuracyMeters = accuracy,
        speedMetersPerSecond = speed,
        activity = mode,
    )

    private fun LocationObservation.moved(
        northMeters: Double = 0.0,
        eastMeters: Double = 0.0,
        accuracy: Float? = accuracyMeters,
    ) = copy(
        latitude = latitude + northMeters / METERS_PER_DEGREE,
        longitude = longitude + eastMeters / (METERS_PER_DEGREE * cos(HOME_LATITUDE * PI / 180)),
        accuracyMeters = accuracy,
    )

    private fun stayAt(
        northMeters: Double,
        from: Duration,
        until: Duration,
        every: Duration = 1.minutes,
    ) = generateSequence(from) { it + every }
        .takeWhile { it <= until }
        .map { sample("stay-${northMeters.toInt()}-${it.inWholeSeconds}", it, northMeters = northMeters, mode = TravelMode.STILL) }
        .toList()

    /** Fixes scattered the way a balanced-power (Wi-Fi) fix scatters: within about the reported accuracy. */
    private fun jitteryStay(
        random: Random,
        from: Duration,
        until: Duration,
        every: Duration = 3.minutes,
        accuracies: IntRange = 30..90,
    ) = generateSequence(from) { it + every }
        .takeWhile { it <= until }
        .map {
            val accuracy = random.nextInt(accuracies.first, accuracies.last + 1).toFloat()
            val spread = accuracy / 1.5
            sample(
                "wifi-${it.inWholeSeconds}",
                it,
                northMeters = random.gaussian() * spread,
                eastMeters = random.gaussian() * spread,
                accuracy = accuracy,
            )
        }.toList()

    private fun drive(
        from: Duration,
        until: Duration,
        fromMeters: Double,
    ) = generateSequence(from) { it + 10.seconds }
        .takeWhile { it < until }
        .map {
            sample(
                "drive-${it.inWholeSeconds}",
                it,
                northMeters = fromMeters + (it - from).inWholeSeconds * 10.0,
                speed = 10f,
                mode = TravelMode.VEHICLE,
            )
        }.toList()

    private fun List<LocationDayItem>.describe() =
        joinToString("; ") {
            "${it::class.simpleName} ${(it.start - base).inWholeMinutes}-${(it.end - base).inWholeMinutes}m (${it.evidenceIds.size} samples)"
        }

    private fun distanceFromHome(visit: PlaceVisit) = geographicDistance(visit.latitude, visit.longitude, HOME_LATITUDE, HOME_LONGITUDE)

    private fun Random.gaussian(): Double {
        val u = nextDouble().coerceAtLeast(1e-9)
        val v = nextDouble()
        return sqrt(-2 * ln(u)) * cos(2 * PI * v)
    }

    private companion object {
        const val HOME_LATITUDE = 36.17
        const val HOME_LONGITUDE = -115.14
        const val METERS_PER_DEGREE = 111_320.0
    }
}
