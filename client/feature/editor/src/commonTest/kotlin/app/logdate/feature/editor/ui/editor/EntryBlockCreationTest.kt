package app.logdate.feature.editor.ui.editor

import app.logdate.feature.editor.ui.handwriting.HandwritingPage
import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.InkPoint
import app.logdate.feature.postcards.model.InkTool
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.editor_error_recording_not_finalized
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class EntryBlockCreationTest {
    @Test
    fun blankPlaceholdersAllowChoicesAndClearingRestoresThem() {
        val text = TextBlockUiState(content = "A thought")
        assertFalse(text.isEmptyCreationBlock())
        for (block in listOf(
            text.copy(content = " \n\t"),
            ImageBlockUiState(),
            ImageBlockUiState(uri = ""),
            VideoBlockUiState(),
            CameraBlockUiState(),
            AudioBlockUiState(),
            HandwritingBlockUiState(),
        )) {
            assertTrue(block.isEmptyCreationBlock(), "Empty ${block::class} must allow creation choices")
        }
    }

    @Test
    fun captionsPendingFilesAndRecoveredMediaAreProtected() {
        for (block in listOf(
            ImageBlockUiState(caption = "Retain this"),
            ImageBlockUiState(uri = "content://recovered/image"),
            VideoBlockUiState(caption = "Retain this"),
            VideoBlockUiState(durationMs = 1000),
            CameraBlockUiState(pendingFilePath = "/synthetic/pending.jpg"),
            CameraBlockUiState(caption = "Retain this"),
            AudioBlockUiState(caption = "Retain this"),
            AudioBlockUiState(transcription = "Retain this"),
            AudioBlockUiState(startOnOpen = true),
            AudioBlockUiState(captureState = AudioCaptureState.Recording()),
            AudioBlockUiState(captureState = AudioCaptureState.Stopping()),
            AudioBlockUiState(captureState = AudioCaptureState.Ready("content://recovered/audio", 8000)),
            AudioBlockUiState(
                captureState = AudioCaptureState.Failed(Res.string.editor_error_recording_not_finalized, "/synthetic/recover.m4a"),
            ),
            HandwritingBlockUiState(
                page =
                    HandwritingPage(
                        strokes = listOf(CanvasElement.Ink(Uuid.random(), InkTool.PEN, "#263044", 3f, listOf(InkPoint(1f, 2f, 1f)))),
                    ),
            ),
        )) {
            assertFalse(block.isEmptyCreationBlock(), "Never replace retained ${block::class}")
        }
    }

    @Test
    fun replacingAnEmptyTargetIsOneStateChangeAtTheSameIndexWithFreshIdentity() {
        val before = TextBlockUiState(content = "Earlier retained text")
        val target = TextBlockUiState(timestamp = Instant.parse("2026-10-28T00:00:00Z"), timeZoneId = "Europe/London")
        val after = ImageBlockUiState(uri = "content://retained/image")
        val state = EditorState(blocks = listOf(before, target, after), expandedBlockId = target.id)
        val replacementId = Uuid.random()
        val changed = state.replaceEmptyCreationBlock(target.id, BlockType.HANDWRITING, replacementId)
        assertEquals(3, changed.blocks.size)
        assertSame(before, changed.blocks[0])
        assertSame(after, changed.blocks[2])
        val replaced = assertIs<HandwritingBlockUiState>(changed.blocks[1])
        assertEquals(replacementId, replaced.id)
        assertNotEquals(target.id, replaced.id, "A type change must not retrieve a retained audio session by old ID")
        assertEquals(target.timestamp, replaced.timestamp)
        assertEquals(target.timeZoneId, replaced.timeZoneId)
        assertEquals(replacementId, changed.expandedBlockId)
    }

    @Test
    fun staleTapsCannotReplaceContentRecordingSavedOrLockedTargets() {
        val target = TextBlockUiState()
        val focused = EditorState(blocks = listOf(target), expandedBlockId = target.id)
        for (state in listOf(
            focused.copy(blocks = listOf(target.copy(content = "Arrived since the frame rendered"))),
            focused.copy(isSaving = true),
            focused.copy(isEditingLocked = true),
            focused.copy(shouldExit = true),
            focused.copy(readOnlyBlocks = mapOf(target.id to true)),
            focused.copy(expandedBlockId = null),
            focused.copy(blocks = listOf(target, AudioBlockUiState(captureState = AudioCaptureState.Recording()))),
        )) {
            assertSame(state, state.replaceEmptyCreationBlock(target.id, BlockType.IMAGE, Uuid.random()))
        }
        assertSame(focused, focused.replaceEmptyCreationBlock(target.id, BlockType.TEXT, Uuid.random()), "Same mode is a no-op")
    }

    @Test
    fun replacementPreservesUnknownTimeZoneAndRejectsExistingIdentity() {
        val target = TextBlockUiState(timeZoneId = null)
        val retained = ImageBlockUiState(uri = "content://retained/image")
        val state = EditorState(blocks = listOf(target, retained), expandedBlockId = target.id)
        assertSame(state, state.replaceEmptyCreationBlock(target.id, BlockType.HANDWRITING, retained.id))
        val changed = state.replaceEmptyCreationBlock(target.id, BlockType.HANDWRITING, Uuid.random())
        assertEquals(null, changed.blocks.first().timeZoneId)
    }

    @Test
    fun addMemoryAppendsWithoutConvertingContentAndRejectsStaleOverviewActions() {
        val retained = TextBlockUiState(content = "Retain my text")
        val state = EditorState(blocks = listOf(retained))
        val id = Uuid.random()
        val next = state.beginEmptyCreationBlock(BlockType.HANDWRITING, id)
        assertEquals(2, next.blocks.size)
        assertSame(retained, next.blocks.first())
        assertIs<HandwritingBlockUiState>(next.blocks.last())
        assertEquals(id, next.expandedBlockId)
        assertSame(state, state.beginEmptyCreationBlock(BlockType.IMAGE, retained.id))
        assertSame(state, state.beginEmptyCreationBlock(BlockType.IMAGE, id, onlyEmptyEntry = true))
        for (blocked in listOf(
            state.copy(isSaving = true),
            state.copy(isEditingLocked = true),
            state.copy(shouldExit = true),
            state.copy(expandedBlockId = retained.id),
        )) {
            assertSame(blocked, blocked.beginEmptyCreationBlock(BlockType.IMAGE, id))
        }
    }

    @Test
    fun pendingAudioStartProtectsTheTargetAndRejectsStaleAddActions() {
        val target = AudioBlockUiState()
        val reserved =
            EditorState(
                blocks = listOf(target),
                expandedBlockId = target.id,
                audioCaptureStartReservations = mapOf(target.id to Uuid.random()),
            )
        assertSame(reserved, reserved.replaceEmptyCreationBlock(target.id, BlockType.TEXT, Uuid.random()))
        val overview = reserved.copy(expandedBlockId = null)
        assertSame(overview, overview.beginEmptyCreationBlock(BlockType.TEXT, Uuid.random()))
        assertEquals(AudioCaptureState.Empty, target.captureState, "Pending admission is not a durable recording")
    }
}
