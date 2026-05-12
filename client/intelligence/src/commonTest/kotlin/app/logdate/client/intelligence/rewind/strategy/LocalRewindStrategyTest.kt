package app.logdate.client.intelligence.rewind.strategy

import app.logdate.client.intelligence.curation.BeatBucketer
import app.logdate.client.intelligence.curation.CurationConfig
import app.logdate.client.intelligence.curation.CurationConfigProvider
import app.logdate.client.intelligence.curation.DiversitySelector
import app.logdate.client.intelligence.curation.MediaSignalExtractor
import app.logdate.client.intelligence.curation.MediaSignals
import app.logdate.client.intelligence.curation.PhotoHardFilter
import app.logdate.client.intelligence.curation.RejectReason
import app.logdate.client.intelligence.curation.RewindMediaCurator
import app.logdate.client.intelligence.curation.SignificanceScorer
import app.logdate.client.intelligence.narrative.RewindSequencer
import app.logdate.client.repository.media.IndexedMedia
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Exercises the user-visible payoff of the curation-strictness preference: the
 * `excludeScreenshots` flag the user picks in Rewind settings actually changes whether
 * a screenshot lands in a locally-built Rewind.
 */
class LocalRewindStrategyTest {
    private val baseTs = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    @Test
    fun `excludes screenshots when the user's preference says so`() =
        runTest {
            val screenshot = screenshotPhoto()
            val strategy = strategyFor(screenshot, includeScreenshots = false)
            val output = strategy.produce(inputWith(screenshot))

            assertTrue(
                output.curation.rejected.any {
                    it.media.uid == screenshot.uid &&
                        RejectReason.SCREENSHOT in it.reasons
                },
                "expected screenshot to be rejected when the user opted out",
            )
        }

    @Test
    fun `keeps screenshots when the user has opted them in`() =
        runTest {
            val screenshot = screenshotPhoto()
            val strategy = strategyFor(screenshot, includeScreenshots = true)
            val output = strategy.produce(inputWith(screenshot))

            assertEquals(
                false,
                output.curation.rejected.any { it.media.uid == screenshot.uid },
                "screenshot should survive the hard filter when the user opted in",
            )
        }

    private fun screenshotPhoto(): IndexedMedia.Image =
        IndexedMedia.Image(
            uid = Uuid.random(),
            uri = "test://screenshot",
            timestamp = baseTs,
            caption = null,
        )

    private fun strategyFor(
        screenshot: IndexedMedia.Image,
        includeScreenshots: Boolean,
    ): LocalRewindStrategy {
        val extractor =
            object : MediaSignalExtractor {
                override suspend fun extract(media: List<IndexedMedia>): Map<Uuid, MediaSignals> =
                    media.associate { item ->
                        item.uid to
                            if (item.uid == screenshot.uid) {
                                MediaSignals(isLikelyScreenshot = true)
                            } else {
                                MediaSignals()
                            }
                    }
            }
        val curator =
            RewindMediaCurator(
                signalExtractor = extractor,
                hardFilter = PhotoHardFilter(),
                scorer = SignificanceScorer(),
                bucketer = BeatBucketer(),
                selector = DiversitySelector(),
            )
        val configProvider =
            object : CurationConfigProvider {
                override suspend fun get(): CurationConfig = CurationConfig(excludeScreenshots = !includeScreenshots)
            }
        return LocalRewindStrategy(
            curator = curator,
            sequencer = RewindSequencer(),
            configProvider = configProvider,
        )
    }

    private fun inputWith(screenshot: IndexedMedia.Image): RewindInput =
        RewindInput(
            periodStart = baseTs,
            periodEnd = Instant.fromEpochMilliseconds(baseTs.toEpochMilliseconds() + 7L * 24L * 60L * 60L * 1000L),
            textEntries = emptyList(),
            media = listOf(screenshot),
            people = emptyList(),
            locationHistory = emptyList(),
            weekId = "2026-W18",
        )
}
