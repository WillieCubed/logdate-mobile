package app.logdate.feature.core.settings.ui

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

internal actual fun formatByteSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = abs(bytes).toDouble()
    var unitIndex = 0

    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }

    val fractionDigits = if (unitIndex == 0) 0 else if (size < 10) 1 else 0
    val formatter = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }
    val formatted = formatter.format(size)
    val sign = if (bytes < 0) "-" else ""
    return "$sign$formatted ${units[unitIndex]}"
}
