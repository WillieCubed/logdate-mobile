@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.watch

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.logdate.ui.common.SimpleSettingsItem
import app.logdate.ui.watch.WatchSetupProvider
import app.logdate.ui.watch.rememberWatchSetupLauncher
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.watch_system_setup
import logdate.client.feature.core.generated.resources.watch_system_setup_choose
import logdate.client.feature.core.generated.resources.watch_system_setup_close
import logdate.client.feature.core.generated.resources.watch_system_setup_failed
import logdate.client.feature.core.generated.resources.watch_system_setup_galaxy
import logdate.client.feature.core.generated.resources.watch_system_setup_other
import logdate.client.feature.core.generated.resources.watch_system_setup_other_help
import logdate.client.feature.core.generated.resources.watch_system_setup_pixel
import org.jetbrains.compose.resources.stringResource

/** Manufacturer setup precedes LogDate's separate companion association permission. */
@Composable
internal fun WatchSystemSetupButton(
    label: String? = null,
    launcher: ((WatchSetupProvider) -> Boolean)? = rememberWatchSetupLauncher(),
) {
    var choosing by remember { mutableStateOf(false) }
    var manufacturerHelp by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Button(onClick = { choosing = true }, enabled = launcher != null) { Text(label ?: stringResource(Res.string.watch_system_setup)) }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(Res.string.watch_system_setup_choose)) },
            text = {
                Column {
                    WatchSetupProvider.entries.forEach { provider ->
                        SimpleSettingsItem(
                            title =
                                stringResource(
                                    if (provider ==
                                        WatchSetupProvider.PIXEL
                                    ) {
                                        Res.string.watch_system_setup_pixel
                                    } else {
                                        Res.string.watch_system_setup_galaxy
                                    },
                                ),
                            description = "",
                            onClick = {
                                failed = launcher?.invoke(provider) != true
                                if (!failed) choosing = false
                            },
                            action = {},
                        )
                    }
                    SimpleSettingsItem(
                        title = stringResource(Res.string.watch_system_setup_other),
                        description = "",
                        onClick = {
                            choosing = false
                            manufacturerHelp = true
                        },
                        action = {},
                    )
                    if (failed) Text(stringResource(Res.string.watch_system_setup_failed), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(Res.string.watch_system_setup_close)) } },
        )
    }
    if (manufacturerHelp) {
        AlertDialog(
            onDismissRequest = { manufacturerHelp = false },
            title = { Text(stringResource(Res.string.watch_system_setup_other)) },
            text = { Text(stringResource(Res.string.watch_system_setup_other_help)) },
            confirmButton = {
                TextButton(
                    onClick = { manufacturerHelp = false },
                ) { Text(stringResource(Res.string.watch_system_setup_close)) }
            },
        )
    }
}
