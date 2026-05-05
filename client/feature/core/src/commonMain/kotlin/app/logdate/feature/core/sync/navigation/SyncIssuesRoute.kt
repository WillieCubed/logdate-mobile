package app.logdate.feature.core.sync.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.core.sync.SyncIssuesScreen
import app.logdate.ui.navigation.taggedEntry
import kotlinx.serialization.Serializable

/**
 * Detail screen listing sync writes that exhausted their retry budget.
 */
@Serializable
data object SyncIssuesRoute : NavKey

/** Pushes the sync issues detail screen. */
fun NavBackStack<NavKey>.navigateToSyncIssues() {
    add(SyncIssuesRoute)
}

/** Registers the sync issues entry. */
fun EntryProviderScope<NavKey>.syncIssuesEntry(onBack: () -> Unit) {
    taggedEntry<SyncIssuesRoute> {
        SyncIssuesScreen(onGoBack = onBack)
    }
}
