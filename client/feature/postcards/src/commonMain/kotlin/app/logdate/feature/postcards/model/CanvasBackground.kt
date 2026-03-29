package app.logdate.feature.postcards.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The background fill of a Postcard canvas.
 */
@Serializable
sealed interface CanvasBackground {
    /**
     * A solid color background.
     *
     * @param value Hex color string (e.g., "#FFF5E6").
     */
    @Serializable
    @SerialName("color")
    data class SolidColor(
        val value: String,
    ) : CanvasBackground

    /**
     * A linear gradient background defined by color stops.
     */
    @Serializable
    @SerialName("gradient")
    data class Gradient(
        val stops: List<GradientStop>,
    ) : CanvasBackground
}

/**
 * A single stop in a gradient, defining a color at a normalized position.
 *
 * @param color Hex color string.
 * @param position Normalized position along the gradient axis, from 0.0 to 1.0.
 */
@Serializable
data class GradientStop(
    val color: String,
    val position: Float,
)
