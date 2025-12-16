package app.logdate.shared.model

import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * A rewind for a user's memories.
 */
data class Rewind(
    /**
     * A universally-unique identifier for the rewind.
     */
    val uid: Uuid,
    /**
     * The start date of the time period for this Rewind.
     */
    val startDate: Instant,
    /**
     * The end date of the time period for this Rewind.
     */
    val endDate: Instant,
    /**
     * When this Rewind was generated.
     */
    val generationDate: Instant,
    /**
     * A short label for the rewind.
     *
     * For normal rewinds that correspond to a week of the year this is the year and week number.
     *
     * Examples:
     * - 2024#42
     * - 2025#01
     */
    val label: String,
    /**
     * A user-friendly title for the rewind.
     */
    val title: String,
    /**
     * Content included in this rewind.
     */
    val content: List<RewindContent> = emptyList()
)