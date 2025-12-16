@file:OptIn(
    ExperimentalFoundationApi::class
)

package app.logdate.feature.editor.ui.content

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import app.logdate.feature.editor.ui.editor.EditorMode
import app.logdate.feature.editor.ui.editor.EditorMode.Companion.fromPageIndex
import app.logdate.feature.editor.ui.editor.EditorMode.Companion.toPageIndex
import app.logdate.feature.editor.ui.state.EditorNavigationState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A toggle component that allows switching between text and audio modes in the editor.
 * Only shown when there's no content yet.
 *
 * @param navigationState Specialized state for editor navigation
 * @param modifier Optional modifier for customization
 */
@Composable
fun EditorModeToggle(
    navigationState: EditorNavigationState,
    modifier: Modifier = Modifier
) {
    // Only show when we have no content (multiple modes available)
    if (navigationState.hasContent) return
    
    val pagerState = navigationState.pagerState
    val currentMode = fromPageIndex(pagerState.currentPage)
    val nextMode = when (currentMode) {
        EditorMode.TEXT -> EditorMode.AUDIO
        EditorMode.AUDIO -> EditorMode.TEXT
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = {
                navigationState.coroutineScope.launch {
                    pagerState.animateScrollToPage(nextMode.toPageIndex())
                }
            }
        ) {
            when (currentMode) {
                EditorMode.TEXT -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Switch to Audio Mode"
                    )
                }
                EditorMode.AUDIO -> {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Switch to Text Mode"
                    )
                }
            }
        }
    }
}