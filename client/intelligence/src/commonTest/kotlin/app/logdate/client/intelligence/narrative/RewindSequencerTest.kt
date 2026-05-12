package app.logdate.client.intelligence.narrative

import app.logdate.client.intelligence.curation.CurationResult
import app.logdate.client.intelligence.curation.MediaCandidate
import app.logdate.client.intelligence.curation.MediaSignals
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.shared.model.RewindContent
import app.logdate.shared.model.StoryBeat
import app.logdate.shared.model.WeekNarrative
import app.logdate.shared.model.WeekStatsSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RewindSequencerTest {
    private val sequencer = RewindSequencer()
    private val emptyStats = WeekStatsSnapshot(photoCount = 0, textNoteCount = 0, distinctLocations = 0, distinctPeople = 0)

    @Test
    fun sequence_createsOpeningContext() {
        val narrative = narrativeOf(overallNarrative = "You explored the coast. It was wonderful.")

        val result =
            sequencer.sequence(
                narrative = narrative,
                curation = CurationResult.EMPTY,
                textEntries = emptyList(),
                people = emptyList(),
                weather = null,
                locationPath = emptyList(),
                stats = emptyStats,
                activities = emptyList(),
            )

        assertTrue(result.isNotEmpty())
        val firstPanel = assertIs<RewindContent.NarrativeContext>(result.first())
        assertEquals("You explored the coast.", firstPanel.contextText)
    }

    @Test
    fun sequence_createsResolution() {
        val narrative = narrativeOf(overallNarrative = "You explored the coast. It was wonderful.")

        val result =
            sequencer.sequence(
                narrative = narrative,
                curation = CurationResult.EMPTY,
                textEntries = emptyList(),
                people = emptyList(),
                weather = null,
                locationPath = emptyList(),
                stats = emptyStats,
                activities = emptyList(),
            )

        assertTrue(result.isNotEmpty())
        val lastPanel = assertIs<RewindContent.NarrativeContext>(result.last())
        assertEquals("It was wonderful.", lastPanel.contextText)
    }

    @Test
    fun sequence_includesStoryBeatsWithEvidence() {
        val textEntry =
            JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = Instant.parse("2024-07-22T10:00:00Z"),
                lastUpdated = Instant.parse("2024-07-22T10:00:00Z"),
                content = "Found the hidden beach!",
            )

        val narrative =
            narrativeOf(
                storyBeats =
                    listOf(
                        StoryBeat(
                            moment = "Found the hidden beach",
                            context = "Long-anticipated goal",
                            emotionalWeight = "triumphant",
                            evidenceIds = listOf(textEntry.uid.toString()),
                        ),
                    ),
                overallNarrative = "You found the beach. Amazing.",
            )

        val result =
            sequencer.sequence(
                narrative = narrative,
                curation = CurationResult.EMPTY,
                textEntries = listOf(textEntry),
                people = emptyList(),
                weather = null,
                locationPath = emptyList(),
                stats = emptyStats,
                activities = emptyList(),
            )

        // Should have: opening context + text evidence + resolution
        assertTrue(result.size >= 3)

        // Find the text note panel
        val textPanel = assertIs<RewindContent.TextNote>(result.find { it is RewindContent.TextNote })
        assertEquals(textEntry.content, textPanel.content)
    }

    @Test
    fun sequence_includesMediaEvidence() {
        val imageMedia =
            IndexedMedia.Image(
                uid = Uuid.random(),
                uri = "file:///beach.jpg",
                timestamp = Instant.parse("2024-07-22T18:00:00Z"),
                caption = "Sunset at the beach",
            )

        val videoMedia =
            IndexedMedia.Video(
                uid = Uuid.random(),
                uri = "file:///beach.mp4",
                timestamp = Instant.parse("2024-07-22T18:30:00Z"),
                caption = "Wave action",
                duration = 30.seconds,
            )

        val narrative =
            narrativeOf(
                storyBeats =
                    listOf(
                        StoryBeat(
                            moment = "Beach memories",
                            context = "Captured the moment",
                            emotionalWeight = "happy",
                            evidenceIds = listOf(imageMedia.uid.toString(), videoMedia.uid.toString()),
                        ),
                    ),
                overallNarrative = "You captured beautiful beach moments.",
            )

        val result =
            sequencer.sequence(
                narrative = narrative,
                curation = curationOf(listOf(imageMedia, videoMedia), narrative.storyBeats),
                textEntries = emptyList(),
                people = emptyList(),
                weather = null,
                locationPath = emptyList(),
                stats = emptyStats,
                activities = emptyList(),
            )

        val imagePanel = assertIs<RewindContent.Image>(result.find { it is RewindContent.Image })
        val videoPanel = assertIs<RewindContent.Video>(result.find { it is RewindContent.Video })

        assertEquals(imageMedia.uri, imagePanel.uri)
        assertEquals(videoMedia.uri, videoPanel.uri)
    }

    @Test
    fun sequence_createsTransitionsBetweenBeats() {
        val textEntry1 =
            JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = Instant.parse("2024-07-22T10:00:00Z"),
                lastUpdated = Instant.parse("2024-07-22T10:00:00Z"),
                content = "Morning hike was refreshing",
            )

        val textEntry2 =
            JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = Instant.parse("2024-07-22T19:00:00Z"),
                lastUpdated = Instant.parse("2024-07-22T19:00:00Z"),
                content = "Evening tacos were amazing",
            )

        val narrative =
            narrativeOf(
                storyBeats =
                    listOf(
                        StoryBeat(
                            moment = "Morning hike",
                            context = "Starting the day right",
                            emotionalWeight = "energized",
                            evidenceIds = listOf(textEntry1.uid.toString()),
                        ),
                        StoryBeat(
                            moment = "Evening tacos",
                            context = "Culinary exploration",
                            emotionalWeight = "satisfied",
                            evidenceIds = listOf(textEntry2.uid.toString()),
                        ),
                    ),
                overallNarrative = "A perfect day. From hiking to tacos.",
            )

        val result =
            sequencer.sequence(
                narrative = narrative,
                curation = CurationResult.EMPTY,
                textEntries = listOf(textEntry1, textEntry2),
                people = emptyList(),
                weather = null,
                locationPath = emptyList(),
                stats = emptyStats,
                activities = emptyList(),
            )

        val transitions = result.filterIsInstance<RewindContent.Transition>()
        assertTrue(transitions.isNotEmpty(), "Should have at least one transition between beats")
    }

    @Test
    fun sequence_handlesMultipleStoryBeats() {
        val entries =
            listOf(
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = Instant.parse("2024-07-22T10:00:00Z"),
                    lastUpdated = Instant.parse("2024-07-22T10:00:00Z"),
                    content = "First event",
                ),
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = Instant.parse("2024-07-23T10:00:00Z"),
                    lastUpdated = Instant.parse("2024-07-23T10:00:00Z"),
                    content = "Second event",
                ),
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = Instant.parse("2024-07-24T10:00:00Z"),
                    lastUpdated = Instant.parse("2024-07-24T10:00:00Z"),
                    content = "Third event",
                ),
            )

        val narrative =
            narrativeOf(
                storyBeats =
                    entries.mapIndexed { index, entry ->
                        StoryBeat(
                            moment = "Event ${index + 1}",
                            context = "Part of busy week",
                            emotionalWeight = "engaged",
                            evidenceIds = listOf(entry.uid.toString()),
                        )
                    },
                overallNarrative = "A busy week. One event after another. You handled it well.",
            )

        val result =
            sequencer.sequence(
                narrative = narrative,
                curation = CurationResult.EMPTY,
                textEntries = entries,
                people = emptyList(),
                weather = null,
                locationPath = emptyList(),
                stats = emptyStats,
                activities = emptyList(),
            )

        val textPanels = result.filterIsInstance<RewindContent.TextNote>()
        assertEquals(3, textPanels.size)

        val transitions = result.filterIsInstance<RewindContent.Transition>()
        assertEquals(2, transitions.size)
    }

    @Test
    fun sequence_handlesBeatWithNoEvidence() {
        val narrative =
            narrativeOf(
                storyBeats =
                    listOf(
                        StoryBeat(
                            moment = "A moment of reflection",
                            context = "Taking time to think",
                            emotionalWeight = "contemplative",
                            evidenceIds = emptyList(),
                        ),
                    ),
                overallNarrative = "A quiet week. Time for reflection.",
            )

        val result =
            sequencer.sequence(
                narrative = narrative,
                curation = CurationResult.EMPTY,
                textEntries = emptyList(),
                people = emptyList(),
                weather = null,
                locationPath = emptyList(),
                stats = emptyStats,
                activities = emptyList(),
            )

        val narrativeContexts = result.filterIsInstance<RewindContent.NarrativeContext>()
        assertTrue(narrativeContexts.size >= 3)
    }

    @Test
    fun sequence_sortsEvidenceChronologically() {
        val laterEntry =
            JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = Instant.parse("2024-07-22T18:00:00Z"),
                lastUpdated = Instant.parse("2024-07-22T18:00:00Z"),
                content = "Evening reflection",
            )

        val earlierEntry =
            JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = Instant.parse("2024-07-22T10:00:00Z"),
                lastUpdated = Instant.parse("2024-07-22T10:00:00Z"),
                content = "Morning thoughts",
            )

        val narrative =
            narrativeOf(
                storyBeats =
                    listOf(
                        StoryBeat(
                            moment = "Day of reflection",
                            context = "Deep thinking",
                            emotionalWeight = "contemplative",
                            evidenceIds = listOf(laterEntry.uid.toString(), earlierEntry.uid.toString()),
                        ),
                    ),
                overallNarrative = "A thoughtful day.",
            )

        val result =
            sequencer.sequence(
                narrative = narrative,
                curation = CurationResult.EMPTY,
                textEntries = listOf(laterEntry, earlierEntry),
                people = emptyList(),
                weather = null,
                locationPath = emptyList(),
                stats = emptyStats,
                activities = emptyList(),
            )

        val textPanels = result.filterIsInstance<RewindContent.TextNote>()
        assertEquals(2, textPanels.size)

        assertEquals("Morning thoughts", textPanels[0].content)
        assertEquals("Evening reflection", textPanels[1].content)
    }

    @Test
    fun sequence_withEmptyNarrative_createsMinimalStructure() {
        val narrative = narrativeOf(overallNarrative = "Not much happened.")

        val result =
            sequencer.sequence(
                narrative = narrative,
                curation = CurationResult.EMPTY,
                textEntries = emptyList(),
                people = emptyList(),
                weather = null,
                locationPath = emptyList(),
                stats = emptyStats,
                activities = emptyList(),
            )

        assertTrue(result.size >= 2)
        assertTrue(result.first() is RewindContent.NarrativeContext)
        assertTrue(result.last() is RewindContent.NarrativeContext)
    }

    @Test
    fun `local-heuristic origin leads with a personality card`() {
        val narrative =
            narrativeOf(
                overallNarrative = "Your week.",
                origin = app.logdate.shared.model.NarrativeOrigin.LOCAL_HEURISTIC,
            )

        val result =
            sequencer.sequence(
                narrative = narrative,
                curation = CurationResult.EMPTY,
                textEntries = emptyList(),
                people = emptyList(),
                weather = null,
                locationPath = emptyList(),
                stats = emptyStats,
                activities = emptyList(),
            )

        assertTrue(result.isNotEmpty())
        assertIs<RewindContent.PersonalityCard>(
            result.first(),
            message = "expected local-origin Rewind to open on a personality card",
        )
    }

    @Test
    fun `local-heuristic origin skips the closing prose panel`() {
        val narrative =
            narrativeOf(
                overallNarrative = "First sentence. Second sentence.",
                origin = app.logdate.shared.model.NarrativeOrigin.LOCAL_HEURISTIC,
            )

        val result =
            sequencer.sequence(
                narrative = narrative,
                curation = CurationResult.EMPTY,
                textEntries = emptyList(),
                people = emptyList(),
                weather = null,
                locationPath = emptyList(),
                stats = emptyStats,
                activities = emptyList(),
            )

        // Local origin should never close on a NarrativeContext that echoes the templated
        // resolution sentence; that copy would feel hollow without an AI-written narrative.
        val closingResolution =
            result.filterIsInstance<RewindContent.NarrativeContext>().any {
                it.contextText == "Second sentence."
            }
        assertEquals(
            false,
            closingResolution,
            "local-origin Rewind should not emit a resolution panel; got panels=${result.map { it::class.simpleName }}",
        )
    }

    @Test
    fun `quotes-only-llm origin still opens with narrative prose`() {
        val narrative =
            narrativeOf(
                overallNarrative = "Opening line. Closing line.",
                origin = app.logdate.shared.model.NarrativeOrigin.QUOTES_ONLY_LLM,
            )

        val result =
            sequencer.sequence(
                narrative = narrative,
                curation = CurationResult.EMPTY,
                textEntries = emptyList(),
                people = emptyList(),
                weather = null,
                locationPath = emptyList(),
                stats = emptyStats,
                activities = emptyList(),
            )

        val firstPanel = assertIs<RewindContent.NarrativeContext>(result.first())
        assertEquals("Opening line.", firstPanel.contextText)
    }

    /** Builds a minimal [WeekNarrative] for tests, defaulting the unused fields. */
    private fun narrativeOf(
        storyBeats: List<StoryBeat> = emptyList(),
        overallNarrative: String = "A week.",
        origin: app.logdate.shared.model.NarrativeOrigin = app.logdate.shared.model.NarrativeOrigin.LLM,
    ): WeekNarrative =
        WeekNarrative(
            themes = emptyList(),
            emotionalTone = "neutral",
            storyBeats = storyBeats,
            overallNarrative = overallNarrative,
            origin = origin,
        )

    /**
     * Test fixture that mirrors what the curator would produce: every media item cited by
     * any beat lands in that beat's bucket; everything else becomes a free agent.
     */
    private fun curationOf(
        media: List<IndexedMedia>,
        beats: List<StoryBeat>,
    ): CurationResult {
        val perBeat = mutableMapOf<Int, MutableList<MediaCandidate>>()
        val freeAgents = mutableListOf<MediaCandidate>()
        media.forEach { m ->
            val beatIdx = beats.indexOfFirst { m.uid.toString() in it.evidenceIds }
            val candidate =
                MediaCandidate(
                    media = m,
                    signals = MediaSignals(),
                    isLLMCited = beatIdx >= 0,
                    assignedBeatIndex = if (beatIdx >= 0) beatIdx else null,
                )
            if (beatIdx >= 0) {
                perBeat.getOrPut(beatIdx) { mutableListOf() }.add(candidate)
            } else {
                freeAgents.add(candidate)
            }
        }
        return CurationResult(
            perBeat = perBeat.mapValues { it.value.toList() },
            freeAgents = freeAgents,
            rejected = emptyList(),
            sigByMediaUid = media.associate { it.uid to 50f },
        )
    }
}
