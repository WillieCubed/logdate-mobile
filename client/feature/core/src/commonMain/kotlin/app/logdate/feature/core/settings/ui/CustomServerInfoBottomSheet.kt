@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.ui.platform.PlatformSheet
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.custom_server_info_changes_body
import logdate.client.feature.core.generated.resources.custom_server_info_changes_title
import logdate.client.feature.core.generated.resources.custom_server_info_intro_body
import logdate.client.feature.core.generated.resources.custom_server_info_intro_title
import logdate.client.feature.core.generated.resources.custom_server_info_missing_body
import logdate.client.feature.core.generated.resources.custom_server_info_missing_title
import logdate.client.feature.core.generated.resources.custom_server_info_title
import logdate.client.feature.core.generated.resources.custom_server_info_url_body
import logdate.client.feature.core.generated.resources.custom_server_info_url_title
import logdate.client.feature.core.generated.resources.custom_server_info_what_stays_local_body
import logdate.client.feature.core.generated.resources.custom_server_info_what_stays_local_title
import logdate.client.feature.core.generated.resources.keep_using_logdate_cloud
import logdate.client.feature.core.generated.resources.use_custom_server
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomServerInfoBottomSheet(
    onDismiss: () -> Unit,
    onUseCustomServer: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    PlatformSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.lg)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(Res.string.custom_server_info_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            ServerInfoSection(
                icon = Icons.Default.Info,
                title = stringResource(Res.string.custom_server_info_intro_title),
                body = stringResource(Res.string.custom_server_info_intro_body),
            )
            ServerInfoSection(
                icon = Icons.Default.Dns,
                title = stringResource(Res.string.custom_server_info_changes_title),
                body = stringResource(Res.string.custom_server_info_changes_body),
            )
            ServerInfoSection(
                icon = Icons.Default.CloudQueue,
                title = stringResource(Res.string.custom_server_info_missing_title),
                body = stringResource(Res.string.custom_server_info_missing_body),
            )
            ServerInfoSection(
                icon = Icons.Default.Storage,
                title = stringResource(Res.string.custom_server_info_what_stays_local_title),
                body = stringResource(Res.string.custom_server_info_what_stays_local_body),
            )
            ServerInfoSection(
                icon = Icons.Default.Link,
                title = stringResource(Res.string.custom_server_info_url_title),
                body = stringResource(Res.string.custom_server_info_url_body),
            )

            Button(
                onClick = onUseCustomServer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.use_custom_server))
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.keep_using_logdate_cloud))
            }
        }
    }
}

@Composable
private fun ServerInfoSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
