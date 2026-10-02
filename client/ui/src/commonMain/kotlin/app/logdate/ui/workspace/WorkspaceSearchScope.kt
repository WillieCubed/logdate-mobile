@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

internal data class WorkspaceSearchBinding(
    val query: String,
    val hint: String,
    val onQuery: (String) -> Unit,
)

internal class WorkspaceSearchController {
    private var owner: Any? = null
    var binding: WorkspaceSearchBinding? by mutableStateOf(null)
        private set

    fun bind(
        owner: Any,
        value: WorkspaceSearchBinding,
    ) {
        this.owner = owner
        binding = value
    }

    fun release(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            binding = null
        }
    }
}

internal val LocalWorkspaceSearchController = staticCompositionLocalOf<WorkspaceSearchController?> { null }

@Composable
internal fun WorkspaceSearchHost(content: @Composable () -> Unit) {
    val controller = remember { WorkspaceSearchController() }
    CompositionLocalProvider(LocalWorkspaceSearchController provides controller, content = content)
}

/** A destination can scope the shell's sole search field without adding panel search chrome. */
@Composable
fun WorkspaceSearchScope(
    query: String,
    hint: String,
    onQuery: (String) -> Unit,
    enabled: Boolean = true,
) {
    val controller = LocalWorkspaceSearchController.current ?: return
    val owner = remember { Any() }
    SideEffect {
        if (enabled) controller.bind(owner, WorkspaceSearchBinding(query, hint, onQuery)) else controller.release(owner)
    }
    DisposableEffect(controller) { onDispose { controller.release(owner) } }
}
