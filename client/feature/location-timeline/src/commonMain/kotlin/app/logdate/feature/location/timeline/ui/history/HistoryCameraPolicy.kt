package app.logdate.feature.location.timeline.ui.history

internal fun shouldFitHistoryDay(
    fitted: Boolean,
    hasCoordinates: Boolean,
): Boolean = !fitted && hasCoordinates

internal fun shouldMoveHistoryCamera(
    loaded: Boolean,
    previous: String?,
    selected: String?,
): Boolean = loaded && selected != null && selected != previous

internal fun advanceHistorySelection(
    loaded: Boolean,
    previous: String?,
    selected: String?,
): String? = if (loaded) selected else previous
