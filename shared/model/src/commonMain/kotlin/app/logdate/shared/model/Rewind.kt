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
    val content: List<RewindContent> = emptyList(),
    /**
     * Intelligence metadata generated during rewind creation.
     *
     * Contains contextual information like detected activities, location insights,
     * milestones, and highlighted people from the time period.
     */
    val metadata: RewindMetadata? = null
)

/**
 * Intelligence metadata for a Rewind.
 *
 * This captures contextual understanding of what the time period was about,
 * including detected activities, location patterns, milestones, and people connections.
 */
data class RewindMetadata(
    /**
     * Activities detected during the time period (e.g., travel, social, focused work).
     */
    val detectedActivities: List<ActivityType>,
    /**
     * Summary of location-based insights for the period.
     */
    val locationSummary: LocationSummary?,
    /**
     * Significant milestones or achievements detected.
     */
    val milestones: List<String>,
    /**
     * Names of people highlighted in this rewind.
     */
    val peopleHighlighted: List<String>
)

/**
 * Types of activities that can be detected in a time period.
 */
enum class ActivityType {
    /**
     * Significant travel or movement to new locations.
     */
    TRAVEL,
    /**
     * Social week with many people interactions.
     */
    SOCIAL,
    /**
     * Focused work period with consistent location and project keywords.
     */
    FOCUSED_WORK,
    /**
     * Quiet week with minimal entries or activity.
     */
    QUIET,
    /**
     * Week with significant milestones or achievements.
     */
    MILESTONE,
    /**
     * Mixed activities without clear dominant pattern.
     */
    MIXED
}

/**
 * Summary of location-based insights for a time period.
 */
data class LocationSummary(
    /**
     * Number of distinct locations visited during the period.
     */
    val distinctLocations: Int,
    /**
     * Number of new places visited for the first time.
     */
    val newPlaces: Int,
    /**
     * Name or description of the primary/most-visited location.
     */
    val primaryLocation: String?
)