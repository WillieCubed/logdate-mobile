@file:OptIn(ExperimentalMaterial3Api::class)

package app.logdate.feature.editor.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.ui.common.SwipeToAction
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeShort
import kotlin.uuid.Uuid

/**
 * Material You style dialog for viewing and managing previous drafts.
 */
@Composable
fun DraftsListDialog(
    drafts: List<EntryDraft>,
    isLoading: Boolean = false,
    onDismiss: () -> Unit,
    onDraftSelected: (EntryDraft) -> Unit,
    onDraftDeleted: (Uuid) -> Unit,
    onDeleteAllDrafts: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        ),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(520.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Previous Drafts",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Row {
                        // Only show delete all button if there are drafts to delete
                        if (drafts.isNotEmpty() && !isLoading) {
                            IconButton(
                                onClick = onDeleteAllDrafts,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete All Drafts",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Content
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Loading drafts...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    drafts.isEmpty() -> {
                        DraftsEmptyState()
                    }

                    else -> {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            LazyColumn(
                                modifier = Modifier.padding(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                items(
                                    items = drafts.sortedByDescending { it.updatedAt },
                                    key = { it.id }
                                ) { draft ->
                                    SwipeToDeleteDraftItem(
                                        draft = draft,
                                        onDraftSelected = { onDraftSelected(draft) },
                                        onDraftDeleted = { onDraftDeleted(draft.id) },
                                        modifier = Modifier.fillMaxWidth()
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

/**
 * Empty state displayed when no drafts are available.
 * Shows a message with an icon encouraging the user to start writing.
 */
@Composable
private fun DraftsEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No drafts found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Start writing to create your first draft.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SwipeToDeleteDraftItem(
    draft: EntryDraft,
    onDraftSelected: () -> Unit,
    onDraftDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Use our reusable SwipeToAction component
    SwipeToAction(
        onAction = onDraftDeleted,
        actionLabel = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Delete",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        content = {
            // Make the content clickable independently of the swipe action
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onDraftSelected() }
                    .padding(Spacing.md)
            ) {
                DraftListItemContent(
                    draft = draft,
                    modifier = Modifier
                )
            }
        },
        modifier = modifier
            .height(80.dp),
        contentShape = MaterialTheme.shapes.extraLarge,
        actionShape = MaterialTheme.shapes.extraLarge,
        spacing = Spacing.sm
    )
}

@Composable
private fun DraftListItemContent(
    draft: EntryDraft,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Draft preview
        val previewText = draft.notes.firstOrNull()?.let { note ->
            when (note) {
                is JournalNote.Text -> note.content
                is JournalNote.Image -> "📷 Image"
                is JournalNote.Video -> "🎥 Video"
                is JournalNote.Audio -> "🎵 Audio"
                else -> "Content"
            }
        } ?: "Empty draft"

        Text(
            text = previewText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Timestamp and note count
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = draft.updatedAt.toReadableDateTimeShort(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (draft.notes.size > 1) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "• ${draft.notes.size} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}