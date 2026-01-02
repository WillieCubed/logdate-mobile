package app.logdate.shared.model

import kotlinx.datetime.Instant
import kotlin.time.Duration
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
        override val significanceScore: Float? = null
    ) : RewindContent()

    /**
     * An image in a Rewind.
     */
    data class Image(
        override val timestamp: Instant,
        override val sourceId: Uuid,
        val uri: String,
        val caption: String? = null,
        override val significanceScore: Float? = null
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
        override val significanceScore: Float? = null
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
        override val significanceScore: Float? = null
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
        override val significanceScore: Float? = null
    ) : RewindContent()
}