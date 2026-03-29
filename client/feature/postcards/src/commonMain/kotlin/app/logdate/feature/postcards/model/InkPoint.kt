package app.logdate.feature.postcards.model

import kotlinx.serialization.Serializable

/**
 * A single point in an ink stroke, capturing position and pen pressure.
 *
 * @param x X coordinate relative to the ink element's transform origin.
 * @param y Y coordinate relative to the ink element's transform origin.
 * @param pressure Pen pressure from 0.0 (lightest) to 1.0 (full pressure).
 */
@Serializable
data class InkPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
)
