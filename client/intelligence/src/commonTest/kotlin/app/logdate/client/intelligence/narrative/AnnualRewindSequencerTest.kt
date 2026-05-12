package app.logdate.client.intelligence.narrative

import app.logdate.shared.model.NarrativeOrigin
import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindContent
import app.logdate.shared.model.YearChapter
import app.logdate.shared.model.YearNarrative
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AnnualRewindSequencerTest {
    private val sequencer = AnnualRewindSequencer()

    private fun rewinds() =
        listOf(
            Rewind(
                uid = Uuid.random(),
                startDate = Clock.System.now(),
                endDate = Clock.System.now(),
                generationDate = Clock.System.now(),
                label = "wk",
                title = "Week",
                content = emptyList(),
                metadata = null,
            ),
        )

    private fun narrative(origin: NarrativeOrigin): YearNarrative =
        YearNarrative(
            chapters =
                listOf(
                    YearChapter(
                        name = "Early year",
                        summary = "Quieter weeks.",
                        monthRange = "January – March",
                        emotionalTone = "varied",
                        keyWeekIndices = listOf(0),
                        themes = listOf("travel"),
                    ),
                ),
            overallNarrative =
                "A year shaped by travel across one week. Plenty more to come. " +
                    "Memorable.",
            yearThemes = listOf("travel", "social"),
            emotionalArc = "Steady throughout.",
            reflectionPrompts = emptyList(),
            origin = origin,
        )

    @Test
    fun `LLM-origin narrative emits prose opening panel`() {
        val panels = sequencer.sequence(narrative(NarrativeOrigin.LLM), rewinds(), 2026)
        val firstNarrative =
            panels.firstOrNull { it is RewindContent.NarrativeContext } as? RewindContent.NarrativeContext
        assertTrue(firstNarrative != null)
        assertTrue(
            firstNarrative!!.contextText.startsWith("A year shaped by travel"),
            "expected LLM-origin Rewind to lead with the overallNarrative prose",
        )
    }

    @Test
    fun `local-origin narrative skips prose opening`() {
        val panels = sequencer.sequence(narrative(NarrativeOrigin.LOCAL_HEURISTIC), rewinds(), 2026)
        val firstNarrative =
            panels.firstOrNull { it is RewindContent.NarrativeContext } as? RewindContent.NarrativeContext
        assertTrue(firstNarrative != null)
        assertEquals(
            "travel · social",
            firstNarrative!!.contextText,
            "expected local-origin Rewind to lead with the themes card",
        )
    }

    @Test
    fun `local-origin narrative skips closing repetition`() {
        val panels = sequencer.sequence(narrative(NarrativeOrigin.LOCAL_HEURISTIC), rewinds(), 2026)
        // The last sentence of the prose ("Memorable.") would only appear if the
        // closing bookend ran. It shouldn't for local-origin.
        assertTrue(
            panels.none {
                it is RewindContent.NarrativeContext &&
                    it.contextText.trim() == "Memorable."
            },
            "expected local-origin Rewind to omit the closing-sentence bookend",
        )
    }

    @Test
    fun `LLM-origin narrative includes closing repetition when multi-sentence`() {
        val panels = sequencer.sequence(narrative(NarrativeOrigin.LLM), rewinds(), 2026)
        assertTrue(
            panels.any {
                it is RewindContent.NarrativeContext &&
                    it.contextText.trim() == "Memorable."
            },
            "expected LLM-origin Rewind to include the closing-sentence bookend",
        )
    }
}
