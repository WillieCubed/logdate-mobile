package app.logdate.feature.postcards.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The complete document model for a Postcard.
 *
 * A Postcard is a spatial composition of layered elements (photos, ink, shapes, text,
 * stickers) on an unbounded canvas. The canvas uses an origin-based coordinate system
 * with density-independent units — there are no fixed bounds. The viewport and export
 * determine which region is visible.
 *
 * This document is serialized to JSON for persistence. The [version] field enables
 * forward-compatible schema evolution.
 *
 * @param version Schema version for forward compatibility. Current version: 1.
 * @param id Unique identifier for this Postcard.
 * @param title User-assigned title (or auto-generated from source moment date).
 * @param createdAt When the Postcard was first created.
 * @param modifiedAt When the Postcard was last modified.
 * @param sourceMomentRef Optional reference to the moment that seeded this Postcard.
 * @param background The canvas background fill.
 * @param elements All elements on the canvas, ordered by [CanvasElement.zIndex].
 */
@Serializable
data class PostcardDocument(
    val version: Int = CURRENT_VERSION,
    val id: Uuid,
    val title: String,
    val createdAt: Instant,
    val modifiedAt: Instant,
    val sourceMomentRef: Uuid? = null,
    val background: CanvasBackground = CanvasBackground.SolidColor("#FFFFFF"),
    val elements: List<CanvasElement> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1

        /**
         * Shared Json instance for serializing/deserializing PostcardDocuments.
         * Uses `ignoreUnknownKeys` so documents from newer app versions
         * can still be read by older versions.
         */
        val json: Json = Json { ignoreUnknownKeys = true }
    }
}
