package app.logdate.wear.v2.capture

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import app.logdate.wear.R

/** The key the written text comes back under. */
internal const val WRITTEN_TEXT_KEY = "written_text"

/** The system's text input, where the watch offers voice and the keyboard, with [label] as its prompt. */
fun remoteTextInputIntent(label: String): Intent {
    val input =
        RemoteInput
            .Builder(WRITTEN_TEXT_KEY)
            .setLabel(label)
            .wearableExtender { setInputActionType(EditorInfo.IME_ACTION_DONE) }
            .build()
    return RemoteInputIntentHelper.putRemoteInputsExtra(RemoteInputIntentHelper.createActionRemoteInputIntent(), listOf(input))
}

/** The text the system input returned, or null when nothing was written. */
fun writtenText(result: Intent?): String? {
    if (result == null) return null
    val text = RemoteInput.getResultsFromIntent(result)?.getCharSequence(WRITTEN_TEXT_KEY)?.toString()
    return text?.takeIf { it.isNotBlank() }
}

/** Opens the system text input; [onText] gets what was written, and a cancelled input does nothing. */
@Composable
fun rememberRemoteTextInput(onText: (String) -> Unit): () -> Unit {
    val label = stringResource(R.string.wear_capture_write_prompt)
    val currentOnText by rememberUpdatedState(onText)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            writtenText(result.data)?.let { text -> currentOnText(text) }
        }
    return remember(launcher, label) { { launcher.launch(remoteTextInputIntent(label)) } }
}
