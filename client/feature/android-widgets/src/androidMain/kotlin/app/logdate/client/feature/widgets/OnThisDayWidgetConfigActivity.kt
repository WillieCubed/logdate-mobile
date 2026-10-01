@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.logdate.client.domain.recommendation.GetMemoryRecallUseCase
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.recommendation.RecallMode
import app.logdate.client.domain.recommendation.WidgetContentType
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.ui.theme.LogDateTheme
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.koin.android.ext.android.inject

/**
 * Configuration activity launched when the user first places the Memories widget.
 *
 * Lets the user choose recall mode and content types for one widget instance.
 */
class OnThisDayWidgetConfigActivity : SecureWidgetConfigActivity() {
    private val settingsRepository: MemoriesSettingsRepository by inject()
    private val getMemoryRecall: GetMemoryRecallUseCase by inject()
    private val notesRepository: JournalNotesRepository by inject()
    private val transcriptionRepository: TranscriptionRepository by inject()
    private val userStateRepository: UserStateRepository by inject()

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
            if (!unlockSetup(userStateRepository)) return@launch
            val currentSettings = settingsRepository.getSettings()
            val instanceSettings = WidgetInstanceSettings(this@OnThisDayWidgetConfigActivity)

            setContent {
                LogDateTheme {
                    WidgetConfigScreen(
                        initialRecallMode = instanceSettings.recallMode(appWidgetId, currentSettings.recallMode),
                        initialContentTypes = instanceSettings.contentTypes(appWidgetId, currentSettings.widgetContentTypes),
                        previewFor = { mode, types ->
                            runCatching {
                                getMemoryRecall(
                                    aiEnabled = currentSettings.aiRecallEnabled,
                                    recallMode = mode,
                                    contentTypes = types,
                                    rotationDay =
                                        kotlin.time.Clock.System
                                            .now()
                                            .toLocalDateTime(
                                                kotlinx.datetime.TimeZone.currentSystemDefault(),
                                            ).date,
                                ).firstOrNull()?.toWidgetStateWithAudio(notesRepository, transcriptionRepository, types)
                                    ?: resolveEmptyWidgetState(notesRepository)
                            }.getOrNull() ?: OnThisDayWidgetState.NoMemoryToday
                        },
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
            WidgetInstanceSettings(this@OnThisDayWidgetConfigActivity).saveRecall(appWidgetId, recallMode, contentTypes)

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
    previewFor: suspend (RecallMode, Set<WidgetContentType>) -> OnThisDayWidgetState,
    onDone: (RecallMode, Set<WidgetContentType>) -> Unit,
) {
    var recallMode by remember { mutableStateOf(initialRecallMode) }
    var contentTypes by remember { mutableStateOf(initialContentTypes) }
    var previewState by remember { mutableStateOf<OnThisDayWidgetState>(OnThisDayWidgetState.Loading) }
    LaunchedEffect(recallMode, contentTypes) { previewState = previewFor(recallMode, contentTypes) }
    val context = LocalContext.current
    val isExample = previewState !is OnThisDayWidgetState.HasMemory
    val displayPreview = if (isExample) sampleRotatingMemory(context, contentTypes) else previewState

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Scaffold(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxHeight().fillMaxWidth(),
            topBar = { TopAppBar(title = { Text("Memories") }) },
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    WidgetSetupPreview(OnThisDayWidget(displayPreview), displayPreview)
                }
                Text("Find memories", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((mode, label, icon) in listOf(
                        Triple(RecallMode.ON_THIS_DAY, "On this day", Icons.Rounded.AutoAwesome),
                        Triple(RecallMode.REDISCOVER, "Archive", Icons.Rounded.History),
                    )) {
                        WidgetSetupChoice(
                            selected = recallMode == mode,
                            onClick = { recallMode = mode },
                            modifier = Modifier.weight(1f).height(72.dp),
                        ) {
                            Row(
                                Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                                Text(label, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Show", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((type, label, icon) in listOf(
                        Triple(WidgetContentType.TEXT, "Text", Icons.Rounded.Notes),
                        Triple(WidgetContentType.PHOTOS, "Photos", Icons.Rounded.PhotoLibrary),
                        Triple(WidgetContentType.AUDIO, "Audio", Icons.Rounded.Mic),
                    )) {
                        WidgetSetupChoice(
                            selected = type in contentTypes,
                            onClick = { contentTypes = contentTypes.toggle(type) },
                            modifier = Modifier.weight(1f).height(84.dp),
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                            Text(label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onDone(recallMode, contentTypes) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.widget_config_done))
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

internal fun sampleRotatingMemory(
    context: android.content.Context,
    contentTypes: Set<WidgetContentType> = setOf(WidgetContentType.PHOTOS),
): OnThisDayWidgetState.HasMemory {
    val previousYear =
        kotlin.time.Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .minus(1, DateTimeUnit.YEAR)
    return OnThisDayWidgetState.HasMemory(
        dateIso = previousYear.toString(),
        dateFormatted = previousYear.formatForDisplay(),
        summary = if (WidgetContentType.PHOTOS in contentTypes) "Milo after the rain" else "We stayed out until the rain stopped",
        thumbnailUri =
            if (WidgetContentType.PHOTOS in contentTypes) {
                "android.resource://${context.packageName}/drawable/widget_photo_sample"
            } else {
                null
            },
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
