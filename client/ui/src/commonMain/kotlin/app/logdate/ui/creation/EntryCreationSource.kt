package app.logdate.ui.creation

import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import kotlin.uuid.Uuid

/** One physical source owns one pending visit; variants in the header/rail share this lease. */
class EntryCreationSource internal constructor(
    initialId: String = Uuid.random().toString(),
    private val newId: () -> String = { Uuid.random().toString() },
    private var launched: Boolean = false,
    private var departed: Boolean = false,
) {
    var id by mutableStateOf(initialId)
        private set
    private var available by mutableStateOf(true)
    private var disposed = false
    val canLaunch: Boolean get() = available && !launched && !disposed

    fun launch(
        expectedId: String,
        launcher: EntryCreationLauncher?,
        action: () -> Unit,
    ): Boolean = launch(expectedId, launcher, EntryCreationShape.Large, action)

    fun launch(
        expectedId: String,
        launcher: EntryCreationLauncher?,
        shape: EntryCreationShape,
        action: () -> Unit,
    ): Boolean {
        if (expectedId != id || !canLaunch) return false
        launched = true
        available = false
        return try {
            if (launcher == null) action() else launcher.withSource(id, shape, action)
            true
        } catch (error: Throwable) {
            launched = false
            available = true
            throw error
        }
    }

    fun observeVisibility(
        visible: Boolean,
        settled: Boolean,
    ) {
        if (disposed) return
        if (!visible) departed = true
        if (visible && settled && departed) {
            id = newId()
            launched = false
            departed = false
        }
        available = visible && settled
    }

    fun dispose() {
        disposed = true
        available = false
    }

    companion object {
        val Saver =
            listSaver<EntryCreationSource, Any>(
                save = { listOf(it.id, it.launched, it.departed) },
                restore = { EntryCreationSource(it[0] as String, launched = it[1] as Boolean, departed = it[2] as Boolean) },
            )
    }
}

@Composable
fun rememberEntryCreationSource(): EntryCreationSource {
    val source = rememberSaveable(saver = EntryCreationSource.Saver) { EntryCreationSource() }
    val transition = LocalNavAnimatedVisibilityScope.current?.transition
    val visible = transition == null || transition.targetState == EnterExitState.Visible
    val settled = transition == null || (!transition.isRunning && transition.currentState == EnterExitState.Visible && visible)
    SideEffect { source.observeVisibility(visible, settled) }
    DisposableEffect(source) { onDispose { source.dispose() } }
    return source
}
