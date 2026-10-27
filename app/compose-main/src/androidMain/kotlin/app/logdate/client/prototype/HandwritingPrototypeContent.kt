@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client.prototype

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.handwriting.HandwritingEntryPrototype
import logdate.app.composemain.generated.resources.Res
import logdate.app.composemain.generated.resources.ink_practice_earlier
import logdate.app.composemain.generated.resources.ink_practice_load_error
import logdate.app.composemain.generated.resources.ink_practice_save_error
import logdate.app.composemain.generated.resources.ink_practice_saved
import logdate.app.composemain.generated.resources.ink_practice_saving
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HandwritingPrototypeContent(
    state: HandwritingPrototypeViewModel,
    onExit: () -> Unit,
) {
    val page = state.page
    if (page == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            if (state.loadFailed) Text(stringResource(Res.string.ink_practice_load_error)) else CircularProgressIndicator()
        }
    } else {
        val statusMessage =
            when (state.status) {
                PracticePageStatus.SAVING -> Res.string.ink_practice_saving
                PracticePageStatus.SAVED -> Res.string.ink_practice_saved
                PracticePageStatus.SAVED_EARLIER -> Res.string.ink_practice_earlier
                PracticePageStatus.SAVE_ERROR -> Res.string.ink_practice_save_error
                else -> null
            }
        HandwritingEntryPrototype(
            initialPage = page,
            initialText = state.text,
            onTextChanged = state::editText,
            onExit = onExit,
            onPageChanged = state::edit,
            onSavePracticePage = state::save,
            storageStatus = statusMessage?.let { stringResource(it) },
        )
    }
}
