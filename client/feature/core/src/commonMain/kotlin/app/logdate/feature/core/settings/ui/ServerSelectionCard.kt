@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.custom_server
import logdate.client.feature.core.generated.resources.custom_server_settings_description
import logdate.client.feature.core.generated.resources.https_your_server_example_com
import logdate.client.feature.core.generated.resources.logdate_cloud
import logdate.client.feature.core.generated.resources.logdate_cloud_settings_description
import logdate.client.feature.core.generated.resources.server
import logdate.client.feature.core.generated.resources.server_url
import org.jetbrains.compose.resources.stringResource

@Composable
fun ServerSelectionCard(
    serverSelectionState: ServerSelectionState,
    onSelectPreset: (ServerPreset) -> Unit,
    onUpdateCustomUrl: (String) -> Unit,
    onShowCustomServerInfo: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.server),
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md)
                    .selectableGroup(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            ServerOptionRow(
                title = stringResource(Res.string.logdate_cloud),
                description = stringResource(Res.string.logdate_cloud_settings_description),
                selected = serverSelectionState.selectedPreset == ServerPreset.PRODUCTION,
                onClick = { onSelectPreset(ServerPreset.PRODUCTION) },
            )

            ServerOptionRow(
                title = stringResource(Res.string.custom_server),
                description = stringResource(Res.string.custom_server_settings_description),
                selected = serverSelectionState.selectedPreset == ServerPreset.CUSTOM,
                onClick = { onSelectPreset(ServerPreset.CUSTOM) },
                trailingContent = {
                    IconButton(onClick = onShowCustomServerInfo) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                        )
                    }
                },
            )

            AnimatedVisibility(visible = serverSelectionState.selectedPreset == ServerPreset.CUSTOM) {
                OutlinedTextField(
                    value = serverSelectionState.customServerUrl,
                    onValueChange = onUpdateCustomUrl,
                    label = { Text(stringResource(Res.string.server_url)) },
                    placeholder = { Text(stringResource(Res.string.https_your_server_example_com)) },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun ServerOptionRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton,
                ).padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailingContent?.invoke()
    }
}
