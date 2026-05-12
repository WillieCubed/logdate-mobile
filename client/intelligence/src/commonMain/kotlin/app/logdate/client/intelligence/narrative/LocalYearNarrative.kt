package app.logdate.client.intelligence.narrative

import app.logdate.shared.model.NarrativeOrigin
import app.logdate.shared.model.Rewind
import app.logdate.shared.model.YearChapter
import app.logdate.shared.model.YearNarrative

/**
 * Builds a [YearNarrative] from a year's weekly rewinds without calling an LLM.
 *
 * Used by [app.logdate.client.domain.rewind.GenerateAnnualRewindUseCase] when AI
 * synthesis is unavailable — so the user still gets a Year in Review (organized
 * into quarter-chapters) instead of an error.
 *
 * Tone is intentionally factual: we know which themes / activities / people
 * appeared most, and that's what we tell back. No invented prompts.
 */
fun buildLocalYearNarrative(
    year: Int,
    weeklyRewinds: List<Rewind>,
): YearNarrative {
    val themes = aggregateTopThemes(weeklyRewinds, max = MAX_YEAR_THEMES)
    val chapters = buildQuarterChapters(year, weeklyRewinds)
    val topTheme = themes.firstOrNull()
    val overall =
        if (topTheme != null) {
            "A year shaped by $topTheme across ${weeklyRewinds.size} weeks."
        } else {
            "A year recorded across ${weeklyRewinds.size} weeks."
        }

    return YearNarrative(
        chapters = chapters,
        overallNarrative = overall,
        yearThemes = themes,
        emotionalArc = "A varied year.",
        reflectionPrompts = emptyList(),
        origin = NarrativeOrigin.LOCAL_HEURISTIC,
    )
}

private fun aggregateTopThemes(
    weeklyRewinds: List<Rewind>,
    max: Int,
): List<String> =
    weeklyRewinds
        .flatMap { it.metadata?.detectedActivities ?: emptyList() }
        .map { it.name.lowercase() }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(max)
        .map { it.key }

private fun buildQuarterChapters(
    year: Int,
    weeklyRewinds: List<Rewind>,
): List<YearChapter> {
    if (weeklyRewinds.isEmpty()) return emptyList()
    return (0 until QUARTERS).map { q ->
        val startIdx = (weeklyRewinds.size * q) / QUARTERS
        val endIdx = (weeklyRewinds.size * (q + 1)) / QUARTERS
        val weeks = weeklyRewinds.subList(startIdx, endIdx)
        val chapterThemes =
            weeks
                .flatMap { it.metadata?.detectedActivities ?: emptyList() }
                .map { it.name.lowercase() }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(MAX_CHAPTER_THEMES)
                .map { it.key }
        YearChapter(
            name = QUARTER_NAMES[q],
            summary =
                if (chapterThemes.isNotEmpty()) {
                    "Mostly ${chapterThemes.first()} this stretch."
                } else {
                    "Quieter weeks."
                },
            monthRange = QUARTER_MONTH_RANGES[q],
            emotionalTone = "varied",
            keyWeekIndices = (startIdx until endIdx).toList(),
            themes = chapterThemes,
        )
    }
}

private const val MAX_YEAR_THEMES: Int = 5
private const val MAX_CHAPTER_THEMES: Int = 3
private const val QUARTERS: Int = 4
private val QUARTER_NAMES: List<String> = listOf("Early year", "Spring", "Summer", "Late year")
private val QUARTER_MONTH_RANGES: List<String> =
    listOf(
        "January – March",
        "April – June",
        "July – September",
        "October – December",
    )
