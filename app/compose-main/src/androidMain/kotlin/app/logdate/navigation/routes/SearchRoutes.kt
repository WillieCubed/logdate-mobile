package app.logdate.navigation.routes

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.search.ui.SearchScreen
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.routes.core.SearchRoute
import app.logdate.navigation.routes.routeEntry
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

fun MainAppNavigator.openSearch() {
    backStack.add(SearchRoute)
}

fun EntryProviderScope<NavKey>.searchRoutes(
    onBack: () -> Unit,
    onNavigateToDay: (LocalDate) -> Unit,
    onNavigateToPerson: (Uuid) -> Unit = {},
) {
    routeEntry<SearchRoute> { _ ->
        SearchScreen(
            onNavigateToDay = onNavigateToDay,
            onNavigateToPerson = onNavigateToPerson,
            onGoBack = onBack,
        )
    }
}
