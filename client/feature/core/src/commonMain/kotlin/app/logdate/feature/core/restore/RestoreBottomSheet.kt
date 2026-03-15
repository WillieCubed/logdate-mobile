@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.restore

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.cancel
import logdate.client.feature.core.generated.resources.import_backup
import logdate.client.feature.core.generated.resources.restore_choose_file
import logdate.client.feature.core.generated.resources.restore_confirm_description
import logdate.client.feature.core.generated.resources.restore_merge_info
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RestoreBottomSheet(
    restoreState: RestoreState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .navigationBarsPadding(),
        ) {
            AnimatedContent(
                targetState = restoreState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "restore-sheet-state",
            ) { state ->
                when (state) {
                    is RestoreState.Confirming -> {
                        RestoreConfirmContent(
                            onConfirm = onConfirm,
                            onDismiss = onDismiss,
                        )
                    }
                    is RestoreState.Selecting -> {
                        RestoreConfirmContent(
                            onConfirm = onConfirm,
                            onDismiss = onDismiss,
                            buttonsEnabled = false,
                        )
                    }
                    is RestoreState.Restoring -> {
                        RestoreProgressCard(
                            onCancel = onCancel,
                        )
                    }
                    is RestoreState.Completed -> {
                        RestoreSuccessCard(
                            summary = state.summary,
                            onDismiss = onDismiss,
                        )
                    }
                    is RestoreState.Failed -> {
                        RestoreFailureCard(
                            reason = state.reason,
                            onRetry = onRetry,
                            onDismiss = onDismiss,
                        )
                    }
                    is RestoreState.Idle -> {
                        Spacer(modifier = Modifier.height(Spacing.lg))
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun RestoreConfirmContent(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    buttonsEnabled: Boolean = true,
) {
    Column {
        Text(
            text = stringResource(Res.string.import_backup),
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        Text(
            text = stringResource(Res.string.restore_confirm_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.restore_merge_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDismiss,
                enabled = buttonsEnabled,
            ) {
                Text(stringResource(Res.string.cancel))
            }
            Button(
                onClick = onConfirm,
                enabled = buttonsEnabled,
                modifier = Modifier.padding(start = Spacing.sm),
            ) {
                Text(stringResource(Res.string.restore_choose_file))
            }
        }
    }
}
