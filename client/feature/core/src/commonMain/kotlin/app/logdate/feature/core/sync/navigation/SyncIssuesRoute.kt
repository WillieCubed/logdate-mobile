package app.logdate.feature.core.sync.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.logdate.ui.navigation.taggedEntry
import kotlinx.serialization.Serializable

/** Retain the serialized key so restoring an older back stack can dismiss the removed screen. */
@Serializable
data object SyncIssuesRoute : NavKey

fun EntryProviderScope<NavKey>.syncIssuesEntry(onBack: () -> Unit) {
    taggedEntry<SyncIssuesRoute> {
        LaunchedEffect(Unit) { onBack() }
    }
}
