package app.logdate.feature.editor.ui.handwriting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.uuid.Uuid

internal val EntryPageStateSaver =
    listSaver<EntryPageOverviewState, Any>(
        save = { state ->
            listOf(
                state.earlierExpanded,
                state.scroll.value,
                state.historyScroll.value,
                state.focusedAnchor
                    ?.blockId
                    ?.toString()
                    .orEmpty(),
                state.focusedAnchor?.top ?: 0f,
                state.focusedAnchor?.width ?: 0,
            )
        },
        restore = { values ->
            EntryPageOverviewState(values[0] as Boolean, values[1] as Int, values[2] as Int).apply {
                val id = values[3] as String
                if (id.isNotEmpty()) focusedAnchor = EntryPageScrollAnchor(Uuid.parse(id), values[4] as Float, values[5] as Int)
            }
        },
    )

@Composable
internal fun rememberEntryPageState() = rememberSaveable(saver = EntryPageStateSaver) { EntryPageOverviewState(false) }
