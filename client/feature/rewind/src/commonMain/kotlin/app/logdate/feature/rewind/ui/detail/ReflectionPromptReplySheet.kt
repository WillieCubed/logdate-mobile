@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import logdate.client.feature.rewind.generated.resources.Res
import logdate.client.feature.rewind.generated.resources.reflection_prompt_reply_cancel
import logdate.client.feature.rewind.generated.resources.reflection_prompt_reply_clear
import logdate.client.feature.rewind.generated.resources.reflection_prompt_reply_field_label
import logdate.client.feature.rewind.generated.resources.reflection_prompt_reply_field_placeholder
import logdate.client.feature.rewind.generated.resources.reflection_prompt_reply_save
import logdate.client.feature.rewind.generated.resources.reflection_prompt_reply_sheet_title
import org.jetbrains.compose.resources.stringResource

/**
 * Modal bottom sheet for typing a reply to a noticing prompt.
 *
 * Pre-fills with the user's previous reply when one exists, so editing is the same gesture
 * as creating. The Save button submits whatever's in the field; an empty field maps to
 * "clear my reply" further down the use case chain so this composable doesn't have to
 * distinguish create / edit / delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReflectionPromptReplySheet(
    state: ReflectionReplySheetState.Open,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(state) { mutableStateOf(state.existing.orEmpty()) }
    val hasExisting = state.existing != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(PaddingValues(horizontal = 24.dp, vertical = 16.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.reflection_prompt_reply_sheet_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = state.prompt.observation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
            )
            Text(
                text = state.prompt.invitation,
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(stringResource(Res.string.reflection_prompt_reply_field_label)) },
                placeholder = { Text(stringResource(Res.string.reflection_prompt_reply_field_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
            ) {
                if (hasExisting) {
                    TextButton(onClick = { onSave("") }) {
                        Text(stringResource(Res.string.reflection_prompt_reply_clear))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.reflection_prompt_reply_cancel))
                }
                TextButton(onClick = { onSave(draft) }) {
                    Text(stringResource(Res.string.reflection_prompt_reply_save))
                }
            }
        }
    }
}
