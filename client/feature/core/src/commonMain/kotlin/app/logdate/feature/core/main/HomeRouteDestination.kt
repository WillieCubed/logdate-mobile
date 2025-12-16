package app.logdate.feature.core.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

enum class HomeRouteDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Timeline(
        label = "Timeline",
        selectedIcon = Icons.Filled.Timeline,
        unselectedIcon = Icons.Outlined.Timeline,
    ),
    Rewind(
        label = "Rewind",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
    ),
    Journals(
        label = "Journals",
        selectedIcon = Icons.Filled.Book,
        unselectedIcon = Icons.Outlined.Book,
    ),
    LocationHistory(
        label = "Locations",
        selectedIcon = Icons.Filled.LocationOn,
        unselectedIcon = Icons.Outlined.LocationOn,
    );
//    People(
//        label = "People",
//        selectedIcon = "ic_people_selected",
//        unselectedIcon = "ic_people_unselected",
//    ),
//    Events(
//        label = "Events",
//        selectedIcon = "ic_events_selected",
//        unselectedIcon = "ic_events_unselected",
//    ),
//    Notes(
//        label = "Notes",
//        selectedIcon = "ic_notes_selected",
//        unselectedIcon = "ic_notes_unselected",
//    );

    companion object {
        val ALL = entries.toTypedArray()
    }
}