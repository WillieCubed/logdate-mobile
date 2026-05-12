package app.logdate.client.intelligence.curation

import app.logdate.client.repository.media.IndexedMedia
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
}
