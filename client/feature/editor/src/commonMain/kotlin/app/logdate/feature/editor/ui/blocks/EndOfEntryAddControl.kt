package app.logdate.feature.editor.ui.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.ui.platform.PlatformIcons
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.close
import logdate.client.feature.editor.generated.resources.memory_add
import logdate.client.feature.editor.generated.resources.memory_add_photo
import logdate.client.feature.editor.generated.resources.memory_add_video
import logdate.client.feature.editor.generated.resources.memory_record_audio
import logdate.client.feature.editor.generated.resources.memory_take_photo
import logdate.client.feature.editor.generated.resources.memory_write
import org.jetbrains.compose.resources.stringResource

/** The final item in the entry; a continued pull reveals every block type in place. */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun EndOfEntryAddControl(
    progress: Float,
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onAdd: (BlockType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = progress.coerceIn(0f, 1f)
    val gatedAdd: (BlockType) -> Unit = { type -> if (expanded && fraction > 0.95f) onAdd(type) }
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        val wide = maxWidth >= 560.dp
        val choiceHeight = if (wide) 96.dp else 176.dp
        val width = 180.dp + (maxWidth - 180.dp) * fraction
        Surface(
            modifier =
                Modifier
                    .width(width)
                    .height(56.dp + choiceHeight * fraction)
                    .testTag("add_memory_surface"),
            shape =
                RoundedCornerShape(
                    topStart = 28.dp - 12.dp * fraction,
                    topEnd = 28.dp - 12.dp * fraction,
                    bottomStart = 28.dp - 12.dp * fraction,
                    bottomEnd = 28.dp - 12.dp * fraction,
                ),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(Modifier.fillMaxSize()) {
                Column {
                    Box(Modifier.fillMaxWidth().height(8.dp).alpha(fraction), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f), CircleShape),
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("add_to_entry")
                            .clickable(enabled = !expanded && fraction < 0.05f, onClick = onExpand)
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(24.dp * (1f - fraction)).height(24.dp).alpha(1f - fraction)) {
                            Icon(PlatformIcons.add(), null)
                        }
                        Spacer(Modifier.width(8.dp * (1f - fraction)))
                        Text(
                            stringResource(Res.string.memory_add),
                            modifier = Modifier.weight(1f).testTag("add_to_entry_label"),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Box(
                            Modifier
                                .width(48.dp * fraction)
                                .height(48.dp)
                                .clipToBounds()
                                .alpha(fraction),
                        ) {
                            IconButton(
                                onClick = onCollapse,
                                enabled = expanded && fraction > 0.95f,
                                modifier = Modifier.size(48.dp).testTag("add_close"),
                            ) {
                                Icon(PlatformIcons.close(), contentDescription = stringResource(Res.string.close))
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().alpha(((fraction - 0.18f) / 0.72f).coerceIn(0f, 1f))) {
                        if (wide) {
                            Row(
                                Modifier.fillMaxWidth().height(choiceHeight).padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CreationChoice(
                                    BlockType.TEXT,
                                    stringResource(Res.string.memory_write),
                                    PlatformIcons.text(),
                                    gatedAdd,
                                    Modifier.weight(1f),
                                )
                                CreationChoice(
                                    BlockType.IMAGE,
                                    stringResource(Res.string.memory_add_photo),
                                    PlatformIcons.photoLibrary(),
                                    gatedAdd,
                                    Modifier.weight(1f),
                                )
                                CreationChoice(
                                    BlockType.AUDIO,
                                    stringResource(Res.string.memory_record_audio),
                                    PlatformIcons.mic(),
                                    gatedAdd,
                                    Modifier.weight(1f),
                                )
                                CreationChoice(
                                    BlockType.VIDEO,
                                    stringResource(Res.string.memory_add_video),
                                    PlatformIcons.videoFile(),
                                    gatedAdd,
                                    Modifier.weight(1f),
                                )
                                CreationChoice(
                                    BlockType.CAMERA,
                                    stringResource(Res.string.memory_take_photo),
                                    PlatformIcons.camera(),
                                    gatedAdd,
                                    Modifier.weight(1f),
                                )
                            }
                        } else {
                            Column(Modifier.fillMaxWidth().height(choiceHeight).padding(horizontal = 12.dp)) {
                                Row(Modifier.fillMaxWidth().height(88.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CreationChoice(
                                        BlockType.TEXT,
                                        stringResource(Res.string.memory_write),
                                        PlatformIcons.text(),
                                        gatedAdd,
                                        Modifier.weight(1f),
                                    )
                                    CreationChoice(
                                        BlockType.IMAGE,
                                        stringResource(Res.string.memory_add_photo),
                                        PlatformIcons.photoLibrary(),
                                        gatedAdd,
                                        Modifier.weight(1f),
                                    )
                                    CreationChoice(
                                        BlockType.AUDIO,
                                        stringResource(Res.string.memory_record_audio),
                                        PlatformIcons.mic(),
                                        gatedAdd,
                                        Modifier.weight(1f),
                                    )
                                }
                                Row(Modifier.fillMaxWidth().height(88.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CreationChoice(
                                        BlockType.VIDEO,
                                        stringResource(Res.string.memory_add_video),
                                        PlatformIcons.videoFile(),
                                        gatedAdd,
                                        Modifier.weight(1f),
                                    )
                                    CreationChoice(
                                        BlockType.CAMERA,
                                        stringResource(Res.string.memory_take_photo),
                                        PlatformIcons.camera(),
                                        gatedAdd,
                                        Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun CreationChoice(
    type: BlockType,
    label: String,
    icon: Painter,
    onAdd: (BlockType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .height(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onAdd(type) }
                .testTag("add_memory_$type"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(44.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
