@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.server_configuration
import logdate.client.feature.core.generated.resources.server_connected
import logdate.client.feature.core.generated.resources.server_connected_version
import logdate.client.feature.core.generated.resources.server_switching_info
import logdate.client.feature.core.generated.resources.test_connection
import logdate.client.feature.core.generated.resources.test_connection_before_saving
import logdate.client.feature.core.generated.resources.testing_connection
import logdate.client.feature.core.generated.resources.you_are_using_a_non_production_server_your_data_will_not_sync_with_logdate_cloud
import logdate.client.ui.generated.resources.common_save
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

/**
 * Server configuration section used by Account & Sign-In settings.
 * Shows server preset selector, validation status, and info card.
 */
@Composable
internal fun ServerSelectionSection(
    serverSelectionState: ServerSelectionState,
    onSelectPreset: (ServerPreset) -> Unit,
    onUpdateCustomUrl: (String) -> Unit,
    onValidateAndSave: () -> Unit,
    onShowCustomServerInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(Res.string.server_configuration),
            style = MaterialTheme.typography.titleMedium,
        )

        // Non-production warning
        AnimatedVisibility(visible = serverSelectionState.selectedPreset != ServerPreset.PRODUCTION) {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = stringResource(Res.string.you_are_using_a_non_production_server_your_data_will_not_sync_with_logdate_cloud),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xs))

        ServerSelectionCard(
            serverSelectionState = serverSelectionState,
            onSelectPreset = onSelectPreset,
            onUpdateCustomUrl = onUpdateCustomUrl,
            onShowCustomServerInfo = onShowCustomServerInfo,
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Validation status and button
        ValidationStatusSection(
            validationState = serverSelectionState.validationState,
            onValidate = onValidateAndSave,
            isValidationNeeded = serverSelectionState.selectedPreset != ServerPreset.PRODUCTION,
        )

        // Info card
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = stringResource(Res.string.server_switching_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ValidationStatusSection(
    validationState: ServerValidationState,
    onValidate: () -> Unit,
    isValidationNeeded: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (validationState) {
                is ServerValidationState.Idle -> {
                    if (isValidationNeeded) {
                        Text(
                            text = stringResource(Res.string.test_connection_before_saving),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                is ServerValidationState.Validating -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = stringResource(Res.string.testing_connection),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is ServerValidationState.Success -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text =
                                if (validationState.serverVersion != null) {
                                    stringResource(Res.string.server_connected_version, validationState.serverVersion)
                                } else {
                                    stringResource(Res.string.server_connected)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                is ServerValidationState.Error -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = validationState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            Button(
                onClick = onValidate,
                enabled = validationState !is ServerValidationState.Validating,
            ) {
                Text(
                    text =
                        when (validationState) {
                            is ServerValidationState.Success -> stringResource(UiRes.string.common_save)
                            else -> stringResource(Res.string.test_connection)
                        },
                )
            }
        }
    }
}
