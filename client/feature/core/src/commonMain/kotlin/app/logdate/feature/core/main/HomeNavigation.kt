package app.logdate.feature.core.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore

internal class HomeNavigation(
    val selected: HomeRouteDestination,
    val destinations: List<HomeRouteDestination>,
    val select: (HomeRouteDestination) -> Unit,
)

@Composable
internal fun rememberHomeNavigation(
    libraryEnabled: Boolean,
    flags: FeatureFlagStore,
): HomeNavigation {
    val placesEnabled: Boolean? by remember(flags) { flags.observe(FeatureFlag.PLACES_BETA) }
        .collectAsStateWithLifecycle(initialValue = null)
    val visible = HomeRouteDestination.visibleEntries(libraryEnabled, placesEnabled == true)
    var saved: HomeRouteDestination by rememberSaveable { mutableStateOf(HomeRouteDestination.Timeline) }
    val current = saved.takeIf { it in visible } ?: HomeRouteDestination.Timeline
    LaunchedEffect(visible, placesEnabled) {
        // Do not erase a restored Places selection before its saved opt-in arrives.
        if (placesEnabled == null && saved == HomeRouteDestination.LocationHistory) return@LaunchedEffect
        if (saved !in visible) saved = HomeRouteDestination.Timeline
    }
    return HomeNavigation(current, visible) { if (it in visible) saved = it }
}
