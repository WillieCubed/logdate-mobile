package app.logdate.feature.editor.ui.content

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.feature.editor.ui.layout.ExpandableContentToolbar
import app.logdate.feature.editor.ui.layout.OverscrollDetector
import app.logdate.feature.editor.ui.layout.rememberOverscrollDetector
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A footer component for the editor content that includes an expandable toolbar
 * which appears when the user overscrolls past the bottom of the content.
 *
 * @param scrollState The scroll state of the editor content
 * @param onAddBlock Callback when a new block is added
 */
@Composable
fun EditorContentFooter(
    scrollState: ScrollState,
    onAddBlock: (BlockType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // Create the overscroll detector
    val overscrollDetector = rememberOverscrollDetector(
        scrollState = scrollState,
        onOverscrollReleased = { amount, threshold ->
            // When overscroll is released, snap to expanded/collapsed state
            if (amount >= threshold) {
                // Past threshold - expand fully
                expanded = true
                Napier.d("Toolbar expanded")
            } else {
                // Below threshold - collapse
                expanded = false
                Napier.d("Toolbar collapsed")
            }
        }
    )
    
    // Auto-collapse after a period of inactivity when expanded
    LaunchedEffect(expanded) {
        if (expanded) {
            // Wait for 5 seconds then collapse
            delay(5000)
            expanded = false
            Napier.d("Toolbar auto-collapsed after timeout")
        }
    }
    
    // Create more playful progress animation with bouncier springs
    val animatedProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else overscrollDetector.progressFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,    // Very bouncy
            stiffness = Spring.StiffnessVeryLow            // Even slower for more playfulness
        ),
        label = "Progress animation"
    )
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Add some spacing above the toolbar
        Spacer(modifier = Modifier.height(8.dp))
        
        // Expandable toolbar
        ExpandableContentToolbar(
            progress = animatedProgress,
            expanded = expanded,
        ) {
            // Toolbar content - block type buttons
            BlockTypeButton(
                icon = Icons.Rounded.TextFields,
                contentDescription = "Add Text",
                onClick = {
                    onAddBlock(BlockType.TEXT)
                    collapseToolbar(coroutineScope, expanded) { expanded = false }
                }
            )
            
            BlockTypeButton(
                icon = Icons.Rounded.Image,
                contentDescription = "Add Image",
                onClick = {
                    onAddBlock(BlockType.IMAGE)
                    collapseToolbar(coroutineScope, expanded) { expanded = false }
                }
            )
            
            BlockTypeButton(
                icon = Icons.Rounded.AudioFile,
                contentDescription = "Add Audio",
                onClick = {
                    onAddBlock(BlockType.AUDIO)
                    collapseToolbar(coroutineScope, expanded) { expanded = false }
                }
            )
            
            BlockTypeButton(
                icon = Icons.Rounded.VideoFile,
                contentDescription = "Add Video",
                onClick = {
                    onAddBlock(BlockType.VIDEO)
                    collapseToolbar(coroutineScope, expanded) { expanded = false }
                }
            )
            
            BlockTypeButton(
                icon = Icons.Rounded.Camera,
                contentDescription = "Take Photo",
                onClick = {
                    onAddBlock(BlockType.CAMERA)
                    collapseToolbar(coroutineScope, expanded) { expanded = false }
                }
            )
        }
        
        // Bottom spacing to ensure there's room for overscroll
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * A button that appears in the expandable toolbar.
 */
@Composable
private fun BlockTypeButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = modifier
            .padding(horizontal = 8.dp)
            .size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * Helper function to collapse the toolbar with a short delay
 * Allows animations to complete before collapsing
 */
private fun collapseToolbar(
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    currentlyExpanded: Boolean,
    onCollapse: () -> Unit
) {
    if (currentlyExpanded) {
        coroutineScope.launch {
            // Longer delay to allow for more bouncy animations to complete
            delay(500) // Extended delay for better playful animation experience
            onCollapse()
        }
    }
}