package app.logdate.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/** Surface meanings are shared by every Home destination; role and focus never recolor a panel. */
enum class WorkspaceSurface { Canvas, Panel, Group, Raised, Selected }

fun ColorScheme.workspaceContainer(surface: WorkspaceSurface): Color =
    when (surface) {
        WorkspaceSurface.Canvas -> surfaceContainer
        WorkspaceSurface.Panel -> this.surface
        WorkspaceSurface.Group -> surfaceContainerLow
        WorkspaceSurface.Raised -> surfaceContainerHigh
        WorkspaceSurface.Selected -> secondaryContainer
    }

fun ColorScheme.workspaceContent(surface: WorkspaceSurface): Color =
    when (surface) {
        WorkspaceSurface.Selected -> onSecondaryContainer
        else -> onSurface
    }
