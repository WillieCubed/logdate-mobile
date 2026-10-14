@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.watch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.feature.core.settings.ui.watch.v2.WatchCompanionPage
import app.logdate.feature.core.settings.ui.watch.v2.WatchCompanionPreferencesViewModel
import app.logdate.feature.core.settings.ui.watch.v2.WatchCompanionSections
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.watch_help
import logdate.client.feature.core.generated.resources.watch_notifications
import logdate.client.feature.core.generated.resources.watch_notifications_description
import logdate.client.feature.core.generated.resources.watch_notifications_title
import logdate.client.feature.core.generated.resources.watch_privacy_description
import logdate.client.feature.core.generated.resources.watch_privacy_title
import logdate.client.feature.core.generated.resources.watch_settings
import logdate.client.feature.core.generated.resources.watch_sync
import logdate.client.feature.core.generated.resources.watch_sync_now
import logdate.client.feature.core.generated.resources.watch_sync_pending
import logdate.client.feature.core.generated.resources.watch_sync_up_to_date
import logdate.client.feature.core.generated.resources.watch_troubleshooting
import logdate.client.feature.core.generated.resources.watch_troubleshooting_description
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WatchSettingsScreen(
    onBack: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToTroubleshooting: () -> Unit,
    viewModel: WatchSettingsViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
    onNavigateToReminders: () -> Unit = {},
    onNavigateToTiles: () -> Unit = {},
    onNavigateToMicrophone: () -> Unit = {},
    onNavigateToRemove: () -> Unit = {},
    onNavigateToConnectionCheck: () -> Unit = onNavigateToTroubleshooting,
) {
    val flags = koinInject<FeatureFlagStore>()
    val redesign by remember(flags) { flags.observe(FeatureFlag.WATCH_EXPERIENCE_V2) }.collectAsState(false)
    val status by viewModel.status.collectAsState()
    val requestFailure by viewModel.requestFailure.collectAsState()
    val installRequestSent by viewModel.installRequestSent.collectAsState()

    if (redesign) {
        WatchCompanionPage(
            status,
            requestFailure,
            onBack,
            viewModel::beginAssociation,
            viewModel::installAppOnWatch,
            onNavigateToReminders,
            onNavigateToTiles,
            onNavigateToMicrophone,
            onNavigateToNotifications,
            onNavigateToConnectionCheck,
            onNavigateToRemove,
            modifier,
            installRequestSent,
        ) { captureActions -> WatchCompanionSettings(captureActions = captureActions) }
        return
    }
    WatchSettingsContent(
        status = status,
        requestFailure = requestFailure,
        onBack = onBack,
        onBeginAssociation = viewModel::beginAssociation,
        onRequestSync = viewModel::requestSync,
        onInstallOnWatch = viewModel::installAppOnWatch,
        onNavigateToNotifications = onNavigateToNotifications,
        onNavigateToTroubleshooting = onNavigateToTroubleshooting,
        modifier = modifier,
        companionSettings =
            if (redesign) {
                { WatchCompanionSettings() }
            } else {
                null
            },
    )
}

@Composable
private fun WatchCompanionSettings(
    captureActions: @Composable () -> Unit = {},
    viewModel: WatchCompanionPreferencesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val saveFailed by viewModel.saveFailed.collectAsState()
    WatchCompanionSections(
        state = state,
        saving = saving,
        saveFailed = saveFailed,
        onDays = { viewModel.set(SharedPreferenceKey.WindowDays, it) },
        onThisDay = { viewModel.set(SharedPreferenceKey.WindowOnThisDay, it) },
        onRewind = { viewModel.set(SharedPreferenceKey.WindowRewind, it) },
        onPlaces = { viewModel.set(SharedPreferenceKey.CapturePlaces, it) },
        onMood = { viewModel.set(SharedPreferenceKey.MoodAfterRecording, it) },
        onAudioDownloads = { viewModel.set(SharedPreferenceKey.AudioDownloadPolicy, it.stored) },
        captureActions = captureActions,
    )
}

