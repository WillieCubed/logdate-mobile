package app.logdate.feature.editor.ui.editor

import kotlin.time.Clock
import kotlin.uuid.Uuid

/** A creation choice may replace only a placeholder that has no user content or recovery intent. */
internal fun EntryBlockUiState.isEmptyCreationBlock(): Boolean =
    when (this) {
        is TextBlockUiState -> content.isBlank()
        is HandwritingBlockUiState -> page.strokes.isEmpty()
        is AudioBlockUiState -> captureState == AudioCaptureState.Empty && caption.isBlank() && transcription.isBlank() && !startOnOpen
        is CameraBlockUiState -> uri.isNullOrBlank() && pendingFilePath.isNullOrBlank() && caption.isBlank() && durationMs == 0L
        is VideoBlockUiState -> uri.isNullOrBlank() && caption.isBlank() && durationMs == 0L
        is ImageBlockUiState -> uri.isNullOrBlank() && caption.isBlank()
    }

internal fun EntryBlockUiState.creationType(): BlockType =
    when (this) {
        is TextBlockUiState -> BlockType.TEXT
        is HandwritingBlockUiState -> BlockType.HANDWRITING
        is ImageBlockUiState -> BlockType.IMAGE
        is AudioBlockUiState -> BlockType.AUDIO
        is CameraBlockUiState -> BlockType.CAMERA
        is VideoBlockUiState -> BlockType.VIDEO
    }

private fun EditorState.canMutateCreation(): Boolean =
    !isEditingLocked &&
        !isSaving &&
        !shouldExit &&
        !isRecordingAudio &&
        !disableEmptyBlockCreation &&
        audioCaptureStartReservations.isEmpty()

/** Called inside the editor CAS mutation, so a stale animation-frame tap cannot replace new content. */
internal fun EditorState.replaceEmptyCreationBlock(
    targetId: Uuid,
    type: BlockType,
    replacementId: Uuid,
): EditorState {
    if (!canMutateCreation() || expandedBlockId != targetId || isReadOnly(targetId) || blocks.any { it.id == replacementId }) return this
    val target = blocks.firstOrNull { it.id == targetId } ?: return this
    if (!target.isEmptyCreationBlock() || target.creationType() == type) return this
    val replacement = emptyCreationBlock(type, replacementId, target)
    return copy(
        blocks = blocks.map { if (it.id == targetId) replacement else it },
        expandedBlockId = replacement.id,
        isModified = true,
    )
}

internal fun EditorState.beginEmptyCreationBlock(
    type: BlockType,
    id: Uuid,
    onlyEmptyEntry: Boolean = false,
): EditorState {
    if (!canMutateCreation() ||
        expandedBlockId != null ||
        (onlyEmptyEntry && blocks.isNotEmpty()) ||
        blocks.any { it.id == id }
    ) {
        return this
    }
    val block = emptyCreationBlock(type, id)
    return copy(blocks = blocks + block, expandedBlockId = id, isModified = true)
}

private fun emptyCreationBlock(
    type: BlockType,
    id: Uuid,
    seed: EntryBlockUiState? = null,
): EntryBlockUiState {
    val timestamp = seed?.timestamp ?: Clock.System.now()
    val location = seed?.location
    val timeZone =
        if (seed != null) {
            seed.timeZoneId
        } else {
            app.logdate.client.repository.journals.SystemCaptureTimeZone
                .currentTimeZoneId()
        }
    return when (type) {
        BlockType.TEXT -> TextBlockUiState(id = id, timestamp = timestamp, location = location, timeZoneId = timeZone)
        BlockType.HANDWRITING -> HandwritingBlockUiState(id = id, timestamp = timestamp, location = location, timeZoneId = timeZone)
        BlockType.IMAGE -> ImageBlockUiState(id = id, timestamp = timestamp, location = location, timeZoneId = timeZone)
        BlockType.AUDIO -> AudioBlockUiState(id = id, timestamp = timestamp, location = location, timeZoneId = timeZone)
        BlockType.CAMERA -> CameraBlockUiState(id = id, timestamp = timestamp, location = location, timeZoneId = timeZone)
        BlockType.VIDEO -> VideoBlockUiState(id = id, timestamp = timestamp, location = location, timeZoneId = timeZone)
    }
}

internal fun EntryEditorViewModel.chooseCreationTypeImpl(
    targetId: Uuid?,
    type: BlockType,
) {
    if (editorState.value.isLoading) return
    if (targetId != null && editorState.value.isReadOnly(targetId)) return
    val replacementId = Uuid.random()
    val changed =
        mutateEditorContent { current ->
            if (targetId == null) {
                current.beginEmptyCreationBlock(type, replacementId, onlyEmptyEntry = true)
            } else {
                current.replaceEmptyCreationBlock(targetId, type, replacementId)
            }
        }
    if (changed && targetId != null) audioBlockFinalizers.remove(targetId)
}

internal fun EntryEditorViewModel.beginBlockCreationImpl(type: BlockType) {
    if (editorState.value.isLoading) return
    val id = Uuid.random()
    mutateEditorContent { it.beginEmptyCreationBlock(type, id) }
}
