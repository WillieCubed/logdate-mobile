package app.logdate.navigation.scenes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import app.logdate.navigation.routes.core.JournalList
import app.logdate.navigation.routes.core.LibraryListRoute
import app.logdate.navigation.routes.core.LocationRoute
import app.logdate.navigation.routes.core.RewindList
import app.logdate.navigation.routes.core.TimelineListRoute

/**
 * Represents the different tabs in the HomeScene.
 */
enum class HomeTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: NavKey,
) {
    TIMELINE("Timeline", Icons.Filled.History, Icons.Outlined.History, TimelineListRoute),
    LOCATION("Locations", Icons.Filled.LocationOn, Icons.Outlined.LocationOn, LocationRoute),
    JOURNALS("Journals", Icons.Filled.Book, Icons.Outlined.Book, JournalList),
    LIBRARY("Library", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary, LibraryListRoute),
    REWIND("Rewind", Icons.Filled.DateRange, Icons.Outlined.DateRange, RewindList),
    ;

    companion object {
        /**
         * Tabs visible in navigation by default.
         *
         * The Library tab is behind a feature flag and excluded from the default set.
         * Pass the full [entries] list to include it when the flag is enabled.
         */
        val visibleEntries: List<HomeTab> = entries.filter { it != LIBRARY }
    }
}
