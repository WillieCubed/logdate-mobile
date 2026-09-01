package app.logdate.client.intelligence.curation

import app.logdate.client.repository.media.IndexedMedia
import app.logdate.shared.model.StoryBeat
import app.logdate.shared.model.WeekNarrative
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Integration test for the whole curator pipeline: signal extraction → hard filter →
 * scorer → bucketer → diversity selector. Mirrors the plan's launch verification fixture:
 * a 50-photo set including a screenshot, a doc scan, a 5-photo burst, and a receipt.
 */
class RewindMediaCuratorTest {
    private val baseTs = Instant.fromEpochMilliseconds(1_700_000_000_000L)
    private val signalLookup: MutableMap<Uuid, MediaSignals> = mutableMapOf()

    private val curator =
        RewindMediaCurator(
            signalExtractor = SeededSignalExtractor(),
            hardFilter = PhotoHardFilter(),
            scorer = SignificanceScorer(),
            bucketer = BeatBucketer(),
            selector = DiversitySelector(),
        )

    @Test
    fun `mixed fixture drops screenshot doc scan and receipt and collapses the burst`() =
        runTest {
            // 50 regular photos spaced 30 minutes apart so the burst-collapse window never fires.
            val regulars =
                (0 until 50).map { idx ->
                    indexedImage(atMs = baseTs.toEpochMilliseconds() + idx * 30L * 60L * 1000L)
                }
            // 5-photo burst, all sharing the same burstGroupKey.
            val burst =
                (0 until 5).map { idx ->
                    indexedImage(
                        atMs = baseTs.toEpochMilliseconds() + idx * 1000L,
                        signals = MediaSignals(burstGroupKey = "burst-A", widthPx = 4032, heightPx = 3024),
                    )
                }
            val screenshot = indexedImage(signals = MediaSignals(isLikelyScreenshot = true))
            val docScan = indexedImage(signals = MediaSignals(isLikelyDocumentScan = true))
            val receipt = indexedImage(signals = MediaSignals(isLikelyDocumentScan = true))

            val all = regulars + burst + listOf(screenshot, docScan, receipt)

            val result =
                curator.curate(
                    allMedia = all,
                    narrative = null,
                    textEntries = emptyList(),
                    people = emptyList(),
                    locationHistory = emptyList(),
                    periodStart = baseTs,
                    periodEnd =
                        Instant.fromEpochMilliseconds(
                            baseTs.toEpochMilliseconds() + 50L * 60L * 60L * 1000L,
                        ),
                    config = CurationConfig(),
                )

            val rejectedUids = result.rejected.map { it.media.uid }.toSet()
            assertTrue(screenshot.uid in rejectedUids, "expected screenshot in rejected")
            assertTrue(docScan.uid in rejectedUids, "expected doc scan in rejected")
            assertTrue(receipt.uid in rejectedUids, "expected receipt in rejected")
            val burstRejects = burst.count { it.uid in rejectedUids }
            assertEquals(4, burstRejects, "expected exactly 4 burst members rejected; got $burstRejects")
        }

    @Test
    fun `empty media returns the EMPTY result without running the pipeline`() =
        runTest {
            val result =
                curator.curate(
                    allMedia = emptyList(),
                    narrative = null,
                    textEntries = emptyList(),
                    people = emptyList(),
                    locationHistory = emptyList(),
                    periodStart = baseTs,
                    periodEnd = baseTs,
                    config = CurationConfig(),
                )
            assertEquals(CurationResult.EMPTY, result)
        }

    @Test
    fun `every kept candidate has a significance score`() =
        runTest {
            val photos =
                (0 until 6).map { idx ->
                    indexedImage(atMs = baseTs.toEpochMilliseconds() + idx * 60L * 60L * 1000L)
                }
            val result =
                curator.curate(
                    allMedia = photos,
                    narrative = null,
                    textEntries = emptyList(),
                    people = emptyList(),
                    locationHistory = emptyList(),
                    periodStart = baseTs,
                    periodEnd = Instant.fromEpochMilliseconds(baseTs.toEpochMilliseconds() + 24L * 60L * 60L * 1000L),
                    config = CurationConfig(),
                )

            (result.perBeat.values.flatten() + result.freeAgents).forEach { candidate ->
                val sig = result.sigByMediaUid[candidate.media.uid]
                assertTrue(sig != null, "expected significance score for ${candidate.media.uid}")
            }
        }

