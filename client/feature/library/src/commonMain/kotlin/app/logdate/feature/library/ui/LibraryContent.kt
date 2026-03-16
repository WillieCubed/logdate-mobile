@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.library.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.feature.library.ui.components.MediaThumbnailGrid
import app.logdate.ui.common.adaptivePanelShape
import kotlin.uuid.Uuid

/**
 * The main content surface for the library.
 *
 * Uses `surface` color so the panel sits visually above the shell background.
 * The same panel renders for all states (loading, empty, content) so layout is
 * consistent regardless of data availability.
 */
@Composable
fun LibraryPanel(
    state: LibraryUiState,
    columnCount: Int,
    onItemClick: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = adaptivePanelShape(maxWidth, maxHeight),
        ) {
            val showLoading = state is LibraryUiState.Loading
            LibraryLoadingPlaceholder(isVisible = showLoading)
            AnimatedVisibility(
                visible = !showLoading,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200)),
            ) {
                Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                    when (state) {
                        is LibraryUiState.Loading -> {
                            // Handled by placeholder above
                        }

                        is LibraryUiState.PermissionRequired -> {
                            LibraryEmptyState(
                                headline = "Let's get started",
                                body = "Grant photo and video access so your library can show your memories.",
                            )
                        }

                        is LibraryUiState.Empty -> {
                            LibraryEmptyState(
                                headline = "Your memories live here",
                                body = "Photos and videos you capture in LogDate will show up in your library. Go snap something!",
                            )
                        }

                        is LibraryUiState.Content -> {
                            MediaThumbnailGrid(
                                groups = state.groups,
                                columnCount = columnCount,
                                onItemClick = onItemClick,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Placeholder shimmer shown while the library is loading.
 */
@Composable
private fun LibraryLoadingPlaceholder(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        // Empty for now — can be replaced with a shimmer grid
    }
}

/**
 * Empty state with a friendly illustration and message, displayed inside the library panel.
 */
@Composable
private fun LibraryEmptyState(
    headline: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(80.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier =
                    Modifier
                        .padding(20.dp)
                        .size(40.dp),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
