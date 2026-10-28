package app.logdate.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.ui.navigation.LocationTimelineRoute
import app.logdate.feature.core.main.HomeRoute

internal fun placesNavigationTarget(
    target: NavKey,
    enabled: Boolean?,
): NavKey? =
    if (target != LocationTimelineRoute) {
        target
    } else {
        when (enabled) {
            true -> target
            false -> HomeRoute
            null -> null
        }
    }

internal fun placesBackStack(
    stack: List<NavKey>,
    enabled: Boolean,
): List<NavKey> {
    if (enabled || LocationTimelineRoute !in stack) return stack
    val remaining = stack.filterNot { it == LocationTimelineRoute }
    return if (stack.firstOrNull() == LocationTimelineRoute && remaining.firstOrNull() != HomeRoute) {
        listOf(HomeRoute) + remaining
    } else {
        remaining.ifEmpty { listOf(HomeRoute) }
    }
}

/** Wait for durable preferences before reconciling a restored Places entry. */
@Composable
internal fun rememberPlacesBetaNavigation(
    flags: FeatureFlagStore,
    stack: MutableList<NavKey>,
    onboarded: Boolean,
    locked: Boolean,
): State<Boolean?> {
    val enabled: State<Boolean?> = remember(flags) { flags.observe(FeatureFlag.PLACES_BETA) }.collectAsState(initial = null)
    LaunchedEffect(enabled.value, stack.toList(), onboarded, locked) {
        val available = enabled.value ?: return@LaunchedEffect
        if (!onboarded || locked) return@LaunchedEffect
        val normalized = placesBackStack(stack.toList(), available)
        if (normalized != stack) {
            stack.removeAll { it == LocationTimelineRoute }
            if (normalized.firstOrNull() == HomeRoute && stack.firstOrNull() != HomeRoute) stack.add(0, HomeRoute)
        }
    }
    return enabled
}

internal fun placesLocationAction(
    enabled: Boolean?,
    stack: MutableList<NavKey>,
): (() -> Unit)? = if (enabled == true) ({ stack.add(LocationTimelineRoute) }) else null

internal fun addPlacesSafeTarget(
    stack: MutableList<NavKey>,
    target: NavKey,
) {
    if (target != HomeRoute || stack.lastOrNull() != HomeRoute) stack.add(target)
}

internal suspend fun openPlacesDeepLink(
    flags: FeatureFlagStore,
    stack: MutableList<NavKey>,
) {
    val target = requireNotNull(placesNavigationTarget(LocationTimelineRoute, flags.isEnabled(FeatureFlag.PLACES_BETA)))
    addPlacesSafeTarget(stack, target)
}
