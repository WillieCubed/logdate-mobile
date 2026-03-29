package app.logdate.feature.postcards.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The type of ink tool used to draw a stroke.
 */
@Serializable
enum class InkTool {
    @SerialName("pen")
    PEN,

    @SerialName("highlighter")
    HIGHLIGHTER,

    @SerialName("eraser")
    ERASER,
}
