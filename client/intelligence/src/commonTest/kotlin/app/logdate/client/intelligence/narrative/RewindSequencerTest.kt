package app.logdate.client.intelligence.narrative

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.shared.model.Person
import app.logdate.shared.model.RewindContent
import app.logdate.shared.model.StoryBeat
import app.logdate.shared.model.WeekNarrative
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class RewindSequencerTest {

    private val sequencer = RewindSequencer()

    @Test
    fun sequence_createsOpeningContext() {
        val narrative = WeekNarrative(
            themes = listOf("vacation"),
            emotionalTone = "excited",
            storyBeats = emptyList(),
            overallNarrative = "You explored the coast. It was wonderful."
        )

        val result = sequencer.sequence(
            narrative = narrative,
            textEntries = emptyList(),
            media = emptyList(),
            people = emptyList()
        )

        assertTrue(result.isNotEmpty())
        val firstPanel = result.first()
        assertTrue(firstPanel is RewindContent.NarrativeContext)
        assertEquals("You explored the coast.", (firstPanel as RewindContent.NarrativeContext).contextText)
    }

    @Test
    fun sequence_createsResolution() {
        val narrative = WeekNarrative(
            themes = listOf("vacation"),
            emotionalTone = "excited",
            storyBeats = emptyList(),
            overallNarrative = "You explored the coast. It was wonderful."
        )

        val result = sequencer.sequence(
            narrative = narrative,
            textEntries = emptyList(),
            media = emptyList(),
            people = emptyList()
        )

        assertTrue(result.isNotEmpty())
        val lastPanel = result.last()
        assertTrue(lastPanel is RewindContent.NarrativeContext)
        assertEquals("It was wonderful.", (lastPanel as RewindContent.NarrativeContext).contextText)
    }

    @Test
    fun sequence_includesStoryBeatsWithEvidence() {
        val textEntry = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = Instant.parse("2024-07-22T10:00:00Z"),
            lastUpdated = Instant.parse("2024-07-22T10:00:00Z"),
            content = "Found the hidden beach!"
        )

        val narrative = WeekNarrative(
            themes = listOf("exploration"),
            emotionalTone = "triumphant",
            storyBeats = listOf(
                StoryBeat(
                    moment = "Found the hidden beach",
                    context = "Long-anticipated goal",
                    emotionalWeight = "triumphant",
                    evidenceIds = listOf(textEntry.uid.toString())
                )
            ),
            overallNarrative = "You found the beach. Amazing."
        )

        val result = sequencer.sequence(
            narrative = narrative,
            textEntries = listOf(textEntry),
            media = emptyList(),
            people = emptyList()
        )

        // Should have: opening context + text evidence + resolution
        assertTrue(result.size >= 3)

        // Find the text note panel
        val textPanel = result.find { it is RewindContent.TextNote }
        assertTrue(textPanel is RewindContent.TextNote)
        assertEquals(textEntry.content, (textPanel as RewindContent.TextNote).content)
    }

    @Test
    fun sequence_includesMediaEvidence() {
        val imageMedia = IndexedMedia.Image(
            uid = Uuid.random(),
            uri = "file:///beach.jpg",
            timestamp = Instant.parse("2024-07-22T18:00:00Z"),
            caption = "Sunset at the beach"
        )

        val videoMedia = IndexedMedia.Video(
            uid = Uuid.random(),
            uri = "file:///beach.mp4",
            timestamp = Instant.parse("2024-07-22T18:30:00Z"),
            caption = "Wave action",
            duration = 30.seconds
        )

        val narrative = WeekNarrative(
            themes = listOf("vacation"),
            emotionalTone = "joyful",
            storyBeats = listOf(
                StoryBeat(
                    moment = "Beach memories",
                    context = "Captured the moment",
                    emotionalWeight = "happy",
                    evidenceIds = listOf(
                        imageMedia.uid.toString(),
                        videoMedia.uid.toString()
                    )
                )
            ),
            overallNarrative = "You captured beautiful beach moments."
        )

        val result = sequencer.sequence(
            narrative = narrative,
            textEntries = emptyList(),
            media = listOf(imageMedia, videoMedia),
            people = emptyList()
        )

        // Find image and video panels
        val imagePanel = result.find { it is RewindContent.Image }
        val videoPanel = result.find { it is RewindContent.Video }

        assertTrue(imagePanel is RewindContent.Image)
        assertTrue(videoPanel is RewindContent.Video)

        assertEquals(imageMedia.uri, (imagePanel as RewindContent.Image).uri)
        assertEquals(videoMedia.uri, (videoPanel as RewindContent.Video).uri)
    }

    @Test
    fun sequence_createsTransitionsBetweenBeats() {
        val textEntry1 = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = Instant.parse("2024-07-22T10:00:00Z"),
            lastUpdated = Instant.parse("2024-07-22T10:00:00Z"),
            content = "Morning hike was refreshing"
        )

        val textEntry2 = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = Instant.parse("2024-07-22T19:00:00Z"),
            lastUpdated = Instant.parse("2024-07-22T19:00:00Z"),
            content = "Evening tacos were amazing"
        )

        val narrative = WeekNarrative(
            themes = listOf("vacation", "food"),
            emotionalTone = "delightful",
            storyBeats = listOf(
                StoryBeat(
                    moment = "Morning hike",
                    context = "Starting the day right",
                    emotionalWeight = "energized",
                    evidenceIds = listOf(textEntry1.uid.toString())
                ),
                StoryBeat(
                    moment = "Evening tacos",
                    context = "Culinary exploration",
                    emotionalWeight = "satisfied",
                    evidenceIds = listOf(textEntry2.uid.toString())
                )
            ),
            overallNarrative = "A perfect day. From hiking to tacos."
        )

        val result = sequencer.sequence(
            narrative = narrative,
            textEntries = listOf(textEntry1, textEntry2),
            media = emptyList(),
            people = emptyList()
        )

        // Should have transitions between beats
        val transitions = result.filterIsInstance<RewindContent.Transition>()
        assertTrue(transitions.isNotEmpty(), "Should have at least one transition between beats")
    }

    @Test
    fun sequence_handlesMultipleStoryBeats() {
        val entries = listOf(
            JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = Instant.parse("2024-07-22T10:00:00Z"),
                lastUpdated = Instant.parse("2024-07-22T10:00:00Z"),
                content = "First event"
            ),
            JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = Instant.parse("2024-07-23T10:00:00Z"),
                lastUpdated = Instant.parse("2024-07-23T10:00:00Z"),
                content = "Second event"
            ),
            JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = Instant.parse("2024-07-24T10:00:00Z"),
                lastUpdated = Instant.parse("2024-07-24T10:00:00Z"),
                content = "Third event"
            )
        )

        val narrative = WeekNarrative(
            themes = listOf("eventful"),
            emotionalTone = "busy",
            storyBeats = entries.mapIndexed { index, entry ->
                StoryBeat(
                    moment = "Event ${index + 1}",
                    context = "Part of busy week",
                    emotionalWeight = "engaged",
                    evidenceIds = listOf(entry.uid.toString())
                )
            },
            overallNarrative = "A busy week. One event after another. You handled it well."
        )

        val result = sequencer.sequence(
            narrative = narrative,
            textEntries = entries,
            media = emptyList(),
            people = emptyList()
        )

        // Should have all three text entries
        val textPanels = result.filterIsInstance<RewindContent.TextNote>()
        assertEquals(3, textPanels.size)

        // Should have transitions between beats (2 transitions for 3 beats)
        val transitions = result.filterIsInstance<RewindContent.Transition>()
        assertEquals(2, transitions.size)
    }

    @Test
    fun sequence_handlesBeatWithNoEvidence() {
        val narrative = WeekNarrative(
            themes = listOf("quiet"),
            emotionalTone = "peaceful",
            storyBeats = listOf(
                StoryBeat(
                    moment = "A moment of reflection",
                    context = "Taking time to think",
                    emotionalWeight = "contemplative",
                    evidenceIds = emptyList()
                )
            ),
            overallNarrative = "A quiet week. Time for reflection."
        )

        val result = sequencer.sequence(
            narrative = narrative,
            textEntries = emptyList(),
            media = emptyList(),
            people = emptyList()
        )

        // Should create a narrative context panel for the beat even without evidence
        val narrativeContexts = result.filterIsInstance<RewindContent.NarrativeContext>()
        // Opening + beat placeholder + resolution = at least 3
        assertTrue(narrativeContexts.size >= 3)
    }

    @Test
    fun sequence_sortsEvidenceChronologically() {
        val laterEntry = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = Instant.parse("2024-07-22T18:00:00Z"),
            lastUpdated = Instant.parse("2024-07-22T18:00:00Z"),
            content = "Evening reflection"
        )

        val earlierEntry = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = Instant.parse("2024-07-22T10:00:00Z"),
            lastUpdated = Instant.parse("2024-07-22T10:00:00Z"),
            content = "Morning thoughts"
        )

        val narrative = WeekNarrative(
            themes = listOf("reflection"),
            emotionalTone = "thoughtful",
            storyBeats = listOf(
                StoryBeat(
                    moment = "Day of reflection",
                    context = "Deep thinking",
                    emotionalWeight = "contemplative",
                    evidenceIds = listOf(
                        laterEntry.uid.toString(),
                        earlierEntry.uid.toString()
                    )
                )
            ),
            overallNarrative = "A thoughtful day."
        )

        val result = sequencer.sequence(
            narrative = narrative,
            textEntries = listOf(laterEntry, earlierEntry),
            media = emptyList(),
            people = emptyList()
        )

        val textPanels = result.filterIsInstance<RewindContent.TextNote>()
        assertEquals(2, textPanels.size)

        // Earlier entry should come first
        assertEquals("Morning thoughts", textPanels[0].content)
        assertEquals("Evening reflection", textPanels[1].content)
    }

    @Test
    fun sequence_withEmptyNarrative_createsMinimalStructure() {
        val narrative = WeekNarrative(
            themes = emptyList(),
            emotionalTone = "uneventful",
            storyBeats = emptyList(),
            overallNarrative = "Not much happened."
        )

        val result = sequencer.sequence(
            narrative = narrative,
            textEntries = emptyList(),
            media = emptyList(),
            people = emptyList()
        )

        // Should still have opening and resolution
        assertTrue(result.size >= 2)
        assertTrue(result.first() is RewindContent.NarrativeContext)
        assertTrue(result.last() is RewindContent.NarrativeContext)
    }
}