    /**
     * The launch criteria name a specific scale: the caps must hold for a week with at least 200
     * photos in it. A cap that only holds for a handful of items is not a cap, and the failure it
     * would let through -- a Rewind that is a chronological photo dump -- is exactly what curation
     * exists to prevent.
     */
    @Test
    fun `caps hold for a week containing more than two hundred photos`() =
        runTest {
            val weekMillis = 7L * 24L * 60L * 60L * 1000L
            val photoCount = 240
            // Spread across the week so they land in many beats rather than collapsing as a burst.
            val photos =
                (0 until photoCount).map { idx ->
                    indexedImage(
                        atMs = baseTs.toEpochMilliseconds() + idx * (weekMillis / photoCount),
                        signals = cameraPhotoSignals(),
                    )
                }
            // The significance threshold is a separate rule with its own tests; admitting every
            // photo here is what puts the caps under the pressure this case exists to check.
            val config = CurationConfig()

            val result =
                curator.curate(
                    allMedia = photos,
                    // A real week has story beats -- local Rewinds detect them even with no AI.
                    // Without any, every photo is a free agent, and free agents deliberately do
                    // not count against the total cap, so the cap would not be under test at all.
                    narrative = narrativeCiting(photos.map { it.uid.toString() }),
                    textEntries = emptyList(),
                    people = emptyList(),
                    locationHistory = emptyList(),
                    periodStart = baseTs,
                    periodEnd = Instant.fromEpochMilliseconds(baseTs.toEpochMilliseconds() + weekMillis),
                    config = config,
                )

            val kept = result.perBeat.values.flatten()
            assertTrue(
                kept.size <= config.maxTotalMedia,
                "kept ${kept.size} of $photoCount, over the ${config.maxTotalMedia} total cap",
            )
            result.perBeat.forEach { (beat, items) ->
                assertTrue(
                    items.size <= config.maxItemsPerBeat + CITED_OVERFLOW_ALLOWANCE,
                    "beat $beat kept ${items.size}, over the ${config.maxItemsPerBeat} per-beat cap",
                )
            }
            assertTrue(kept.isNotEmpty(), "a week of 240 photos should still produce a Rewind")
        }

    /** Every kept item must be scored, at scale and not just for a handful. */
    @Test
    fun `every kept candidate is scored even at scale`() =
        runTest {
            val weekMillis = 7L * 24L * 60L * 60L * 1000L
            val photos =
                (0 until 240).map { idx ->
                    indexedImage(
                        atMs = baseTs.toEpochMilliseconds() + idx * (weekMillis / 240),
                        signals = cameraPhotoSignals(),
                    )
                }

            val result =
                curator.curate(
                    allMedia = photos,
                    narrative = narrativeCiting(photos.map { it.uid.toString() }),
                    textEntries = emptyList(),
                    people = emptyList(),
                    locationHistory = emptyList(),
                    periodStart = baseTs,
                    periodEnd = Instant.fromEpochMilliseconds(baseTs.toEpochMilliseconds() + weekMillis),
                    config = CurationConfig(),
                )

            val kept = result.perBeat.values.flatten() + result.freeAgents
            assertTrue(kept.isNotEmpty(), "nothing was kept, so this would assert nothing")
            kept.forEach { candidate ->
                assertTrue(
                    result.sigByMediaUid[candidate.media.uid] != null,
                    "no significance score for ${candidate.media.uid}",
                )
            }
        }

    /**
     * Signals for an ordinary camera photo.
     *
     * A default [MediaSignals] carries no dimensions, so the hard filter rejects it as below
     * [CurationConfig.minResolutionPx] before anything is scored -- a fixture built from defaults
     * curates to nothing and asserts nothing.
     */
    private fun cameraPhotoSignals() = MediaSignals(widthPx = 4032, heightPx = 3024)

    /** A narrative whose beats spread the given evidence across a week, so photos land in beats. */
    private fun narrativeCiting(evidenceIds: List<String>): WeekNarrative =
        WeekNarrative(
            themes = emptyList(),
            emotionalTone = "",
            storyBeats =
                evidenceIds.chunked((evidenceIds.size / BEATS_IN_FIXTURE).coerceAtLeast(1)).map { chunk ->
                    StoryBeat(
                        moment = "moment",
                        context = "context",
                        emotionalWeight = "neutral",
                        evidenceIds = chunk,
                    )
                },
            overallNarrative = "",
        )

    private fun indexedImage(
        atMs: Long = baseTs.toEpochMilliseconds(),
        signals: MediaSignals = MediaSignals(),
    ): IndexedMedia.Image {
        val media =
            IndexedMedia.Image(
                uid = Uuid.random(),
                uri = "test://photo",
                timestamp = Instant.fromEpochMilliseconds(atMs),
                caption = null,
            )
        signalLookup[media.uid] = signals
        return media
    }

    private inner class SeededSignalExtractor : MediaSignalExtractor {
        override suspend fun extract(media: List<IndexedMedia>): Map<Uuid, MediaSignals> =
            media.associate {
                it.uid to
                    (signalLookup[it.uid] ?: MediaSignals())
            }
    }

    private companion object {
        /** DiversitySelector lets cited items exceed the per-beat cap by up to two. */
        const val CITED_OVERFLOW_ALLOWANCE = 2

        /** How many beats the heavy-input fixture spreads its photos across. */
        const val BEATS_IN_FIXTURE = 7
    }
}
