package app.logdate.navigation

import androidx.navigation3.runtime.NavKey
import app.logdate.feature.core.main.HomeRoute
import app.logdate.feature.editor.navigation.EntryEditorRoute
import app.logdate.ui.creation.EntryCreationLauncher
import app.logdate.ui.creation.EntryCreationShape

internal fun MutableList<NavKey>.tryLaunchHomeEditor(
    sourceId: String?,
    sourceShape: EntryCreationShape = EntryCreationShape.Large,
): Boolean {
    if (lastOrNull() != HomeRoute) return false
    add(EntryEditorRoute(creationTransitionId = sourceId, creationTransitionShape = sourceShape))
    return true
}

internal fun MutableList<NavKey>.homeEntryCreation(launcher: EntryCreationLauncher): () -> Unit =
    {
        tryLaunchHomeEditor(launcher.currentSourceId, launcher.currentSourceShape)
    }
