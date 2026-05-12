package app.logdate.client.intelligence.curation

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import app.logdate.shared.model.Person
import app.logdate.shared.model.StoryBeat
import app.logdate.shared.model.WeekNarrative
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SignificanceScorerTest {
    private val scorer = SignificanceScorer()

    private val baseTs = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    private fun image(
        uid: Uuid = Uuid.random(),
        atMs: Long = baseTs.toEpochMilliseconds(),
        signals: MediaSignals = MediaSignals(),
    ): MediaCandidate =
        MediaCandidate(
            media =
                IndexedMedia.Image(
                    uid = uid,
                    uri = "test://photo/$uid",
                    timestamp = Instant.fromEpochMilliseconds(atMs),
                    caption = null,
                ),
            signals = signals,
        )

    private fun video(
        duration: kotlin.time.Duration,
        signals: MediaSignals = MediaSignals(),
    ): MediaCandidate =
        MediaCandidate(
            media =
                IndexedMedia.Video(
                    uid = Uuid.random(),
                    uri = "test://video",
                    timestamp = baseTs,
                    duration = duration,
                    caption = null,
                ),
            signals = signals,
        )

    private fun textEntry(
        content: String,
        atMs: Long,
    ): JournalNote.Text =
        JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = Instant.fromEpochMilliseconds(atMs),
            lastUpdated = Instant.fromEpochMilliseconds(atMs),
            content = content,
        )

    private fun location(
        lat: Double,
        lon: Double,
        atMs: Long = baseTs.toEpochMilliseconds(),
    ): LocationHistoryItem =
        LocationHistoryItem(
            userId = "u",
            deviceId = "d",
            timestamp = Instant.fromEpochMilliseconds(atMs),
            location = Location(lat, lon, LocationAltitude(0.0, AltitudeUnit.METERS)),
            confidence = 1f,
            isGenuine = true,
        )

    private fun narrative(beatEvidenceIds: List<String>): WeekNarrative =
        WeekNarrative(
            themes = emptyList(),
            emotionalTone = "",
            storyBeats =
                listOf(
                    StoryBeat(
                        moment = "m",
                        context = "c",
                        emotionalWeight = "neutral",
                        evidenceIds = beatEvidenceIds,
                    ),
                ),
            overallNarrative = "",
        )

    @Test
    fun `null narrative and empty context yields zeroes`() {
        val candidate = image()
        val scored =
            scorer.score(
                candidates = listOf(candidate),
                narrative = null,
                textEntries = emptyList(),
                people = emptyList(),
                locationHistory = emptyList(),
            )
        val breakdown = scored.single().derivedScores!!
        assertEquals(0f, breakdown.narrativeEvidence)
        assertEquals(0f, breakdown.peopleProximity)
        assertEquals(0f, breakdown.locationNovelty)
        assertEquals(0f, breakdown.journalProximity)
        assertEquals(0f, breakdown.mediaIntrinsic)
        // Single candidate is a cluster of size 1 → "lonely shot" branch contributes 2.
        assertEquals(2f, breakdown.timeClusterDensity)
        assertEquals(2f, breakdown.total)
        assertEquals(false, scored.single().isLLMCited)
    }

    @Test
    fun `LLM cited evidence adds the flat 40 floor`() {
        val cited = image()
        val notCited = image()
        val scored =
            scorer.score(
                candidates = listOf(cited, notCited),
                narrative = narrative(listOf(cited.media.uid.toString())),
                textEntries = emptyList(),
                people = emptyList(),
                locationHistory = emptyList(),
            )
        val citedBreakdown = scored.first { it.media.uid == cited.media.uid }.derivedScores!!
        val uncitedBreakdown = scored.first { it.media.uid == notCited.media.uid }.derivedScores!!
        assertEquals(40f, citedBreakdown.narrativeEvidence)
        assertEquals(0f, uncitedBreakdown.narrativeEvidence)
        assertTrue(scored.first { it.media.uid == cited.media.uid }.isLLMCited)
    }

    @Test
    fun `people proximity awards 3 points per distinct named person in window`() {
        val photoTs = baseTs.toEpochMilliseconds()
        val entries =
            listOf(
                // Two distinct names within ±2h.
                textEntry("Coffee with Sarah and Alex", photoTs - 30.minutes.inWholeMilliseconds),
                // Sarah again — should not double-count.
                textEntry("Saw Sarah", photoTs + 10.minutes.inWholeMilliseconds),
                // Outside the window — ignored.
                textEntry("Dinner with Morgan", photoTs - 4 * 60.minutes.inWholeMilliseconds),
            )
        val people =
            listOf(
                Person(name = "Sarah"),
                Person(name = "Alex"),
                Person(name = "Morgan"),
            )
        val scored =
            scorer.score(
                candidates = listOf(image(atMs = photoTs)),
                narrative = null,
                textEntries = entries,
                people = people,
                locationHistory = emptyList(),
            )
        assertEquals(6f, scored.single().derivedScores!!.peopleProximity)
    }

    @Test
    fun `people proximity is zero when names are not within the window`() {
        val photoTs = baseTs.toEpochMilliseconds()
        val entries =
            listOf(
                textEntry("Saw Sarah", photoTs - 4 * 60.minutes.inWholeMilliseconds),
            )
        val scored =
            scorer.score(
                candidates = listOf(image(atMs = photoTs)),
                narrative = null,
                textEntries = entries,
                people = listOf(Person(name = "Sarah")),
                locationHistory = emptyList(),
            )
        assertEquals(0f, scored.single().derivedScores!!.peopleProximity)
    }

    @Test
    fun `location novelty rewards brand-new cells and penalises frequent ones`() {
        // Build a location history where (10.0, 20.0) has been visited many times.
        val frequent = List(40) { location(lat = 10.0, lon = 20.0) }
        val novelCandidate = image(signals = MediaSignals(latitude = 50.0, longitude = -100.0))
        val rareCandidate = image(signals = MediaSignals(latitude = 41.0, longitude = -71.0))
        val rareSamples =
            listOf(
                location(lat = 41.0, lon = -71.0),
                location(lat = 41.0, lon = -71.0),
            )
        val frequentCandidate = image(signals = MediaSignals(latitude = 10.0, longitude = 20.0))
        val scored =
            scorer.score(
                candidates = listOf(novelCandidate, rareCandidate, frequentCandidate),
                narrative = null,
                textEntries = emptyList(),
                people = emptyList(),
                locationHistory = frequent + rareSamples,
            )
        val byUid = scored.associateBy { it.media.uid }
        assertEquals(15f, byUid[novelCandidate.media.uid]!!.derivedScores!!.locationNovelty)
        assertEquals(8f, byUid[rareCandidate.media.uid]!!.derivedScores!!.locationNovelty)
        assertEquals(-2f, byUid[frequentCandidate.media.uid]!!.derivedScores!!.locationNovelty)
    }

    @Test
    fun `location novelty is zero when the candidate has no coordinates`() {
        val scored =
            scorer.score(
                candidates = listOf(image()),
                narrative = null,
                textEntries = emptyList(),
                people = emptyList(),
                locationHistory = listOf(location(lat = 0.0, lon = 0.0)),
            )
        assertEquals(0f, scored.single().derivedScores!!.locationNovelty)
    }

    @Test
    fun `time cluster density peaks for 3 to 7 shots inside the 15 minute window`() {
        val anchor = baseTs.toEpochMilliseconds()
        // 5 shots within 15min — peak band.
        val cluster =
            (0 until 5).map { idx ->
                image(atMs = anchor + idx * 60_000L)
            }
        val scored =
            scorer.score(
                candidates = cluster,
                narrative = null,
                textEntries = emptyList(),
                people = emptyList(),
                locationHistory = emptyList(),
            )
        scored.forEach {
            assertEquals(10f, it.derivedScores!!.timeClusterDensity)
        }
    }

    @Test
    fun `time cluster density drops back down for huge clusters`() {
        val anchor = baseTs.toEpochMilliseconds()
        // 20 shots within 15min — outside the sweet spot.
        val cluster =
            (0 until 20).map { idx ->
                image(atMs = anchor + idx * 30_000L)
            }
        val scored =
            scorer.score(
                candidates = cluster,
                narrative = null,
                textEntries = emptyList(),
                people = emptyList(),
                locationHistory = emptyList(),
            )
        // Middle-of-cluster items see the full cluster on both sides.
        val middle = scored[10]
        assertEquals(1f, middle.derivedScores!!.timeClusterDensity)
    }

    @Test
    fun `journal proximity stratifies by minutes-to-nearest-entry`() {
        val photoTs = baseTs.toEpochMilliseconds()
        val veryNearEntries = listOf(textEntry("nearby", photoTs + 10.minutes.inWholeMilliseconds))
        val twoHourEntries = listOf(textEntry("an hour out", photoTs + 60.minutes.inWholeMilliseconds))
        val sixHourEntries = listOf(textEntry("morning", photoTs - 3 * 60.minutes.inWholeMilliseconds))
        val farEntries = listOf(textEntry("yesterday", photoTs - 24 * 60.minutes.inWholeMilliseconds))

        fun journalScore(entries: List<JournalNote.Text>): Float =
            scorer
                .score(
                    candidates = listOf(image(atMs = photoTs)),
                    narrative = null,
                    textEntries = entries,
                    people = emptyList(),
                    locationHistory = emptyList(),
                ).single()
                .derivedScores!!
                .journalProximity

        assertEquals(20f, journalScore(veryNearEntries))
        assertEquals(10f, journalScore(twoHourEntries))
        assertEquals(3f, journalScore(sixHourEntries))
        assertEquals(0f, journalScore(farEntries))
    }

    @Test
    fun `intrinsic bonus rewards high-resolution media and panel-friendly clips`() {
        val hires = image(signals = MediaSignals(widthPx = 4032, heightPx = 3024))
        val lores = image(signals = MediaSignals(widthPx = 200, heightPx = 200))
        val cleanClip = video(duration = 10.seconds)
        val screenRecord = video(duration = 600.seconds)
        val scored =
            scorer.score(
                candidates = listOf(hires, lores, cleanClip, screenRecord),
                narrative = null,
                textEntries = emptyList(),
                people = emptyList(),
                locationHistory = emptyList(),
            )
        val byUid = scored.associateBy { it.media.uid }
        assertEquals(5f, byUid[hires.media.uid]!!.derivedScores!!.mediaIntrinsic)
        assertEquals(0f, byUid[lores.media.uid]!!.derivedScores!!.mediaIntrinsic)
        assertEquals(5f, byUid[cleanClip.media.uid]!!.derivedScores!!.mediaIntrinsic)
        assertEquals(-10f, byUid[screenRecord.media.uid]!!.derivedScores!!.mediaIntrinsic)
    }

    @Test
    fun `total is clamped to the 0 to 100 range`() {
        // Stack every positive signal we can: cited, max people, novel location, 5-cluster, very-near journal, hi-res.
        val photoTs = baseTs.toEpochMilliseconds()
        val candidates =
            (0 until 5).map { idx ->
                image(
                    atMs = photoTs + idx * 60_000L,
                    signals = MediaSignals(latitude = 51.5, longitude = -0.1, widthPx = 4032, heightPx = 3024),
                )
            }
        val cited = candidates.first()
        val scored =
            scorer.score(
                candidates = candidates,
                narrative = narrative(listOf(cited.media.uid.toString())),
                textEntries =
                    listOf(
                        textEntry("Coffee with Sarah", photoTs),
                        textEntry("Alex came too", photoTs),
                    ),
                people = listOf(Person(name = "Sarah"), Person(name = "Alex")),
                locationHistory = emptyList(),
            )
        val citedBreakdown = scored.first { it.media.uid == cited.media.uid }.derivedScores!!
        assertTrue(citedBreakdown.total <= 100f, "expected total clamped to ≤100 but got ${citedBreakdown.total}")
        assertTrue(citedBreakdown.total >= 0f)
    }
}
