package app.logdate.ui.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Whether the destination's content has been scrolled to its end, so the bottom bar can match the
 * working surface instead of sitting on the canvas. It starts away from the end: content that has
 * not scrolled keeps the canvas bar.
 */
class WorkspaceBottomEdge(
    initiallyReached: Boolean = false,
) {
    var reached by mutableStateOf(initiallyReached)
        private set

    val connection =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                when {
                    available.y < 0f -> reached = true
                    consumed.y > 0f -> reached = false
                }
                return Offset.Zero
            }
        }
}

@Composable
fun rememberWorkspaceBottomEdge(key: Any?): WorkspaceBottomEdge = remember(key) { WorkspaceBottomEdge() }
