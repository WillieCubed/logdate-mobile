package app.logdate.feature.postcards.model

import kotlinx.serialization.Serializable

/**
 * Describes the position, rotation, and scale of an element in canvas coordinate space.
 *
 * Coordinates are in density-independent units relative to the canvas origin (0, 0).
 * The canvas is unbounded — elements can be placed at any coordinate.
 */
@Serializable
data class ElementTransform(
    val x: Float = 0f,
    val y: Float = 0f,
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
)
