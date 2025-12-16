package app.logdate.feature.editor.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.editor.AutoSaveStatus
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Top toolbar for the note editor.
 * Provides navigation, saving functionality, and access to additional options.
 *
 * @param onBack Callback when the user navigates back
 * @param onSave Callback when the user saves the entry
 * @param onShowDrafts Callback when the user wants to view drafts
 * @param modifier Modifier for the toolbar
 * @param autoSaveStatus The current auto-save status (optional)
 */
@Composable
fun NoteEditorToolbar(
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShowDrafts: () -> Unit,
    modifier: Modifier = Modifier,
    autoSaveStatus: AutoSaveStatus? = null,
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left-aligned back button
        FilledTonalIconButton(
            onClick = { onBack() },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(
                Spacing.xs,
                alignment = Alignment.End,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Use the separate AutoSaveIndicator composable
            if (autoSaveStatus != null) {
                AutoSaveIndicator(
                    status = autoSaveStatus,
                    modifier = Modifier.padding(end = Spacing.xs)
                )
            }
            
            // Drafts button
            FilledTonalIconButton(
                onClick = { onShowDrafts() }
            ) {
                Icon(
                    imageVector = Icons.Default.Drafts,
                    contentDescription = "Load drafts"
                )
            }

            // Save button
            FilledTonalIconButton(
                onClick = { onSave() }
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save entry"
                )
            }

            // Menu button with additional options
            FilledTonalIconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options"
                )

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    // Additional menu options can be added here if needed
                    DropdownMenuItem(
                        text = { Text("Manage drafts") },
                        onClick = {
                            showMenu = false
                            onShowDrafts()
                        }
                    )
                }
            }
        }
    }
}

/**
 * A subtle indicator for displaying auto-save status.
 * Shows different UI elements based on the current save status.
 * The success indicator automatically disappears after a short delay.
 *
 * @param status The current auto-save status
 * @param modifier Modifier for the indicator
 * @param showDuration How long to show the success indicator before fading (in ms)
 */
@Composable
fun AutoSaveIndicator(
    status: AutoSaveStatus,
    modifier: Modifier = Modifier,
    showDuration: Long = 1500
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Show different indicators based on status
        when (status) {
            AutoSaveStatus.SAVED -> {
                // Create a temporary visibility state that disappears after a delay
                var visibleState by remember { mutableStateOf(true) }
                
                // Effect to hide the icon after a brief period
                LaunchedEffect(status) {
                    delay(showDuration) // Show for the specified duration
                    visibleState = false
                }
                
                // Show a subtle green checkmark that fades in and out
                AnimatedVisibility(
                    visible = visibleState,
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(500))
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Changes saved",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            AutoSaveStatus.ERROR -> {
                // Show error indicator with a temporary visibility state
                var visibleState by remember { mutableStateOf(true) }
                
                // Effect to hide the error icon after a delay
                LaunchedEffect(status) {
                    delay(showDuration * 2) // Show errors longer
                    visibleState = false
                }
                
                AnimatedVisibility(
                    visible = visibleState,
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(500))
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error saving",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            AutoSaveStatus.SAVING -> {
                // Very subtle "Saving..." text
                Text(
                    text = "Saving...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            else -> {
                // Don't show anything for IDLE state
            }
        }
    }
}