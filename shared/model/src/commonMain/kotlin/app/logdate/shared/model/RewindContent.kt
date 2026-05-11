package app.logdate.shared.model

import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Content contained in a Rewind.
 *
 * This is designed to be extensible to accommodate different types of content
 * that might be included in Rewinds in the future.
 */
sealed class RewindContent {
    /**
     * Timestamp when this content was created.
     */
    abstract val timestamp: Instant

    /**
     * Identifier of the original content source.
     */
    abstract val sourceId: Uuid

    /**
     * Significance score (0-100) indicating how meaningful this content is.
     *
     * Higher scores indicate more emotionally significant or important content.
     * Scores are calculated based on multiple signals including:
     * - Text depth and emotional language
     * - People mentions and relationships
     * - Media richness and clustering
     * - Temporal patterns and novelty
     *
     * Null for content that hasn't been scored.
     */
    abstract val significanceScore: Float?

    /**
     * A text note in a Rewind.
     */
    data class TextNote(
        override val timestamp: Instant,
        override val sourceId: Uuid,
        val content: String,
        override val significanceScore: Float? = null,
    ) : RewindContent()

    /**
     * An image in a Rewind.
     */
    data class Image(
        override val timestamp: Instant,
        override val sourceId: Uuid,
        val uri: String,
        val caption: String? = null,
        override val significanceScore: Float? = null,
    ) : RewindContent()

    /**
     * A video in a Rewind.
     */
    data class Video(
        override val timestamp: Instant,
        override val sourceId: Uuid,
        val uri: String,
        val caption: String? = null,
        val duration: Duration,
        override val significanceScore: Float? = null,
    ) : RewindContent()

    /**
     * Narrative context panel - sets the scene and tells what the week was about.
     *
     * Example: "You finally made it to the California coast"
     * Example: "This week was rough - work deadlines and personal stress converged"
     *
     * These panels provide CONTEXT and MEANING, not just content display.
     */
    data class NarrativeContext(
        override val timestamp: Instant,
        override val sourceId: Uuid,
        val contextText: String,
        val backgroundImage: String? = null,
        override val significanceScore: Float? = null,
    ) : RewindContent()

    /**
     * Transition panel - connects story beats and explains thematic shifts.
     *
     * Example: "But evenings shifted to culinary adventures"
     * Example: "Meanwhile, gaming became the escape you needed"
     *
     * These panels create narrative flow by explaining HOW moments connect.
     */
    data class Transition(
        override val timestamp: Instant,
        override val sourceId: Uuid,
        val transitionText: String,
        override val significanceScore: Float? = null,
    ) : RewindContent()

    /**
     * Map panel - visualizes the period's location path as a polyline on a map.
     *
     * Rendered when the period has enough distinct points to read as movement
     * (the sequencer decides; today's threshold is ≥3 points spanning ≥1km).
     */
    data class MapPanel(
        override val timestamp: Instant,
        override val sourceId: Uuid,
        val locationPath: List<MapPoint>,
        override val significanceScore: Float? = null,
    ) : RewindContent()

    /**
     * Weather panel - atmospheric context for the period (sunny / rainy / snowy / mixed)
     * plus average / max / min temperature and precipitation totals.
     */
    data class WeatherPanel(
        override val timestamp: Instant,
        override val sourceId: Uuid,
        val weather: WeatherContext,
        override val significanceScore: Float? = null,
    ) : RewindContent()

    /**
     * Personality card - the Wrapped-style insight panel.
     *
     * Surfaces counts ("12 photos, 8 entries, 3 new places") plus the dominant
     * activity for the period ("Your week was mostly TRAVEL"). The renderer uses bold
     * typography to make this the visual peak of the Rewind.
     */
    data class PersonalityCard(
        override val timestamp: Instant,
        override val sourceId: Uuid,
        val stats: WeekStatsSnapshot,
        val dominantActivity: ActivityType,
        override val significanceScore: Float? = null,
    ) : RewindContent()

    /**
     * Top-N list card - the Wrapped staple: "Top 3 people", "Top places", "Top quotes".
     *
     * One panel per [kind] — the sequencer emits a list when the underlying data has
     * enough items to be interesting (≥2 distinct people for PEOPLE, etc.).
     */
    data class TopList(
        override val timestamp: Instant,
        override val sourceId: Uuid,
        val kind: TopListKind,
        val items: List<TopListItem>,
        override val significanceScore: Float? = null,
    ) : RewindContent()
}

/**
 * Category of a [RewindContent.TopList].
 */
enum class TopListKind {
    PEOPLE,
    PLACES,
    QUOTES,
}

/**
 * One row in a [RewindContent.TopList].
 *
 * [label] is the primary display string (a name, a place, a verbatim quote line).
 * [subtitle] is optional supporting copy (e.g. "5 entries", "Friday at the bookshop").
 * [count] is the underlying tally when relevant — renderers can use it for bar widths
 * or rank emphasis. [sourceId] links back to the originating entry / place / person so
 * tapping the row can deep-link.
 */
data class TopListItem(
    val label: String,
    val subtitle: String? = null,
    val count: Int? = null,
    val sourceId: Uuid? = null,
)

/**
 * Bag of headline numbers for a [RewindContent.PersonalityCard].
 *
 * Populated locally on every Rewind regardless of tier — these counts are always
 * derivable from the period's content without an LLM call.
 */
data class WeekStatsSnapshot(
    val photoCount: Int,
    val textNoteCount: Int,
    val distinctLocations: Int,
    val distinctPeople: Int,
    val newPlaces: Int = 0,
)
