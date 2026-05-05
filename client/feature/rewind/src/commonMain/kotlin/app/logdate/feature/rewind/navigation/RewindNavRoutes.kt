package app.logdate.feature.rewind.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.rewind.ui.RewindOpenCallback
import app.logdate.feature.rewind.ui.RewindOverviewScreen
import app.logdate.feature.rewind.ui.detail.RewindDetailScreen
import app.logdate.ui.navigation.taggedEntry
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * A route corresponding to the Rewind overview screen.
 *
 * Here, a user can view a list of past rewinds and select one to view.
 */
@Serializable
data object RewindOverviewRoute : NavKey

/**
 * A route corresponding to the Rewind detail screen.
 *
 * Here, a user can interact with a Rewind.
 */
@Serializable
data class RewindDetailRoute(
    val id: String,
) : NavKey {
    constructor(id: Uuid) : this(id.toString())
}

/** Pushes the Rewind detail viewer for the given Rewind id. */
fun NavBackStack<NavKey>.navigateToRewind(uid: Uuid) {
    add(RewindDetailRoute(uid))
}

/** Pushes the Rewind overview list. */
fun NavBackStack<NavKey>.navigateToRewindsOverview() {
    add(RewindOverviewRoute)
}

/** Registers the Rewind overview entry. */
fun EntryProviderScope<NavKey>.rewindOverviewEntry(onOpenRewind: RewindOpenCallback) {
    taggedEntry<RewindOverviewRoute> {
        RewindOverviewScreen(onOpenRewind = onOpenRewind)
    }
}

/** Registers the Rewind detail entry. */
fun EntryProviderScope<NavKey>.rewindDetailEntry(onExitRewind: () -> Unit) {
    taggedEntry<RewindDetailRoute> { route ->
        RewindDetailScreen(
            rewindId = Uuid.parse(route.id),
            onExitRewind = onExitRewind,
        )
    }
}

/** Convenience to register both Rewind entries at once. */
fun EntryProviderScope<NavKey>.rewindEntries(
    onOpenRewind: RewindOpenCallback,
    onExitRewind: () -> Unit,
) {
    rewindOverviewEntry(onOpenRewind = onOpenRewind)
    rewindDetailEntry(onExitRewind = onExitRewind)
}
