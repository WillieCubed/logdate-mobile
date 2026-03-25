package app.logdate.client.feature.widgets

/**
 * Intent extra key identifying the navigation source.
 *
 * Matches the convention used by other modules (e.g., audio playback, location history).
 */
const val EXTRA_NAV_SOURCE = "app.logdate.extra.NAV_SOURCE"

/** Navigation source value for the On This Day widget. */
const val NAV_SOURCE_ON_THIS_DAY_WIDGET = "widget_on_this_day"

/** Intent extra carrying the ISO-8601 date string for deep-linking to a specific day. */
const val EXTRA_WIDGET_TARGET_DATE = "widget_target_date"
