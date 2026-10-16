@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.watch.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.logdate.client.domain.watch.WatchAudioDownloadPolicy
import app.logdate.ui.common.SettingsChoiceItem
import app.logdate.ui.common.SimpleSettingsItem
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.watch_audio_charging_only
import logdate.client.feature.core.generated.resources.watch_audio_on_demand
import logdate.client.feature.core.generated.resources.watch_audio_policy_description
import logdate.client.feature.core.generated.resources.watch_audio_wifi_only
import logdate.client.feature.core.generated.resources.watch_audio_wifi_or_charging
import logdate.client.feature.core.generated.resources.watch_library_days_cancel
import logdate.client.feature.core.generated.resources.watch_v2_audio
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WatchAudioDownloadPolicyItem(
    policy: WatchAudioDownloadPolicy,
    enabled: Boolean,
    onSelect: (WatchAudioDownloadPolicy) -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    SimpleSettingsItem(
        title = stringResource(Res.string.watch_v2_audio),
        description = policyLabel(policy),
        onClick = { choosing = true },
        enabled = enabled,
        action = { Icon(Icons.Default.ExpandMore, null) },
    )
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(Res.string.watch_v2_audio)) },
            text = {
                Column {
                    Text(stringResource(Res.string.watch_audio_policy_description))
                    WatchAudioDownloadPolicy.entries.forEach { choice ->
                        SettingsChoiceItem(
                            title = policyLabel(choice),
                            selected = choice == policy,
                            onClick = {
                                choosing = false
                                onSelect(choice)
                            },
                            enabled = enabled,
                        )
                    }
                }
            },
            confirmButton = {
            },
            dismissButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(Res.string.watch_library_days_cancel)) } },
        )
    }
}

@Composable
private fun policyLabel(policy: WatchAudioDownloadPolicy): String =
    stringResource(
        when (policy) {
            WatchAudioDownloadPolicy.WIFI_OR_CHARGING -> Res.string.watch_audio_wifi_or_charging
            WatchAudioDownloadPolicy.WIFI_ONLY -> Res.string.watch_audio_wifi_only
            WatchAudioDownloadPolicy.CHARGING_ONLY -> Res.string.watch_audio_charging_only
            WatchAudioDownloadPolicy.ON_DEMAND -> Res.string.watch_audio_on_demand
        },
    )
