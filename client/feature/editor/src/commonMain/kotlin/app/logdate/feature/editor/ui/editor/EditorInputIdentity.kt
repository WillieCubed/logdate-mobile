package app.logdate.feature.editor.ui.editor

import androidx.compose.runtime.compositionLocalOf
import kotlin.uuid.Uuid

/** Memory identity survives empty-mode changes; input runtimes must not replay an earlier attempt. */
internal val LocalEditorInputTokens = compositionLocalOf<Map<Uuid, Uuid>> { emptyMap() }

internal data class EditorInputOwner(
    val id: Uuid,
    val token: Uuid?,
)

internal fun EntryEditorViewModel.dismissOwnedEditorFocus(
    id: Uuid,
    token: Uuid?,
): Boolean = dismissExpandedBlockOrClearSingleEmptyImpl(EditorInputOwner(id, token))

internal fun audioInputRuntimeKey(
    memoryId: Uuid,
    inputToken: Uuid?,
): String = if (inputToken == null) memoryId.toString() else "$memoryId:input:$inputToken"

internal fun EditorState.matchesFocusOwner(owner: EditorInputOwner?): Boolean =
    owner == null || (expandedBlockId == owner.id && creationInputTokens[owner.id] == owner.token)
