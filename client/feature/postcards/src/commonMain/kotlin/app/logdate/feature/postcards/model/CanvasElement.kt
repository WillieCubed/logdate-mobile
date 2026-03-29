package app.logdate.feature.postcards.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * A single element on a Postcard canvas.
 *
 * Elements are positioned in an unbounded, origin-based coordinate space using
 * density-independent units. Each element has a [transform] defining its position,
 * rotation, and scale, and a [zIndex] controlling draw order.
 */
@Serializable
sealed interface CanvasElement {
    val id: Uuid
    val transform: ElementTransform
    val zIndex: Int

    /**
     * A photo pulled from a LogDate moment.
     *
     * This is a live reference — [momentRef] points to the source moment, enabling
     * tap-through navigation (intertextuality). The photo is loaded from [mediaUri]
     * at render time, not embedded in the document.
     */
    @Serializable
    @SerialName("photo")
    data class Photo(
        override val id: Uuid,
        val momentRef: Uuid,
        val mediaUri: String,
        override val transform: ElementTransform = ElementTransform(),
        override val zIndex: Int = 0,
        val parallaxDepth: Float = 0.3f,
    ) : CanvasElement

    /**
     * User-entered text with font, color, and size.
     */
    @Serializable
    @SerialName("text")
    data class Text(
        override val id: Uuid,
        val content: String,
        val fontFamily: String,
        val color: String,
        val fontSize: Float,
        override val transform: ElementTransform = ElementTransform(),
        override val zIndex: Int = 0,
    ) : CanvasElement

    /**
     * A freehand ink stroke drawn by the user.
     *
     * The stroke is defined by a sequence of [points] with pressure data,
     * drawn with the specified [tool], [color], and [strokeWidth].
     */
    @Serializable
    @SerialName("ink")
    data class Ink(
        override val id: Uuid,
        val tool: InkTool,
        val color: String,
        val strokeWidth: Float,
        val points: List<InkPoint>,
        override val transform: ElementTransform = ElementTransform(),
        override val zIndex: Int = 0,
    ) : CanvasElement

    /**
     * A geometric shape element.
     *
     * The shape's bounds are defined by [width] and [height] relative to the
     * transform origin. Shapes can have an optional [fillColor].
     */
    @Serializable
    @SerialName("shape")
    data class Shape(
        override val id: Uuid,
        val shapeKind: ShapeKind,
        val color: String,
        val fillColor: String? = null,
        val strokeWidth: Float,
        val width: Float,
        val height: Float,
        override val transform: ElementTransform = ElementTransform(),
        override val zIndex: Int = 0,
    ) : CanvasElement

    /**
     * A sticker extracted from a photo via on-device segmentation.
     *
     * References a sticker in the user's personal sticker library by [stickerRef].
     * The actual sticker image is loaded at render time.
     */
    @Serializable
    @SerialName("sticker")
    data class Sticker(
        override val id: Uuid,
        val stickerRef: Uuid,
        override val transform: ElementTransform = ElementTransform(),
        override val zIndex: Int = 0,
    ) : CanvasElement
}
