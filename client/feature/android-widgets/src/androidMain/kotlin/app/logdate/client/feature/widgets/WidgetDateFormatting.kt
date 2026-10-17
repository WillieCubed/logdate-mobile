package app.logdate.client.feature.widgets

import android.content.Context
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * Formats this date for a widget in the device language, for example "March 24, 2025" in English.
 *
 * Widget state stores the result, so it is formatted when the state is built rather than when Glance
 * draws it.
 */
internal fun LocalDate.formatForDisplay(context: Context): String =
    context.getString(R.string.widget_date_month_day_year, context.getString(month.nameResource), day, year)

private val Month.nameResource: Int
    get() =
        when (this) {
            Month.JANUARY -> R.string.widget_month_january
            Month.FEBRUARY -> R.string.widget_month_february
            Month.MARCH -> R.string.widget_month_march
            Month.APRIL -> R.string.widget_month_april
            Month.MAY -> R.string.widget_month_may
            Month.JUNE -> R.string.widget_month_june
            Month.JULY -> R.string.widget_month_july
            Month.AUGUST -> R.string.widget_month_august
            Month.SEPTEMBER -> R.string.widget_month_september
            Month.OCTOBER -> R.string.widget_month_october
            Month.NOVEMBER -> R.string.widget_month_november
            Month.DECEMBER -> R.string.widget_month_december
        }
