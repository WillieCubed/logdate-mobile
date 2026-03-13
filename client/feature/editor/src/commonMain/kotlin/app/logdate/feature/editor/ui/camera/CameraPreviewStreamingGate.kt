package app.logdate.feature.editor.ui.camera

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

internal suspend fun awaitFreshPreviewStreaming(previewStreaming: Flow<Boolean>) {
    val wasStreaming = previewStreaming.first()

    if (wasStreaming) {
        previewStreaming.first { !it }
    }

    previewStreaming.first { it }
}
