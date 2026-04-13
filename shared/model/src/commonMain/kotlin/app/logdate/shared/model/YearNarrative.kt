package app.logdate.shared.model

import kotlinx.serialization.Serializable

/**
 * AI-generated understanding of a full year of the user's life, distilled from
 * the weekly rewinds the system already built across that year.
 *
 * The year narrative sees the same content the user would see if they opened every
 * weekly rewind back-to-back — but compresses it into 5-8 "chapters" that trace
 * the arc of the year rather than retelling individual weeks.
 */
@Serializable
data class YearNarrative(
    /**
     * 5-8 chapters that cover the entire year without gaps or overlaps.
     *
     * Each chapter groups a stretch of consecutive weeks that share a narrative
     * arc — a trip, a project, a relationship shift, a season of change.
     */
    val chapters: List<YearChapter>,
    /**
     * 3-5 sentence story of the year as a whole.
     */
    val overallNarrative: String,
    /**
     * 3-7 themes that characterized the entire year.
     */
    val yearThemes: List<String>,
    /**
     * One sentence describing how the year's emotional tone evolved.
     */
    val emotionalArc: String,
    /**
     * Year-level noticings drawn from patterns across the full year.
     */
    val reflectionPrompts: List<ReflectionPrompt> = emptyList(),
)

/**
 * One chapter of the user's year — a period of weeks that share a narrative arc.
 */
@Serializable
data class YearChapter(
    /** A short name for the chapter (e.g., "The California road trip", "Settling in"). */
    val name: String,
    /** 1-2 sentence summary of what this chapter was about. */
    val summary: String,
    /** Human-readable month range (e.g., "January – March"). */
    val monthRange: String,
    /** Emotional tone of this chapter. */
    val emotionalTone: String,
    /** Indices into the input weekly summaries that define this chapter. */
    val keyWeekIndices: List<Int>,
    /** Themes specific to this chapter. */
    val themes: List<String>,
)
