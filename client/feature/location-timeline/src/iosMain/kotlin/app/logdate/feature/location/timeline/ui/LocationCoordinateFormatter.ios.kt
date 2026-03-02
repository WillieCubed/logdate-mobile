package app.logdate.feature.location.timeline.ui

import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter

internal actual fun formatCoordinateValue(value: Double): String {
    val formatter =
        NSNumberFormatter().apply {
            minimumFractionDigits = 6u
            maximumFractionDigits = 6u
            usesGroupingSeparator = false
        }
    val number = NSNumber(value)
    return formatter.stringFromNumber(number) ?: value.toString()
}
