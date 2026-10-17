@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.ui.common.SettingsChoiceItem
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.custom_server
import logdate.client.feature.core.generated.resources.custom_server_info_title
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
    SettingsSection(
        title = title,
        modifier = modifier.selectableGroup(),
    ) {
        SettingsChoiceItem(
            title = stringResource(Res.string.logdate_cloud),
            description = stringResource(Res.string.logdate_cloud_settings_description),
            selected = serverSelectionState.selectedPreset == ServerPreset.PRODUCTION,
            onClick = { onSelectPreset(ServerPreset.PRODUCTION) },
        )

        SettingsChoiceItem(
            title = stringResource(Res.string.custom_server),
            description = stringResource(Res.string.custom_server_settings_description),
            selected = serverSelectionState.selectedPreset == ServerPreset.CUSTOM,
            onClick = { onSelectPreset(ServerPreset.CUSTOM) },
            trailingContent = {
                IconButton(onClick = onShowCustomServerInfo) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(Res.string.custom_server_info_title),
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
