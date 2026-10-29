package app.logdate.feature.editor.ui

/** Rebase changed endpoints from the dimension actually displayed, using the same seekable clock. */
internal class RetainedEditorDimension {
    private var initialized = false
    private var previousSource = 0f
    private var previousFocus = 0f
    private var displayed = 0f
    private var correction = 0f
    private var correctionStart = 0f

    fun approach(
        source: Float,
        focus: Float,
        fraction: Float,
    ): Float {
        val progress = fraction.coerceIn(0f, 1f)
        val desired = source + (focus - source) * progress
        if (!initialized || progress == 0f || progress == 1f) {
            correction = 0f
            correctionStart = progress
        } else if (source != previousSource || focus != previousFocus) {
            correction = displayed - desired
            correctionStart = progress
        }
        // Either physical endpoint must be exact, even if a predictive gesture scrubs backwards
        // before its cancellation callback changes the logical target.
        val weight =
            when {
                progress == correctionStart -> 1f
                progress < correctionStart -> if (correctionStart == 0f) 0f else progress / correctionStart
                else -> if (correctionStart == 1f) 0f else (1f - progress) / (1f - correctionStart)
            }.coerceIn(0f, 1f)
        displayed = desired + correction * weight
        previousSource = source
        previousFocus = focus
        initialized = true
        return displayed
    }
}
