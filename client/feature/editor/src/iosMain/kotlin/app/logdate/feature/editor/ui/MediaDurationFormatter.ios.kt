package app.logdate.feature.editor.ui

import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSDateComponentsFormatter
import platform.Foundation.NSDateComponentsFormatterUnitsStylePositional
import platform.Foundation.NSDateComponentsFormatterZeroFormattingBehaviorDefault
import platform.Foundation.NSDateComponentsFormatterZeroFormattingBehaviorPad

internal actual fun formatMediaDuration(durationMs: Long, padMinutes: Boolean): String {
    val formatter = NSDateComponentsFormatter().apply {
        unitsStyle = NSDateComponentsFormatterUnitsStylePositional
        allowedUnits = NSCalendarUnitMinute or NSCalendarUnitSecond
        zeroFormattingBehavior = if (padMinutes) {
            NSDateComponentsFormatterZeroFormattingBehaviorPad
        } else {
            NSDateComponentsFormatterZeroFormattingBehaviorDefault
        }
    }
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0).toDouble()
    return formatter.stringFromTimeInterval(totalSeconds) ?: "0:00"
}
