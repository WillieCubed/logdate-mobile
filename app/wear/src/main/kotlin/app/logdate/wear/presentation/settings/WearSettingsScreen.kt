package app.logdate.wear.presentation.settings

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import app.logdate.wear.R
import app.logdate.wear.notification.WearPromptScheduler

@Composable
fun WearSettingsScreen(
    onNavigateBack: () -> Unit,
) {
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

    WearSettingsContent(
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
    morningPromptEnabled: Boolean = true,
    onMorningPromptChanged: (Boolean) -> Unit = {},
    eveningPromptEnabled: Boolean = true,
    onEveningPromptChanged: (Boolean) -> Unit = {},
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

            item(key = "notifications_header") {
                Text(
                    text = stringResource(R.string.wear_settings_notifications),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
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

            item(key = "about_header") {
                Text(
                    text = stringResource(R.string.wear_settings_about),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                )
            }

            item(key = "version") {
                Text(
                    text = stringResource(R.string.wear_settings_version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
