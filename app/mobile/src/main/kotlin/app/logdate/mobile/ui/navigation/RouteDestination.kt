package app.logdate.mobile.ui.navigation

import app.logdate.feature.editor.navigation.ROUTE_NEW_NOTE
import app.logdate.feature.journals.navigation.JOURNAL_NEW_ROUTE
import app.logdate.feature.journals.navigation.ROUTE_JOURNAL_BASE
import app.logdate.feature.onboarding.navigation.ONBOARDING_ROUTE
import app.logdate.mobile.settings.ui.ROUTE_SETTINGS

/**
 * A fixed navigation route for the app.
 */
sealed class RouteDestination(
    val route: String,
) {
    data object Home : RouteDestination("home")

    data object Base : RouteDestination("main")

    data object NewNote : RouteDestination(ROUTE_NEW_NOTE)

    data object JournalDetails : RouteDestination(ROUTE_JOURNAL_BASE)
    data object NewJournal : RouteDestination(JOURNAL_NEW_ROUTE)

    data object Settings : RouteDestination(ROUTE_SETTINGS)

    data object Onboarding : RouteDestination(ONBOARDING_ROUTE)
}

