package app.logdate.feature.editor.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import app.logdate.client.datastore.EntryStartMode
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.ui.platform.PlatformIcons
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.camera
import logdate.client.feature.editor.generated.resources.gallery
import logdate.client.feature.editor.generated.resources.ink_handwrite_action
import logdate.client.feature.editor.generated.resources.memory_audio
import logdate.client.feature.editor.generated.resources.memory_text
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EntryStartMode.label(): String =
    stringResource(
        when (this) {
            EntryStartMode.TEXT -> Res.string.memory_text
            EntryStartMode.HANDWRITING -> Res.string.ink_handwrite_action
            EntryStartMode.CAMERA -> Res.string.camera
            EntryStartMode.AUDIO -> Res.string.memory_audio
            EntryStartMode.GALLERY -> Res.string.gallery
        },
    )

@Composable
internal fun EntryStartMode.icon(): Painter =
    when (this) {
        EntryStartMode.TEXT -> PlatformIcons.text()
        EntryStartMode.HANDWRITING -> PlatformIcons.edit()
        EntryStartMode.CAMERA -> PlatformIcons.camera()
        EntryStartMode.AUDIO -> PlatformIcons.mic()
        EntryStartMode.GALLERY -> PlatformIcons.library()
    }

internal fun EntryStartMode.blockType(): BlockType =
    when (this) {
        EntryStartMode.TEXT -> BlockType.TEXT
        EntryStartMode.HANDWRITING -> BlockType.HANDWRITING
        EntryStartMode.CAMERA -> BlockType.CAMERA
        EntryStartMode.AUDIO -> BlockType.AUDIO
        EntryStartMode.GALLERY -> BlockType.IMAGE
    }

internal fun EntryStartMode.actionTag(): String =
    when (this) {
        EntryStartMode.TEXT -> "editor_start_text_block"
        EntryStartMode.HANDWRITING -> "editor_start_handwriting_block"
        EntryStartMode.CAMERA -> "editor_start_camera_block"
        EntryStartMode.AUDIO -> "editor_start_audio_block"
        EntryStartMode.GALLERY -> "editor_start_photo_block"
    }
