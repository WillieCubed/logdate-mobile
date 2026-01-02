package app.logdate.client.intelligence.narrative

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.shared.model.Person
import app.logdate.shared.model.RewindContent
import app.logdate.shared.model.WeekNarrative
import io.github.aakira.napier.Napier
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Sequences Rewind content into a narrative-driven story flow.
 *
 * Unlike formulaic sequencers (opening→peak→closing), this arranges content
 * to TELL the specific story of each week based on AI-generated narrative understanding.
 *
 * Process:
 * 1. Opening: Narrative context panel that sets the scene ("You explored the coast...")
 * 2. Story Beats: For each beat, gather evidence and create supporting panels
 * 3. Transitions: Connect beats with narrative transitions ("But evenings shifted...")
 * 4. Resolution: Close with reflection on what the week meant
 */
class RewindSequencer {
    /**
     * Sequences content into a narrative-driven story.
     *
     * @param narrative AI-generated understanding of the week's story
     * @param textEntries All text entries from the week
     * @param media All media items from the week
     * @param people People mentioned during the week
     * @return Ordered list of RewindContent panels that tell the story
     */
    fun sequence(
        narrative: WeekNarrative,
        textEntries: List<JournalNote.Text>,
        media: List<IndexedMedia>,
        people: List<Person> = emptyList()
    ): List<RewindContent> {
        Napier.d("Sequencing narrative with ${narrative.storyBeats.size} beats")

        val panels = mutableListOf<RewindContent>()

        // 1. Opening: Narrative context that sets the scene
        panels.add(createOpeningContext(narrative))

        // 2. Story beats with evidence and transitions
        narrative.storyBeats.forEachIndexed { index, beat ->
            // Add transition before beat (except first beat)
            if (index > 0) {
                val transition = createTransition(beat, narrative.storyBeats[index - 1])
                transition?.let { panels.add(it) }
            }

            // Add panels for this story beat
            val beatPanels = createBeatPanels(beat, textEntries, media)
            panels.addAll(beatPanels)
        }

        // 3. Resolution: Close with the overall narrative
        panels.add(createResolution(narrative))

        Napier.d("Generated ${panels.size} panels from narrative")
        return panels
    }

    /**
     * Creates opening narrative context panel.
     *
     * Sets the scene for the week's story.
     */
    private fun createOpeningContext(narrative: WeekNarrative): RewindContent.NarrativeContext {
        // Extract first sentence from overall narrative as opening
        val sentences = narrative.overallNarrative.split(". ")
        val openingText = if (sentences.size > 1) {
            sentences.first() + "."
        } else {
            narrative.overallNarrative
        }

        return RewindContent.NarrativeContext(
            timestamp = Instant.DISTANT_PAST, // Placeholder - will be set by generator
            sourceId = Uuid.random(),
            contextText = openingText,
            backgroundImage = null // Could enhance: Use first photo from week
        )
    }

    /**
     * Creates transition panel between story beats.
     *
     * Explains thematic shifts and connects narrative moments.
     */
    private fun createTransition(
        currentBeat: app.logdate.shared.model.StoryBeat,
        previousBeat: app.logdate.shared.model.StoryBeat
    ): RewindContent.Transition? {
        // Generate transition based on emotional weight shift
        val transitionText = when {
            // Positive to negative shift
            previousBeat.emotionalWeight.contains("joyful", ignoreCase = true) &&
                    currentBeat.emotionalWeight.contains("melancholy", ignoreCase = true) ->
                "But then things shifted"

            // Negative to positive shift
            previousBeat.emotionalWeight.contains("exhausted", ignoreCase = true) &&
                    currentBeat.emotionalWeight.contains("triumphant", ignoreCase = true) ->
                "Then came a breakthrough"

            // Continuing theme
            else -> {
                // Use context to create transition
                val connector = when {
                    currentBeat.context.contains("meanwhile", ignoreCase = true) -> "Meanwhile"
                    currentBeat.context.contains("evening", ignoreCase = true) -> "When evenings came"
                    else -> "And then"
                }
                connector
            }
        }

        return RewindContent.Transition(
            timestamp = Instant.DISTANT_PAST, // Placeholder
            sourceId = Uuid.random(),
            transitionText = transitionText
        )
    }

    /**
     * Creates panels for a specific story beat.
     *
     * Gathers evidence (text quotes, photos, videos) that support this beat.
     */
    private fun createBeatPanels(
        beat: app.logdate.shared.model.StoryBeat,
        textEntries: List<JournalNote.Text>,
        media: List<IndexedMedia>
    ): List<RewindContent> {
        val panels = mutableListOf<RewindContent>()

        // Find evidence for this beat
        val evidenceIds = beat.evidenceIds.toSet()

        // Add text evidence
        textEntries
            .filter { it.uid.toString() in evidenceIds }
            .forEach { entry ->
                panels.add(
                    RewindContent.TextNote(
                        timestamp = entry.creationTimestamp,
                        sourceId = entry.uid,
                        content = entry.content
                    )
                )
            }

        // Add media evidence
        media.filter { it.uid.toString() in evidenceIds }
            .forEach { mediaItem ->
                when (mediaItem) {
                    is IndexedMedia.Image -> {
                        panels.add(
                            RewindContent.Image(
                                timestamp = mediaItem.timestamp,
                                sourceId = mediaItem.uid,
                                uri = mediaItem.uri,
                                caption = mediaItem.caption
                            )
                        )
                    }
                    is IndexedMedia.Video -> {
                        panels.add(
                            RewindContent.Video(
                                timestamp = mediaItem.timestamp,
                                sourceId = mediaItem.uid,
                                uri = mediaItem.uri,
                                caption = mediaItem.caption,
                                duration = mediaItem.duration
                            )
                        )
                    }
                }
            }

        // If no evidence found, create a narrative context panel for the beat
        if (panels.isEmpty()) {
            Napier.w("No evidence found for beat: ${beat.moment}")
            panels.add(
                RewindContent.NarrativeContext(
                    timestamp = Instant.DISTANT_PAST,
                    sourceId = Uuid.random(),
                    contextText = beat.moment,
                    backgroundImage = null
                )
            )
        }

        // Sort panels chronologically within the beat
        return panels.sortedBy { it.timestamp }
    }

    /**
     * Creates resolution panel that closes the week's story.
     */
    private fun createResolution(narrative: WeekNarrative): RewindContent.NarrativeContext {
        // Use last sentence(s) from overall narrative as resolution
        val sentences = narrative.overallNarrative.split(". ")
        val resolutionText = if (sentences.size > 1) {
            sentences.drop(1).joinToString(". ")
        } else {
            // If only one sentence, use emotional tone as resolution
            "A ${narrative.emotionalTone} week."
        }

        return RewindContent.NarrativeContext(
            timestamp = Instant.DISTANT_FUTURE, // Placeholder - signals end
            sourceId = Uuid.random(),
            contextText = resolutionText,
            backgroundImage = null
        )
    }
}
