package app.logdate.wear.presentation.settings

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhonelinkOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import app.logdate.wear.R
import app.logdate.wear.notification.WearPromptScheduler
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WearSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: WearSettingsViewModel = koinViewModel(),
) {
    val settingsState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("wear_settings", Context.MODE_PRIVATE)
    }

    var morningPromptEnabled by remember {
        mutableStateOf(prefs.getBoolean("morning_prompt", true))
    }
    var eveningPromptEnabled by remember {
        mutableStateOf(prefs.getBoolean("evening_prompt", true))
    }

    // Stop/start polling based on lifecycle
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startPolling()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopPolling()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    WearSettingsContent(
        settingsState = settingsState,
        morningPromptEnabled = morningPromptEnabled,
        onMorningPromptChanged = { enabled ->
            morningPromptEnabled = enabled
            prefs.edit().putBoolean("morning_prompt", enabled).apply()
            updatePromptSchedule(context, enabled || eveningPromptEnabled)
        },
        eveningPromptEnabled = eveningPromptEnabled,
        onEveningPromptChanged = { enabled ->
            eveningPromptEnabled = enabled
            prefs.edit().putBoolean("evening_prompt", enabled).apply()
            updatePromptSchedule(context, morningPromptEnabled || enabled)
        },
        onSyncNow = viewModel::syncNow,
    )
}

private fun updatePromptSchedule(context: Context, anyEnabled: Boolean) {
    val scheduler = WearPromptScheduler(context)
    if (anyEnabled) {
        scheduler.scheduleAll()
    } else {
        scheduler.cancelAll()
    }
}

@Composable
internal fun WearSettingsContent(
    settingsState: WearSettingsUiState = WearSettingsUiState(),
    morningPromptEnabled: Boolean = true,
    onMorningPromptChanged: (Boolean) -> Unit = {},
    eveningPromptEnabled: Boolean = true,
    onEveningPromptChanged: (Boolean) -> Unit = {},
    onSyncNow: () -> Unit = {},
) {
    val listState = rememberScalingLazyListState()

    ScreenScaffold(
        timeText = { TimeText() },
        scrollState = listState,
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "header") {
                Text(
                    text = stringResource(R.string.wear_settings_title),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
            }

            // Phone & Sync section
            item(key = "phone_sync_header") {
                Text(
                    text = stringResource(R.string.wear_settings_phone_sync),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                )
            }

            item(key = "connection_status") {
                val icon = if (settingsState.isPhoneConnected) {
                    Icons.Default.PhoneAndroid
                } else {
                    Icons.Default.PhonelinkOff
                }
                val label = if (settingsState.isPhoneConnected) {
                    val name = settingsState.phoneName
                    if (name != null) {
                        stringResource(R.string.wear_settings_connected_to, name)
                    } else {
                        stringResource(R.string.wear_settings_connected_to, "phone")
                    }
                } else {
                    stringResource(R.string.wear_settings_phone_not_connected)
                }
                val color = if (settingsState.isPhoneConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    label = {
                        Text(
                            text = label,
                            color = color,
                        )
                    },
                )
            }

            item(key = "last_sync") {
                val context = LocalContext.current
                val lastSyncText = if (settingsState.lastSyncTime != null) {
                    val relativeTime = DateUtils.getRelativeTimeSpanString(
                        settingsState.lastSyncTime.toEpochMilliseconds(),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
                    stringResource(R.string.wear_settings_last_synced, relativeTime)
                } else {
                    stringResource(R.string.wear_settings_never_synced)
                }
                Text(
                    text = lastSyncText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }

            if (settingsState.pendingCount > 0) {
                item(key = "pending_count") {
                    Text(
                        text = stringResource(
                            R.string.wear_settings_pending_entries,
                            settingsState.pendingCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }
            }

            if (settingsState.hasErrors) {
                item(key = "sync_errors") {
                    Text(
                        text = stringResource(R.string.wear_settings_sync_error, 1),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }
            }

            item(key = "sync_now") {
                val label = if (settingsState.isSyncingNow) {
                    stringResource(R.string.wear_settings_syncing)
                } else {
                    stringResource(R.string.wear_settings_sync_now)
                }
                Button(
                    onClick = onSyncNow,
                    enabled = !settingsState.isSyncingNow,
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                        )
                    },
                    label = { Text(label) },
                )
            }

            // Notifications section
            item(key = "notifications_header") {
                Text(
                    text = stringResource(R.string.wear_settings_notifications),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                )
            }

            item(key = "morning_prompt") {
                SwitchButton(
                    checked = morningPromptEnabled,
                    onCheckedChange = onMorningPromptChanged,
                    label = {
                        Text(stringResource(R.string.wear_settings_morning_prompt))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "evening_prompt") {
                SwitchButton(
                    checked = eveningPromptEnabled,
                    onCheckedChange = onEveningPromptChanged,
                    label = {
                        Text(stringResource(R.string.wear_settings_evening_prompt))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

        }
    }
}
