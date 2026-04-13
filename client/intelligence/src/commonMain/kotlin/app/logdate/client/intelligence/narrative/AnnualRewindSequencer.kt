package app.logdate.client.intelligence.narrative

import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindContent
import app.logdate.shared.model.YearNarrative
import io.github.aakira.napier.Napier
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Builds the panel list for an annual "Year in Review" rewind.
 *
 * This is NOT a chronological replay of weekly highlights. Each panel is a different
 * ANGLE on the year as a whole — big stats, the year in one sentence, top themes,
 * the people who showed up most, how the emotional arc evolved, the defining moments.
 * Think Spotify Wrapped: "your top artists, your listening personality, your audio
 * aura", except each card is a facet of the user's actual life.
 *
 * The weekly rewinds are input only for cherry-picking a handful of representative
 * images and quotes — they're evidence, not the structure. The structure comes from
 * the [YearNarrative] the LLM produced.
 */
class AnnualRewindSequencer {
    fun sequence(
        narrative: YearNarrative,
        weeklyRewinds: List<Rewind>,
        year: Int,
    ): List<RewindContent> {
        Napier.d("Sequencing annual rewind for $year: ${narrative.chapters.size} chapters, ${weeklyRewinds.size} weekly rewinds")
        val panels = mutableListOf<RewindContent>()
        val now = weeklyRewinds.lastOrNull()?.endDate ?: Instant.DISTANT_PAST
        val earliest = weeklyRewinds.firstOrNull()?.startDate ?: Instant.DISTANT_PAST

        // 1. Year title — the overall narrative in one sweep.
        panels.add(
            RewindContent.NarrativeContext(
                timestamp = earliest,
                sourceId = Uuid.random(),
                contextText = narrative.overallNarrative,
            ),
        )

        // 2. Year themes — what the year was ABOUT.
        if (narrative.yearThemes.isNotEmpty()) {
            panels.add(
                RewindContent.NarrativeContext(
                    timestamp = earliest,
                    sourceId = Uuid.random(),
                    contextText = narrative.yearThemes.joinToString(" · "),
                ),
            )
        }

        // 3. Emotional arc — how the year FELT, start to finish.
        if (narrative.emotionalArc.isNotBlank()) {
            panels.add(
                RewindContent.Transition(
                    timestamp = earliest,
                    sourceId = Uuid.random(),
                    transitionText = narrative.emotionalArc,
                ),
            )
        }

        // 4. Chapter headlines — each chapter as a single defining-moment panel, not
        // a replay of the chapter's weeks. The name and summary carry the weight.
        narrative.chapters.forEach { chapter ->
            panels.add(
                RewindContent.NarrativeContext(
                    timestamp = Instant.DISTANT_PAST,
                    sourceId = Uuid.random(),
                    contextText = "${chapter.name}\n${chapter.summary}",
                    backgroundImage = pickChapterImage(chapter.keyWeekIndices, weeklyRewinds),
                ),
            )
        }

        // 5. People who defined the year.
        val topPeople =
            weeklyRewinds
                .flatMap { it.metadata?.peopleHighlighted ?: emptyList() }
                .groupBy { it }
                .entries
                .sortedByDescending { it.value.size }
                .take(5)
                .map { it.key }
        if (topPeople.isNotEmpty()) {
            panels.add(
                RewindContent.NarrativeContext(
                    timestamp = now,
                    sourceId = Uuid.random(),
                    contextText = topPeople.joinToString(", "),
                ),
            )
        }

        // 6. Best quote of the year — the single sharpest line across all weeks.
        val bestQuote =
            weeklyRewinds
                .flatMap { it.metadata?.highlightedQuotes ?: emptyList() }
                .firstOrNull()
        if (bestQuote != null) {
            panels.add(
                RewindContent.TextNote(
                    timestamp = now,
                    sourceId = Uuid.random(),
                    content = "\u201C${bestQuote.text}\u201D",
                ),
            )
        }

        // 7. Closing — the narrative's last sentence as a bookend.
        val closingSentences =
            narrative.overallNarrative
                .split(". ")
                .takeLast(1)
                .joinToString(". ")
        if (closingSentences.isNotBlank() && closingSentences != narrative.overallNarrative) {
            panels.add(
                RewindContent.NarrativeContext(
                    timestamp = now,
                    sourceId = Uuid.random(),
                    contextText = closingSentences,
                ),
            )
        }

        Napier.d("Generated ${panels.size} panels for annual rewind")
        return panels
    }

    /**
     * Picks a single representative image from the chapter's key weeks to use as a
     * background. Returns null when no image is available — the panel renders with a
     * solid accent background instead.
     */
    private fun pickChapterImage(
        keyWeekIndices: List<Int>,
        weeklyRewinds: List<Rewind>,
    ): String? =
        keyWeekIndices
            .asSequence()
            .mapNotNull { idx -> weeklyRewinds.getOrNull(idx) }
            .flatMap { it.content.asSequence() }
            .filterIsInstance<RewindContent.Image>()
            .firstOrNull()
            ?.uri
}
