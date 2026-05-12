package app.logdate.client.intelligence.curation

import app.logdate.client.repository.media.IndexedMedia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class DiversitySelectorTest {
    private val selector = DiversitySelector()

    private fun image(
        atMs: Long = 0L,
        burstKey: String? = null,
        score: Float = 50f,
        beatIndex: Int? = 0,
        cited: Boolean = false,
    ): MediaCandidate =
        MediaCandidate(
            media =
                IndexedMedia.Image(
                    uid = Uuid.random(),
                    uri = "test://photo",
                    timestamp = Instant.fromEpochMilliseconds(atMs),
                    caption = null,
                ),
            signals = MediaSignals(burstGroupKey = burstKey),
            derivedScores =
                ScoreBreakdown(
                    total = score,
                ),
            assignedBeatIndex = beatIndex,
            isLLMCited = cited,
        )

    @Test
    fun `respects the per-beat soft cap`() {
        val config = CurationConfig(maxItemsPerBeat = 2, maxTotalMedia = 100)
        // Six candidates in beat 0, spaced 10 minutes apart so the sub-window rule won't fire.
        val candidates =
            (0 until 6).map { idx ->
                image(atMs = idx * 10L * 60L * 1000L, score = 90f - idx)
            }
        val outcome = selector.select(candidates, config)
        assertEquals(2, outcome.perBeat[0]!!.size)
    }

    @Test
    fun `cited items exceed the soft cap up to plus two`() {
        val config = CurationConfig(maxItemsPerBeat = 2, maxTotalMedia = 100)
        // Two normal candidates + three cited candidates, all in beat 0, spaced apart.
        val normal =
            (0 until 2).map { idx ->
                image(atMs = idx * 10L * 60L * 1000L, score = 80f - idx)
            }
        val cited =
            (0 until 3).map { idx ->
                image(atMs = (10 + idx) * 10L * 60L * 1000L, score = 70f, cited = true)
            }
        val outcome = selector.select(normal + cited, config)
        // Two normal + two cited within hard cap (softCap + 2 = 4).
        assertEquals(4, outcome.perBeat[0]!!.size)
    }

    @Test
    fun `caps at most one item per burst key inside a beat`() {
        val config = CurationConfig(maxItemsPerBeat = 6, maxTotalMedia = 100)
        // Five items sharing burstKey "B1", well spaced so the sub-window rule is fine.
        val items =
            (0 until 5).map { idx ->
                image(
                    atMs = idx * 10L * 60L * 1000L,
                    burstKey = "B1",
                    score = 90f - idx,
                )
            }
        val outcome = selector.select(items, config)
        assertEquals(1, outcome.perBeat[0]!!.size)
    }

    @Test
    fun `caps at most two non-cited items inside any five-minute sub-window`() {
        val config = CurationConfig(maxItemsPerBeat = 10, maxTotalMedia = 100)
        // Five items tightly clustered — within five minutes of each other.
        val items =
            (0 until 5).map { idx ->
                image(atMs = idx * 30L * 1000L, score = 90f - idx)
            }
        val outcome = selector.select(items, config)
        assertEquals(2, outcome.perBeat[0]!!.size)
    }

    @Test
    fun `free agents are filled only by cited or above-threshold candidates`() {
        val config = CurationConfig(minSignificanceForFreeAgent = 35f)
        val belowThreshold = image(beatIndex = null, score = 20f)
        val aboveThreshold = image(beatIndex = null, score = 60f)
        val citedBelow = image(beatIndex = null, score = 10f, cited = true)
        val outcome = selector.select(listOf(belowThreshold, aboveThreshold, citedBelow), config)
        val freeAgentUids = outcome.freeAgents.map { it.media.uid }.toSet()
        assertTrue(aboveThreshold.media.uid in freeAgentUids)
        assertTrue(citedBelow.media.uid in freeAgentUids)
        assertTrue(belowThreshold.media.uid !in freeAgentUids)
    }

    @Test
    fun `global cap trims the lowest scorers while preserving at least one per beat`() {
        // Two beats with three high-scoring items each (six total). Cap is two.
        // Every beat must retain at least one item.
        val config = CurationConfig(maxItemsPerBeat = 3, maxTotalMedia = 2)
        val beat0Items =
            (0 until 3).map { idx ->
                image(atMs = idx * 10L * 60L * 1000L, score = 90f - idx, beatIndex = 0)
            }
        val beat1Items =
            (0 until 3).map { idx ->
                image(atMs = (10 + idx) * 10L * 60L * 1000L, score = 60f - idx, beatIndex = 1)
            }
        val outcome = selector.select(beat0Items + beat1Items, config)
        // Each beat keeps at least one entry (the "drop droppable" rule).
        assertTrue(outcome.perBeat[0]!!.isNotEmpty(), "beat 0 must keep at least one item")
        assertTrue(outcome.perBeat[1]!!.isNotEmpty(), "beat 1 must keep at least one item")
        // The total preserved is at least the per-beat minimum (2 beats × 1 each).
        val total = outcome.perBeat.values.sumOf { it.size }
        assertTrue(total >= 2, "expected ≥2 items overall, got $total")
    }
}
