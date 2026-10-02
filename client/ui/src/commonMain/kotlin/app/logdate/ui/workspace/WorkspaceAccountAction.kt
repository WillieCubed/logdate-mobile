@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.WorkspaceSurface
import app.logdate.ui.theme.workspaceContainer
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.workspace_account_label
import org.jetbrains.compose.resources.stringResource

/** One account target; background work is a quiet marker and details stay on demand. */
@Composable
fun WorkspaceAccountAction(
    indicator: WorkspaceAccountIndicator,
    statusDescription: String?,
    modifier: Modifier = Modifier,
    menu: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(Res.string.workspace_account_label)
    Box(modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier =
                Modifier.testTag("workspace_account").semantics {
                    contentDescription = label
                    statusDescription?.let { stateDescription = it }
                },
        ) {
            Box(Modifier.size(32.dp)) {
                Icon(Icons.Default.AccountCircle, null, Modifier.matchParentSize())
                if (indicator != WorkspaceAccountIndicator.None) {
                    Icon(
                        when (indicator) {
                            WorkspaceAccountIndicator.Working -> Icons.Default.Sync
                            WorkspaceAccountIndicator.Waiting -> Icons.Default.CloudUpload
                            else -> Icons.Default.ErrorOutline
                        },
                        contentDescription = null,
                        tint =
                            if (indicator ==
                                WorkspaceAccountIndicator.Attention
                            ) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp)
                                .background(MaterialTheme.colorScheme.workspaceContainer(WorkspaceSurface.Canvas), CircleShape)
                                .padding(2.dp),
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 240.dp, max = 360.dp),
            containerColor = MaterialTheme.colorScheme.workspaceContainer(WorkspaceSurface.Raised),
            shape = MaterialTheme.shapes.medium,
        ) {
            menu { expanded = false }
        }
    }
}
