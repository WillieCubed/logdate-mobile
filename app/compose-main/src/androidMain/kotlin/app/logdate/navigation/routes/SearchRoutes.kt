package app.logdate.navigation.routes

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import app.logdate.feature.search.ui.SearchScreen
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.routes.core.SearchRoute
import kotlinx.datetime.LocalDate

fun MainAppNavigator.openSearch() {
    backStack.add(SearchRoute)
}

fun EntryProviderBuilder<NavKey>.searchRoutes(
    onBack: () -> Unit,
    onNavigateToDay: (LocalDate) -> Unit,
) {
    entry<SearchRoute> { _ ->
        SearchScreen(
            onNavigateToDay = onNavigateToDay,
            onGoBack = onBack
        )
    }
}
