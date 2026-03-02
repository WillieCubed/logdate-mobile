package app.logdate.feature.location.timeline.ui

import java.text.NumberFormat
import java.util.Locale

internal actual fun formatCoordinateValue(value: Double): String {
    val formatter =
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 6
            maximumFractionDigits = 6
            isGroupingUsed = false
        }
    return formatter.format(value)
}
