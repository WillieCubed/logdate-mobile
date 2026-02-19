package app.logdate.feature.core.settings.ui.components

import kotlinx.datetime.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.datetime.number

/**
 * Android implementation of localized date formatting.
 * Uses the system locale and formatting settings.
 */
actual fun formatDateLocalized(date: LocalDate): String {
    val javaLocalDate = date.toJavaLocalDate()
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
        .withLocale(Locale.getDefault())
    return javaLocalDate.format(formatter)
}

private fun LocalDate.toJavaLocalDate(): java.time.LocalDate =
    java.time.LocalDate.of(year, month.number, day)
