@file:Suppress("ktlint:standard:function-naming")

package app.logdate.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntryDecorator
import app.logdate.ui.navigation.routeClass

/** Applies Home framing to routes that participate in the workspace. */
@Composable
fun <T : Any> rememberWorkspaceRouteDecorator(): NavEntryDecorator<T> =
    remember {
        NavEntryDecorator { entry ->
            val route = entry.routeClass()
            val workspaceRoute =
                route in
                    setOf(
                        app.logdate.feature.journals.navigation.JournalsOverviewRoute::class,
                        app.logdate.feature.journals.navigation.JournalDetailsRoute::class,
                        app.logdate.feature.journals.navigation.JournalContentPickerRoute::class,
                        app.logdate.feature.journals.navigation.NoteDetailRoute::class,
                        app.logdate.feature.library.navigation.LibraryOverviewRoute::class,
                        app.logdate.feature.library.navigation.MediaDetailRoute::class,
                        TimelineDetailRoute::class,
                        app.logdate.feature.rewind.navigation.RewindDetailRoute::class,
                    )
            if (workspaceRoute) {
                app.logdate.ui.workspace
                    .WorkspaceRouteFrame { entry.Content() }
            } else {
                entry.Content()
            }
        }
    }
