@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.audio.MomentAudioCard
import logdate.client.feature.location.timeline.generated.resources.Res
import logdate.client.feature.location.timeline.generated.resources.history_add_memory
import logdate.client.feature.location.timeline.generated.resources.history_audio_memory
import logdate.client.feature.location.timeline.generated.resources.history_details
import logdate.client.feature.location.timeline.generated.resources.history_gap
import logdate.client.feature.location.timeline.generated.resources.history_image_memory
import logdate.client.feature.location.timeline.generated.resources.history_journey
import logdate.client.feature.location.timeline.generated.resources.history_open_memory
import logdate.client.feature.location.timeline.generated.resources.history_text_memory
import logdate.client.feature.location.timeline.generated.resources.history_video_memory
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HistoryTimelineItem(
    item: HistoryItemUi,
    isSelected: Boolean,
    actions: HumanLocationHistoryActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val selectionColor = colors.primary
    Surface(
        onClick = {
            actions.onSelectItem(item.id)
            if (item.kind != HistoryItemKind.Visit) actions.onOpenDetail(item.id)
        },
        modifier =
            modifier.fillMaxWidth().semantics { selected = isSelected }.drawWithContent {
                drawContent()
                if (isSelected) {
                    drawLine(selectionColor, Offset(1.5.dp.toPx(), 0f), Offset(1.5.dp.toPx(), size.height), 3.dp.toPx())
                }
            },
        color = if (isSelected) colors.secondaryContainer.copy(alpha = 0.45f) else Color.Transparent,
    ) {
        if (item.kind == HistoryItemKind.Visit) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(item.timeLabel, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                if (item.supportingText.isNotBlank()) {
                    Text(item.supportingText, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                item.memories.forEach { HistoryMemoryPreview(it, actions.onOpenMemory) }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (item.memories.isEmpty()) {
                        TextButton(onClick = { actions.onEdit(item.id, HistoryEditAction.AddMemory) }) {
                            Text(stringResource(Res.string.history_add_memory))
                        }
                    }
                    TextButton(onClick = { actions.onOpenDetail(item.id) }) {
                        Text(stringResource(Res.string.history_details))
                    }
                }
            }
        } else {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val journey = item.kind == HistoryItemKind.Journey
                Icon(
                    if (journey) Icons.Default.Route else Icons.Default.MoreHoriz,
                    stringResource(if (journey) Res.string.history_journey else Res.string.history_gap),
                    tint = colors.onSurfaceVariant,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        listOf(item.title, item.supportingText).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(item.timeLabel, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun HistoryMemoryPreview(
    memory: HistoryMemoryUi,
    onOpenMemory: (String) -> Unit,
) {
    if (memory.kind == HistoryMemoryKind.Audio && memory.audio != null) {
        Column(Modifier.fillMaxWidth()) {
            MomentAudioCard(audio = memory.audio, timeOfDay = null, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { onOpenMemory(memory.id) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(Res.string.history_open_memory))
            }
        }
        return
    }
    OutlinedCard(onClick = { onOpenMemory(memory.id) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon =
                when (memory.kind) {
                    HistoryMemoryKind.Text -> Icons.AutoMirrored.Filled.Notes
                    HistoryMemoryKind.Image -> Icons.Default.Photo
                    HistoryMemoryKind.Audio -> Icons.Default.Mic
                    HistoryMemoryKind.Video -> Icons.Default.Videocam
                }
            if (memory.thumbnailUri != null) {
                HistoryMemoryThumbnail(memory.thumbnailUri, memory.kind == HistoryMemoryKind.Video)
            } else {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val type =
                    when (memory.kind) {
                        HistoryMemoryKind.Text -> Res.string.history_text_memory
                        HistoryMemoryKind.Image -> Res.string.history_image_memory
                        HistoryMemoryKind.Audio -> Res.string.history_audio_memory
                        HistoryMemoryKind.Video -> Res.string.history_video_memory
                    }
                Text(stringResource(type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    memory.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = if (memory.kind == HistoryMemoryKind.Text) 4 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (memory.supportingText.isNotBlank()) {
                    Text(memory.supportingText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
