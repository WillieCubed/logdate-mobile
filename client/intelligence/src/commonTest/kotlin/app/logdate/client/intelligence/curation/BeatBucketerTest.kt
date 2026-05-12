package app.logdate.client.intelligence.curation

import app.logdate.client.repository.media.IndexedMedia
import app.logdate.shared.model.StoryBeat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

class BeatBucketerTest {
    private val bucketer = BeatBucketer()

    private val periodStart = Instant.fromEpochMilliseconds(0L)
    private val periodEnd = Instant.fromEpochMilliseconds(7L * 24L * 60L * 60L * 1000L) // one week

    private fun image(
        uid: Uuid = Uuid.random(),
        atMs: Long,
    ): MediaCandidate =
        MediaCandidate(
            media =
                IndexedMedia.Image(
                    uid = uid,
                    uri = "test://photo",
                    timestamp = Instant.fromEpochMilliseconds(atMs),
                    caption = null,
                ),
            signals = MediaSignals(),
        )

    private fun beat(
        evidenceIds: List<String> = emptyList(),
        moment: String = "m",
    ): StoryBeat =
        StoryBeat(
            moment = moment,
            context = "c",
            emotionalWeight = "neutral",
            evidenceIds = evidenceIds,
        )

    @Test
    fun `empty inputs return candidates untouched`() {
        val candidate = image(atMs = 0L)
        val outcome = bucketer.bucket(listOf(candidate), beats = emptyList(), textEntries = emptyList(), periodStart, periodEnd)
        assertNull(outcome.single().assignedBeatIndex)
    }

    @Test
    fun `candidate inside an anchored window is assigned to that beat`() {
        val anchor = image(atMs = 2.hours.inWholeMilliseconds * 12L)
        // Beat cites the anchor — window is anchor ±2h.
        val beat0 = beat(evidenceIds = listOf(anchor.media.uid.toString()))
        val nearby = image(atMs = anchor.media.timestamp.toEpochMilliseconds() + 30L * 60L * 1000L) // +30min
        val outcome =
            bucketer.bucket(
                candidates = listOf(anchor, nearby),
                beats = listOf(beat0),
                textEntries = emptyList(),
                periodStart = periodStart,
                periodEnd = periodEnd,
            )
        outcome.forEach { assertEquals(0, it.assignedBeatIndex) }
    }

    @Test
    fun `candidate well outside every anchored window becomes a free agent`() {
        val anchor = image(atMs = 2.hours.inWholeMilliseconds * 12L)
        val beat0 = beat(evidenceIds = listOf(anchor.media.uid.toString()))
        // Three days later → outside both the ±2h anchored window and the single-beat fallback.
        val outsider = image(atMs = anchor.media.timestamp.toEpochMilliseconds() + 3L * 24L * 60L * 60L * 1000L)
        // But anchor is still inside the anchored window so it stays assigned.
        val outcome =
            bucketer.bucket(
                candidates = listOf(anchor, outsider),
                beats = listOf(beat0),
                textEntries = emptyList(),
                periodStart = periodStart,
                periodEnd =
                    Instant.fromEpochMilliseconds(
                        anchor.media.timestamp.toEpochMilliseconds() + 3L * 24L * 60L * 60L * 1000L + 60_000L,
                    ),
            )
        assertEquals(0, outcome.first { it.media.uid == anchor.media.uid }.assignedBeatIndex)
        assertNull(outcome.first { it.media.uid == outsider.media.uid }.assignedBeatIndex)
    }

    @Test
    fun `unanchored beats get an evenly-split slice of the period`() {
        // Three beats, none with resolvable evidence. The period is one week — each beat
        // should own ~one-third of it, starting at the period start.
        val beats = listOf(beat(), beat(), beat())
        val weekMs = periodEnd.toEpochMilliseconds() - periodStart.toEpochMilliseconds()
        // Pick three candidates landing inside slice 0, slice 1, slice 2.
        val sliceMs = weekMs / 3L
        val a = image(atMs = sliceMs / 4L) // first third
        val b = image(atMs = sliceMs + sliceMs / 2L) // middle third
        val c = image(atMs = 2L * sliceMs + sliceMs / 2L) // last third
        val outcome =
            bucketer.bucket(
                candidates = listOf(a, b, c),
                beats = beats,
                textEntries = emptyList(),
                periodStart = periodStart,
                periodEnd = periodEnd,
            )
        assertEquals(0, outcome.first { it.media.uid == a.media.uid }.assignedBeatIndex)
        assertEquals(1, outcome.first { it.media.uid == b.media.uid }.assignedBeatIndex)
        assertEquals(2, outcome.first { it.media.uid == c.media.uid }.assignedBeatIndex)
    }

    @Test
    fun `when two windows overlap an explicit citation wins over centerline distance`() {
        // Beat 0 anchor is at t=12h. Beat 1 anchor is at t=14h. The candidate at t=13h is
        // inside both ±2h windows. Beat 1 explicitly cites the candidate — Beat 1 wins.
        val sharedUid = Uuid.random()
        val candidate = image(uid = sharedUid, atMs = 13.hours.inWholeMilliseconds)
        val anchor0 = image(atMs = 12.hours.inWholeMilliseconds)
        val anchor1 = image(atMs = 14.hours.inWholeMilliseconds)
        val beats =
            listOf(
                beat(evidenceIds = listOf(anchor0.media.uid.toString())),
                beat(evidenceIds = listOf(anchor1.media.uid.toString(), sharedUid.toString())),
            )
        val outcome =
            bucketer.bucket(
                candidates = listOf(anchor0, anchor1, candidate),
                beats = beats,
                textEntries = emptyList(),
                periodStart = periodStart,
                periodEnd = periodEnd,
            )
        assertEquals(1, outcome.first { it.media.uid == sharedUid }.assignedBeatIndex)
    }
}
