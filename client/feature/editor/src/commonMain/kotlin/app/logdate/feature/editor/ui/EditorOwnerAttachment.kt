package app.logdate.feature.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import kotlin.uuid.Uuid

/** Optional lifecycle observation used to verify that motion never reparents a live input owner. */
internal val LocalEditorOwnerAttachmentObserver = compositionLocalOf<((Uuid, Boolean) -> Unit)?> { null }

@Composable
internal fun editorOwnerAttachmentProbe(id: Uuid): Modifier {
    val observer = LocalEditorOwnerAttachmentObserver.current ?: return Modifier
    return OwnerAttachmentElement(id, observer)
}

private data class OwnerAttachmentElement(
    val id: Uuid,
    val observer: (Uuid, Boolean) -> Unit,
) : ModifierNodeElement<OwnerAttachmentNode>() {
    override fun create() = OwnerAttachmentNode(id, observer)

    override fun update(node: OwnerAttachmentNode) {
        node.id = id
        node.observer = observer
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "editorOwnerAttachment"
    }
}

private class OwnerAttachmentNode(
    var id: Uuid,
    var observer: (Uuid, Boolean) -> Unit,
) : Modifier.Node() {
    override fun onAttach() = observer(id, true)

    override fun onDetach() = observer(id, false)
}
