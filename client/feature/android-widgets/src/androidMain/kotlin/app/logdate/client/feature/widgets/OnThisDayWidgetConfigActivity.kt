@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.recommendation.RecallMode
import app.logdate.client.domain.recommendation.WidgetContentType
import app.logdate.ui.theme.LogDateTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Configuration activity launched when the user first places the Memories widget.
 *
 * Lets the user choose their recall mode and content type preferences before
 * the widget is added to the home screen. Required by the `android:configure`
 * attribute in the widget metadata.
 */
class OnThisDayWidgetConfigActivity : ComponentActivity() {
    private val settingsRepository: MemoriesSettingsRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setResult(RESULT_CANCELED)

        val appWidgetId =
            intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        lifecycleScope.launch {
            val currentSettings = settingsRepository.getSettings()

            setContent {
                LogDateTheme {
                    WidgetConfigScreen(
                        initialRecallMode = currentSettings.recallMode,
                        initialContentTypes = currentSettings.widgetContentTypes,
                        onDone = { recallMode, contentTypes ->
                            applyAndFinish(appWidgetId, recallMode, contentTypes)
                        },
                    )
                }
            }
        }
    }

    private fun applyAndFinish(
        appWidgetId: Int,
        recallMode: RecallMode,
        contentTypes: Set<WidgetContentType>,
    ) {
        lifecycleScope.launch {
            settingsRepository.setRecallMode(recallMode)
            settingsRepository.setWidgetContentTypes(contentTypes)

            WorkManager.getInstance(this@OnThisDayWidgetConfigActivity).enqueue(
                OneTimeWorkRequestBuilder<OnThisDayWidgetRefreshWorker>().build(),
            )

            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, result)
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(
    initialRecallMode: RecallMode,
    initialContentTypes: Set<WidgetContentType>,
    onDone: (RecallMode, Set<WidgetContentType>) -> Unit,
) {
    var recallMode by remember { mutableStateOf(initialRecallMode) }
    var contentTypes by remember { mutableStateOf(initialContentTypes) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_config_title)) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            Text(
                text = stringResource(R.string.widget_config_recall_mode),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.widget_config_recall_on_this_day)) },
                supportingContent = { Text(stringResource(R.string.widget_config_recall_on_this_day_desc)) },
                leadingContent = {
                    RadioButton(
                        selected = recallMode == RecallMode.ON_THIS_DAY,
                        onClick = null,
                    )
                },
                modifier = Modifier.clickable { recallMode = RecallMode.ON_THIS_DAY },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.widget_config_recall_rediscover)) },
                supportingContent = { Text(stringResource(R.string.widget_config_recall_rediscover_desc)) },
                leadingContent = {
                    RadioButton(
                        selected = recallMode == RecallMode.REDISCOVER,
                        onClick = null,
                    )
                },
                modifier = Modifier.clickable { recallMode = RecallMode.REDISCOVER },
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.widget_config_content_types),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            ContentTypeRow(
                title = stringResource(R.string.widget_config_content_text),
                checked = WidgetContentType.TEXT in contentTypes,
                onToggle = { contentTypes = contentTypes.toggle(WidgetContentType.TEXT) },
            )
            ContentTypeRow(
                title = stringResource(R.string.widget_config_content_photos),
                checked = WidgetContentType.PHOTOS in contentTypes,
                onToggle = { contentTypes = contentTypes.toggle(WidgetContentType.PHOTOS) },
            )
            ContentTypeRow(
                title = stringResource(R.string.widget_config_content_audio),
                checked = WidgetContentType.AUDIO in contentTypes,
                onToggle = { contentTypes = contentTypes.toggle(WidgetContentType.AUDIO) },
            )

            Spacer(modifier = Modifier.weight(1f))

            FilledTonalButton(
                onClick = { onDone(recallMode, contentTypes) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Text(stringResource(R.string.widget_config_done))
            }
        }
    }
}

@Composable
private fun ContentTypeRow(
    title: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
            )
        },
        modifier = modifier.clickable(onClick = onToggle),
    )
}

/**
 * Toggles a content type in the set, ensuring at least one type remains selected.
 */
private fun Set<WidgetContentType>.toggle(type: WidgetContentType): Set<WidgetContentType> =
    if (type in this) {
        val without = this - type
        without.ifEmpty { this }
    } else {
        this + type
    }
