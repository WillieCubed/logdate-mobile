package app.logdate.model

import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * A rewind for a user's memories.
 */
data class Rewind(
    val uid: Uuid,
    val date: Instant,
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
)