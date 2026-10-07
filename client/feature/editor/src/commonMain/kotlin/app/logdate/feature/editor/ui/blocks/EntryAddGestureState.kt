package app.logdate.feature.editor.ui.blocks

internal data class EntryAddGestureState(
    val expanded: Boolean = false,
    val dragDistance: Float = 0f,
    val dragging: Boolean = false,
) {
    fun dragBy(
        deltaY: Float,
        maxDistance: Float = Float.POSITIVE_INFINITY,
    ): EntryAddGestureState =
        copy(
            dragDistance = (dragDistance + if (expanded) deltaY else -deltaY).coerceIn(0f, maxDistance),
            dragging = true,
        )

    fun progress(travel: Float): Float {
        val fraction = (dragDistance / travel).coerceIn(0f, 1f)
        return if (expanded) 1f - fraction else fraction
    }

    fun release(threshold: Float): EntryAddGestureState =
        EntryAddGestureState(expanded = if (expanded) dragDistance < threshold else dragDistance >= threshold)

    fun cancel(): EntryAddGestureState = EntryAddGestureState(expanded = expanded)
}
