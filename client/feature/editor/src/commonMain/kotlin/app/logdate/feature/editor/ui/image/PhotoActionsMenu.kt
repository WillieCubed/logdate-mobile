package app.logdate.feature.editor.ui.image

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.shared.model.PhotoPresentation
import app.logdate.ui.platform.PlatformIcons
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.delete_image
import logdate.client.feature.editor.generated.resources.more_options
import logdate.client.feature.editor.generated.resources.photo_edge_to_edge
import logdate.client.feature.editor.generated.resources.photo_framed
import org.jetbrains.compose.resources.stringResource

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun PhotoActionsMenu(
    block: ImageBlockUiState,
    onBlockUpdated: (ImageBlockUiState) -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.padding(8.dp)) {
        FilledIconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("photo_actions"),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.6f), contentColor = Color.White),
        ) {
            Icon(PlatformIcons.more(), stringResource(Res.string.more_options))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
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
                    trailingIcon = {
                        if (block.presentation == presentation) Icon(PlatformIcons.check(), contentDescription = null)
                    },
                    onClick = {
                        onBlockUpdated(block.copy(presentation = presentation))
                        expanded = false
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.delete_image)) },
                leadingIcon = { Icon(PlatformIcons.delete(), contentDescription = null) },
                onClick = {
                    expanded = false
                    onDeleteRequested()
                },
            )
        }
    }
}