@Composable
fun WatchSettingsContent(
    status: WatchStatus?,
    requestFailure: WatchRequestFailure?,
    onBack: () -> Unit,
    onBeginAssociation: () -> Unit,
    onRequestSync: () -> Unit,
    onInstallOnWatch: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToTroubleshooting: () -> Unit,
    modifier: Modifier = Modifier,
    companionSettings: (@Composable () -> Unit)? = null,
) {
    FoldableBookLayout(
        modifier = modifier.fillMaxSize(),
        minPaneWidth = 320.dp,
        startPane = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                WatchStatusCard(
                    status = status,
                    requestFailure = requestFailure,
                    onBeginAssociation = onBeginAssociation,
                    onInstallOnWatch = onInstallOnWatch,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
                WatchPrivacyNote()
            }
        },
        endPane = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                if (status.hasLogDate()) companionSettings?.invoke()
                WatchConnectedSections(
                    status = status,
                    onRequestSync = onRequestSync,
                    onNavigateToNotifications = onNavigateToNotifications,
                )
                WatchTroubleshootingSection(onNavigateToTroubleshooting)
            }
        },
        standardContent = {
            SettingsScaffold(
                title = stringResource(Res.string.watch_settings),
                onBack = onBack,
                modifier = modifier,
            ) {
                item {
                    WatchStatusCard(
                        status = status,
                        requestFailure = requestFailure,
                        onBeginAssociation = onBeginAssociation,
                        onInstallOnWatch = onInstallOnWatch,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
                if (status.hasLogDate()) {
                    companionSettings?.let { settings -> item { settings() } }
                    item {
                        WatchConnectedSections(
                            status = status,
                            onRequestSync = onRequestSync,
                            onNavigateToNotifications = onNavigateToNotifications,
                        )
                    }
                }
                item {
                    WatchTroubleshootingSection(onNavigateToTroubleshooting)
                }
                item {
                    WatchPrivacyNote()
                }
            }
        },
    )
}

/** Sync and notification settings exist only for a watch that has LogDate. */
private fun WatchStatus?.hasLogDate(): Boolean = this is WatchStatus.Ready || this is WatchStatus.NeedsAttention

@Composable
private fun WatchConnectedSections(
    status: WatchStatus?,
    onRequestSync: () -> Unit,
    onNavigateToNotifications: () -> Unit,
) {
    val nearby =
        when (status) {
            is WatchStatus.Ready -> status.nearby
            is WatchStatus.NeedsAttention -> status.nearby
            else -> return
        }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        SettingsSection(
            title = stringResource(Res.string.watch_sync),
            modifier = Modifier.padding(horizontal = Spacing.lg),
        ) {
            (status as? WatchStatus.Ready)?.sync?.let { SyncStatusRow(it) }

            OutlinedButton(
                onClick = onRequestSync,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                enabled = nearby,
            ) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(Res.string.watch_sync_now))
            }
        }

        SettingsSection(
            title = stringResource(Res.string.watch_notifications),
            modifier = Modifier.padding(horizontal = Spacing.lg),
        ) {
            SettingsNavItem(
                title = stringResource(Res.string.watch_notifications_title),
                description = stringResource(Res.string.watch_notifications_description),
                onClick = onNavigateToNotifications,
            )
        }
    }
}

/** Shown only when the watch reported how many moments wait; the phone does not know otherwise. */
@Composable
private fun SyncStatusRow(sync: WatchSyncLine) {
    val text =
        if (sync.waiting > 0) {
            stringResource(Res.string.watch_sync_pending, sync.waiting)
        } else {
            stringResource(Res.string.watch_sync_up_to_date)
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Spacing.sm),
    )
}

@Composable
private fun WatchTroubleshootingSection(onNavigateToTroubleshooting: () -> Unit) {
    SettingsSection(
        title = stringResource(Res.string.watch_help),
        modifier = Modifier.padding(horizontal = Spacing.lg),
    ) {
        SettingsNavItem(
            title = stringResource(Res.string.watch_troubleshooting),
            description = stringResource(Res.string.watch_troubleshooting_description),
            onClick = onNavigateToTroubleshooting,
        )
    }
}

@Composable
private fun SettingsNavItem(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun WatchPrivacyNote() {
    Surface {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    Icons.Default.Watch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.watch_privacy_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                text = stringResource(Res.string.watch_privacy_description),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
