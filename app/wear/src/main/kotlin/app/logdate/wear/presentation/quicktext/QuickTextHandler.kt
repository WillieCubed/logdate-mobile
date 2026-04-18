package app.logdate.wear.presentation.quicktext

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.R
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Launches system speech-to-text, saves the transcribed text as a JournalNote.Text,
 * then navigates back. No custom UI -- the system STT activity handles everything.
 */
@Composable
fun QuickTextLauncher(
    notesRepository: JournalNotesRepository,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val spokenText =
                    result.data
                        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        ?.firstOrNull()
                if (!spokenText.isNullOrBlank()) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val now = Clock.System.now()
                            val note =
                                JournalNote.Text(
                                    content = spokenText,
                                    uid = Uuid.random(),
                                    creationTimestamp = now,
                                    lastUpdated = now,
                                )
                            notesRepository.create(note)
                            Napier.d("Quick text note saved: ${spokenText.take(30)}...")
                        } catch (e: Exception) {
                            Napier.e("Failed to save quick text note", e)
                        }
                    }
                }
            }
            onDone()
        }

    val prompt = stringResource(R.string.wear_quick_text_prompt)
    LaunchedEffect(Unit) {
        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            }
        launcher.launch(intent)
    }
}
