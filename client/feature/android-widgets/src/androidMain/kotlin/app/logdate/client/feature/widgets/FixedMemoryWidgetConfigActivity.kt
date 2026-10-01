@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.ui.theme.LogDateTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.koin.android.ext.android.inject

class FixedMemoryWidgetConfigActivity : SecureWidgetConfigActivity() {
    private val notesRepository: JournalNotesRepository by inject()
    private val transcriptionRepository: TranscriptionRepository by inject()
    private val userStateRepository: UserStateRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        lifecycleScope.launch {
            if (!unlockSetup(userStateRepository)) return@launch
            val notes = notesRepository.allNotesObserved.first().sortedByDescending(JournalNote::creationTimestamp)
            val selectedId = WidgetInstanceSettings(this@FixedMemoryWidgetConfigActivity).chosenNoteId(widgetId)
            setContent {
                LogDateTheme {
                    FixedMemorySetup(notes, selectedId, transcriptionRepository) { note ->
                        WidgetInstanceSettings(this@FixedMemoryWidgetConfigActivity).saveChosenNote(widgetId, note.uid.toString())
                        enqueueWidgetRefresh(this@FixedMemoryWidgetConfigActivity)
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                        finish()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FixedMemorySetup(
    notes: List<JournalNote>,
    selectedId: String?,
    transcriptionRepository: TranscriptionRepository,
    onDone: (JournalNote) -> Unit,
) {
    var selected by remember { mutableStateOf(notes.firstOrNull { it.uid.toString() == selectedId }) }
    var selectedTranscript by remember(selected?.uid) { mutableStateOf<String?>(null) }
    LaunchedEffect(selected?.uid) {
        selectedTranscript =
            (selected as? JournalNote.Audio)?.let { note ->
                runCatching { transcriptionRepository.getTranscription(note.uid)?.displayText() }.getOrNull()
            }
    }
    var query by remember { mutableStateOf("") }
    val visibleNotes =
        remember(notes, query) {
            notes.filter { note ->
                query.isBlank() ||
                    note.toChosenEntryContent().let {
                        it.summary.contains(query, ignoreCase = true) || it.dateIso.contains(query)
                    }
            }
        }
    val context = LocalContext.current
    val displayPreview = selected?.toFixedWidgetState(selectedTranscript) ?: sampleFixedMemory(context)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Scaffold(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxHeight().fillMaxWidth(),
            topBar = { TopAppBar(title = { Text("Pinned memory") }) },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
                if (notes.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        WidgetSetupPreview(FixedMemoryWidget(displayPreview), displayPreview)
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text("Add an entry to pin it here.", modifier = Modifier.padding(16.dp))
                    }
                } else {
                    WidgetSetupPreview(FixedMemoryWidget(displayPreview), displayPreview)
                    if (notes.size > 8) {
                        Spacer(Modifier.height(20.dp))
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search entries") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            colors =
                                TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(top = 20.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (visibleNotes.isEmpty()) {
                            item { Text("No matching entries", modifier = Modifier.padding(16.dp)) }
                        }
                        items(visibleNotes, key = { it.uid.toString() }) { note ->
                            val content = note.toChosenEntryContent()
                            var rowTranscript by remember(note.uid) { mutableStateOf<String?>(null) }
                            LaunchedEffect(note.uid) {
                                rowTranscript =
                                    (note as? JournalNote.Audio)?.let { audio ->
                                        runCatching { transcriptionRepository.getTranscription(audio.uid)?.displayText() }.getOrNull()
                                    }
                            }
                            val thumbnail =
                                remember(content.imageUri) { content.imageUri?.let { loadScaledThumbnail(context, it)?.asImageBitmap() } }
                            WidgetSetupChoice(
                                selected = selected?.uid == note.uid,
                                onClick = { selected = note },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (thumbnail != null) {
                                        Image(
                                            bitmap = thumbnail,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)),
                                        )
                                    } else {
                                        val icon =
                                            when (content.kind) {
                                                WidgetEntryKind.AUDIO -> Icons.Rounded.Mic
                                                WidgetEntryKind.TEXT -> Icons.Rounded.Notes
                                                else -> Icons.Rounded.PhotoLibrary
                                            }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp),
                                        )
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(content.summary.ifBlank { rowTranscript ?: content.dateIso }, maxLines = 2)
                                        Text(
                                            LocalDate.parse(content.dateIso).formatForDisplay(),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    if (selected?.uid == note.uid) Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { selected?.let(onDone) }, enabled = selected != null, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Add widget")
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
