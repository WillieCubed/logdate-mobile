package app.logdate.feature.journals.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavBackStackEntry

internal typealias LegacyEnterTransition =
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?

internal typealias LegacyExitTransition =
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?

internal val legacyJournalForwardEnterTransition: LegacyEnterTransition = {
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
    )
}

internal val legacyJournalForwardExitTransition: LegacyExitTransition = {
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
    )
}

internal val legacyJournalPopEnterTransition: LegacyEnterTransition = {
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
    )
}

internal val legacyJournalPopExitTransition: LegacyExitTransition = {
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
    )
}
