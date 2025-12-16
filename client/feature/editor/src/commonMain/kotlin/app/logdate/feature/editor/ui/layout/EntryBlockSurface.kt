package app.logdate.feature.editor.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.feature.editor.ui.common.CardSurface

/**
 * A surface container for entry blocks that provides consistent styling and behavior.
 * This component uses the shared CardSurface component under the hood.
 *
 * @param isExpanded Whether the block is expanded/focused
 * @param isSelected Whether the block is selected (shows highlight)
 * @param onClick Callback when the surface is clicked
 * @param modifier Modifier for customizing the surface
 * @param content The content to display inside the surface
 */
@Composable
fun EntryBlockSurface(
    isExpanded: Boolean,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    CardSurface(
        isFocused = isExpanded,
        isSelected = isSelected,
        onClick = onClick,
        modifier = modifier,
        content = content
    )
}