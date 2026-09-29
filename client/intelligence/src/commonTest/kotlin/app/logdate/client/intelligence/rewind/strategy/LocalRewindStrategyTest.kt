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
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.shared.model.RewindContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
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

    @Test
    fun `sparse journal week renders its real entry with a valid source`() =
        runTest {
            val note =
                JournalNote.Text(
                    creationTimestamp = baseTs,
                    lastUpdated = baseTs,
                    content = "I finally finished the project this week.",
                )
            val strategy = strategyFor(screenshotPhoto(), includeScreenshots = false)
            val output = strategy.produce(inputWith(emptyList(), listOf(note)))

            assertEquals(
                listOf(note.uid),
                output.content.filterIsInstance<RewindContent.TextNote>().map { it.sourceId },
            )
            assertTrue(output.narrative.storyBeats.any { note.uid.toString() in it.evidenceIds })
        }

    @Test
    fun `media heavy week keeps selected panels tied to indexed media`() =
        runTest {
            val media =
                (0 until 25).map { index ->
                    IndexedMedia.Image(
                        uid = Uuid.random(),
                        uri = "test://photo/$index",
                        timestamp = baseTs + index.hours,
                        caption = null,
                    )
                }
            val output = strategyFor(screenshotPhoto(), includeScreenshots = true).produce(inputWith(media))
            val imagePanels = output.content.filterIsInstance<RewindContent.Image>()

            assertTrue(imagePanels.isNotEmpty())
            assertTrue(imagePanels.size <= 20)
            assertTrue(
                imagePanels.all { panel -> media.any { it.uid == panel.sourceId && it.uri == panel.uri } },
            )
            assertTrue(imagePanels.all { it.significanceScore != null })
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

    private fun inputWith(screenshot: IndexedMedia.Image): RewindInput = inputWith(listOf(screenshot))

    private fun inputWith(
        media: List<IndexedMedia>,
        textEntries: List<JournalNote.Text> = emptyList(),
    ): RewindInput =
        RewindInput(
            periodStart = baseTs,
            periodEnd = Instant.fromEpochMilliseconds(baseTs.toEpochMilliseconds() + 7L * 24L * 60L * 60L * 1000L),
            textEntries = textEntries,
            media = media,
            people = emptyList(),
            locationHistory = emptyList(),
            weekId = "2026-W18",
        )
}
