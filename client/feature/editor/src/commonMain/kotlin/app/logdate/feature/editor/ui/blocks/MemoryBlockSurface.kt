package app.logdate.feature.editor.ui.blocks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.CameraBlockUiState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.editor.VideoBlockUiState
import app.logdate.shared.model.PhotoPresentation
import app.logdate.ui.platform.PlatformIcons
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.memory_audio
import logdate.client.feature.editor.generated.resources.memory_camera
import logdate.client.feature.editor.generated.resources.memory_edit
import logdate.client.feature.editor.generated.resources.memory_photo
import logdate.client.feature.editor.generated.resources.memory_remove
import logdate.client.feature.editor.generated.resources.memory_text
import logdate.client.feature.editor.generated.resources.memory_video
import logdate.client.feature.editor.generated.resources.more_options
import logdate.client.feature.editor.generated.resources.photo_edge_to_edge
import logdate.client.feature.editor.generated.resources.photo_framed
import org.jetbrains.compose.resources.stringResource

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun MemoryBlockSurface(
    block: EntryBlockUiState,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onUpdate: (EntryBlockUiState) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = onSelect,
    content: @Composable () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val framed = block is ImageBlockUiState && block.presentation == PhotoPresentation.Framed
    val surfaceColor by animateColorAsState(
        if (framed) Color.White else MaterialTheme.colorScheme.surfaceContainer,
        label = "memorySurfaceColor",
    )
    val photoTopInset by animateDpAsState(if (framed) 12.dp else 0.dp, label = "photoTopInset")
    Surface(
        modifier =
            modifier
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                .fillMaxWidth()
                .testTag(
                    "memory_block_${block.id}",
                ).semantics { selected = isSelected }
                .clickable(onClick = onSelect),
        shape = MaterialTheme.shapes.medium,
        color = surfaceColor,
        contentColor = if (framed) Color.Black else MaterialTheme.colorScheme.onSurface,
    ) {
        Box {
            val contentInsets =
                when (block) {
                    is TextBlockUiState, is AudioBlockUiState -> Modifier.padding(end = 48.dp)
                    is ImageBlockUiState -> Modifier.padding(top = photoTopInset)
                    else -> Modifier
                }
            Column(contentInsets) { content() }
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                val overMedia = block is ImageBlockUiState || block is VideoBlockUiState || block is CameraBlockUiState
                IconButton(
                    onClick = {
                        onSelect()
                        menuOpen = true
                    },
                    modifier = Modifier.testTag("block_menu_${block.id}"),
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = if (overMedia) Color.Black.copy(alpha = 0.45f) else Color.Transparent,
                            contentColor = if (overMedia) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                ) {
                    Icon(PlatformIcons.more(), stringResource(Res.string.more_options))
                }
                DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(Res.string.memory_edit)) }, onClick = {
                        menuOpen = false
                        onEdit()
                    })
                    if (block is ImageBlockUiState) {
                        PhotoPresentation.entries.forEach { presentation ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (presentation ==
                                                PhotoPresentation.Framed
                                            ) {
                                                Res.string.photo_framed
                                            } else {
                                                Res.string.photo_edge_to_edge
                                            },
                                        ),
                                    )
                                },
                                trailingIcon = { if (block.presentation == presentation) Icon(PlatformIcons.check(), null) },
                                onClick = {
                                    onUpdate(block.copy(presentation = presentation))
                                    menuOpen = false
                                },
                            )
                        }
                    }
                    DropdownMenuItem(text = { Text(stringResource(Res.string.memory_remove)) }, onClick = {
                        menuOpen = false
                        onRemove()
                    })
                }
            }
        }
    }
}

@Composable
internal fun memoryBlockLabel(block: EntryBlockUiState): String =
    stringResource(
        when (block) {
            is TextBlockUiState -> Res.string.memory_text
            is ImageBlockUiState -> Res.string.memory_photo
            is AudioBlockUiState -> Res.string.memory_audio
            is VideoBlockUiState -> Res.string.memory_video
            is CameraBlockUiState -> Res.string.memory_camera
        },
    )
