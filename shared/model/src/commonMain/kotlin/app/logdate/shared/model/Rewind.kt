package app.logdate.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant
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
    val metadata: RewindMetadata? = null,
    /**
     * Whether this rewind has been opened and viewed at least once.
     */
    val isViewed: Boolean = false,
    /**
     * When the user first opened this rewind, or null if never viewed.
     */
    val firstViewedAt: Instant? = null,
    /**
     * How many times the user has opened this rewind.
     */
    val viewCount: Int = 0,
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
    val peopleHighlighted: List<String>,
    /**
     * AI-invented noticing prompts the synthesizer drew from the period's actual content.
     *
     * Empty when synthesis didn't produce prompts. The Rewind UI shows these as panels at
     * the end of the story; an empty list means no prompt panels are shown at all.
     */
    val reflectionPrompts: List<ReflectionPrompt> = emptyList(),
    /**
     * Verbatim lines the AI pulled from the user's actual journal entries this period.
     *
     * Empty when synthesis didn't surface any quotes. The Rewind UI shows these as
     * highlighted quote panels woven into the story.
     */
    val highlightedQuotes: List<HighlightedQuote> = emptyList(),
    /**
     * Atmospheric weather context for the rewind's primary location and date range.
     *
     * Null when the rewind period had no usable location data, or when the weather
     * fetch failed or was skipped. The Rewind UI renders this as a small atmospheric
     * chip on the title panel — "rainy week", "75°", and so on — so the user feels
     * what the week was actually like outdoors before they read a single beat.
     */
    val weatherContext: WeatherContext? = null,
    /**
     * Downsampled location path for the rewind's period, for the map panel renderer.
     *
     * The Rewind UI inserts a small visual map of where the user actually was when the
     * list contains enough distinct points to be interesting (≥3 points spanning at
     * least a kilometer). Empty list (or fewer points) means no map panel is rendered.
     * Capped at ~50 points so the metadata blob stays small.
     */
    val locationPath: List<MapPoint> = emptyList(),
)

/**
 * One geographic point on the rewind's location path.
 *
 * Persisted on [RewindMetadata.locationPath] so the map panel can render the user's
 * actual movement across the rewind period. Stored as raw lat/lon (no projection) so
 * the renderer can compute its own bounding box and projection at draw time.
 */
@Serializable
data class MapPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Instant,
)

/**
 * Atmospheric summary of what the weather was like for the rewind's period and place.
 *
 * Aggregated from a daily-resolution weather source over the rewind's date range —
 * see `OpenMeteoHistoricalWeatherProvider` in client/intelligence. Stored on
 * [RewindMetadata] so the title panel can render a quiet weather chip.
 *
 * @property category The dominant condition across the period (the day with the most
 *   precipitation wins for rainy/snowy, otherwise the average sky cover decides).
 * @property avgTempCelsius Average daily mean temperature across the period in Celsius.
 *   Stored in Celsius internally; the UI converts to Fahrenheit when the locale calls
 *   for it.
 * @property maxTempCelsius Highest single-day max across the period.
 * @property minTempCelsius Lowest single-day min across the period.
 * @property precipitationMm Total precipitation across the period in millimeters.
 *   Used to decide between "drizzle" and "downpour" framing in the chip label.
 */
@Serializable
data class WeatherContext(
    val category: WeatherCategory,
    val avgTempCelsius: Double,
    val maxTempCelsius: Double,
    val minTempCelsius: Double,
    val precipitationMm: Double,
)

/**
 * The dominant weather condition across a rewind's period.
 *
 * Used by the title-panel chip to pick its icon and lead phrase.
 */
@Serializable
enum class WeatherCategory {
    SUNNY,
    CLOUDY,
    RAINY,
    SNOWY,
    MIXED,
}

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
    MIXED,
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
    val primaryLocation: String?,
)
