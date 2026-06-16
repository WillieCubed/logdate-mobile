package app.logdate.feature.core.main

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

/**
 * Tabs in the home navigation shell.
 *
 * Matches Android's [HomeTab] enum: same order, icons, and labels so the desktop
 * and Android experiences stay consistent.
 */
enum class HomeRouteDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Timeline(
        label = "Timeline",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
    ),
    LocationHistory(
        label = "Locations",
        selectedIcon = Icons.Filled.LocationOn,
        unselectedIcon = Icons.Outlined.LocationOn,
    ),
    Journals(
        label = "Journals",
        selectedIcon = Icons.Filled.Book,
        unselectedIcon = Icons.Outlined.Book,
    ),
    Library(
        label = "Library",
        selectedIcon = Icons.Filled.PhotoLibrary,
        unselectedIcon = Icons.Outlined.PhotoLibrary,
    ),
    Rewind(
        label = "Rewind",
        selectedIcon = Icons.Filled.DateRange,
        unselectedIcon = Icons.Outlined.DateRange,
    ),
    ;

    companion object {
        /**
         * Tabs shown in the home navigation shell.
         *
         * The Library tab is gated behind the `library_enabled` user setting, so it only appears
         * when [isLibraryEnabled] is true. Every other tab is always present.
         */
        fun visibleEntries(isLibraryEnabled: Boolean): List<HomeRouteDestination> = entries.filter { it != Library || isLibraryEnabled }

        val ALL = entries.toTypedArray()
    }
}
