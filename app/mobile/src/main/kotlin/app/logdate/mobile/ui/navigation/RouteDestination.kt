package app.logdate.mobile.ui.navigation


/**
 * A fixed navigation route for the app.
 */
sealed class RouteDestination(
    val route: String,
) {
    data object Home : RouteDestination("home")

    data object Base : RouteDestination("main")

    data object NewNote : RouteDestination("main/new_note")

    data object JournalDetails : RouteDestination("journals")
    data object NewJournal : RouteDestination("journals/new")
    data object RewindList : RouteDestination("rewind")
    data object RewindDetails : RouteDestination("rewind/$REWIND_ID_ARG")

    data object Settings : RouteDestination("settings")

    data object Onboarding : RouteDestination("onboarding")
}

