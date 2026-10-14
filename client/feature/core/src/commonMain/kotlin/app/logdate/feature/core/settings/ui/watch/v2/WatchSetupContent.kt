@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.watch.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.logdate.feature.core.settings.ui.watch.WatchRequestFailure
import app.logdate.feature.core.settings.ui.watch.WatchRequestFailureMessage
import app.logdate.feature.core.settings.ui.watch.WatchSetupStep
import app.logdate.feature.core.settings.ui.watch.WatchStatus
import app.logdate.feature.core.settings.ui.watch.WatchSystemSetupButton
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.watch_association_pending
import logdate.client.feature.core.generated.resources.watch_association_pending_description
import logdate.client.feature.core.generated.resources.watch_loading
import logdate.client.feature.core.generated.resources.watch_setup_associate_action
import logdate.client.feature.core.generated.resources.watch_setup_associate_description
import logdate.client.feature.core.generated.resources.watch_setup_associate_title
import logdate.client.feature.core.generated.resources.watch_setup_install_action
import logdate.client.feature.core.generated.resources.watch_setup_install_description
import logdate.client.feature.core.generated.resources.watch_setup_install_title
import logdate.client.feature.core.generated.resources.watch_setup_installed_help
import logdate.client.feature.core.generated.resources.watch_setup_pair_description
import logdate.client.feature.core.generated.resources.watch_setup_pair_title
import logdate.client.feature.core.generated.resources.watch_setup_sent
import logdate.client.feature.core.generated.resources.watch_unsupported
import org.jetbrains.compose.resources.stringResource

/** First use has one next action and no controls for a watch LogDate cannot yet reach. */
@Composable
internal fun WatchSetupContent(
    status: WatchStatus?,
    requestFailure: WatchRequestFailure?,
    installRequestSent: Boolean,
    onAssociate: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        when (status) {
            null ->
                SetupCard(Icons.Default.Watch, stringResource(Res.string.watch_loading), null) {
                    CircularProgressIndicator()
                }
            WatchStatus.Unsupported ->
                SetupCard(Icons.Default.Watch, stringResource(Res.string.watch_unsupported), null) {}
            WatchStatus.NoWatch -> {
                SetupCard(
                    Icons.Default.Watch,
                    stringResource(Res.string.watch_setup_pair_title),
                    stringResource(Res.string.watch_setup_pair_description),
                ) {
                    WatchSystemSetupButton()
                }
            }
            is WatchStatus.SettingUp ->
                when (val step = status.step) {
                    is WatchSetupStep.PairWithPhone ->
                        SetupCard(
                            Icons.Default.Watch,
                            stringResource(Res.string.watch_setup_associate_title, step.watchName),
                            stringResource(Res.string.watch_setup_associate_description),
                        ) {
                            Button(onClick = onAssociate) { Text(stringResource(Res.string.watch_setup_associate_action)) }
                        }
                    WatchSetupStep.ConfirmOnPhone ->
                        SetupCard(
                            Icons.Default.Watch,
                            stringResource(Res.string.watch_association_pending),
                            stringResource(Res.string.watch_association_pending_description),
                        ) {
                            CircularProgressIndicator()
                        }
                }
            is WatchStatus.NotInstalled -> {
                SetupCard(
                    Icons.Default.Download,
                    stringResource(Res.string.watch_setup_install_title, status.watchName),
                    stringResource(Res.string.watch_setup_install_description),
                ) {
                    Button(onClick = onInstall) { Text(stringResource(Res.string.watch_setup_install_action)) }
                    if (installRequestSent) {
                        Text(
                            stringResource(Res.string.watch_setup_sent),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    requestFailure?.let { WatchRequestFailureMessage(it) }
                    Text(
                        stringResource(Res.string.watch_setup_installed_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            is WatchStatus.Ready, is WatchStatus.NeedsAttention -> Unit
        }
    }
}

@Composable
private fun SetupCard(
    icon: ImageVector,
    title: String,
    description: String?,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.Start,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(Spacing.md),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            content()
        }
    }
}
