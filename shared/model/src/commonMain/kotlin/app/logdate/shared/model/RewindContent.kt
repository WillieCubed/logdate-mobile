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
     * A text note in a Rewind.
     */
    data class TextNote(
        override val timestamp: Instant,
        override val sourceId: Uuid,
        val content: String
    ) : RewindContent()
    
    /**
     * An image in a Rewind.
     */
    data class Image(
        override val timestamp: Instant,
        override val sourceId: Uuid,
        val uri: String,
        val caption: String? = null
    ) : RewindContent()
    
    /**
     * A video in a Rewind.
     */
    data class Video(
        override val timestamp: Instant,
        override val sourceId: Uuid,
        val uri: String,
        val caption: String? = null,
        val duration: Duration
    ) : RewindContent()
}