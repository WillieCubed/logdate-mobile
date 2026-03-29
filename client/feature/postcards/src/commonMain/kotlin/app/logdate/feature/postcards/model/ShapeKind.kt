package app.logdate.feature.postcards.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The geometric shape type of a shape element.
 */
@Serializable
enum class ShapeKind {
    @SerialName("rectangle")
    RECTANGLE,

    @SerialName("circle")
    CIRCLE,

    @SerialName("line")
    LINE,

    @SerialName("arrow")
    ARROW,
}
