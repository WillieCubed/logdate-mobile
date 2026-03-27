@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.timeline.newstuff

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.audio.LocalTranscriptionState
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.buildTranscriptExcerpt
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.hide_transcript
import logdate.client.ui.generated.resources.preview_transcript
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

@Composable
internal fun TimelineTranscriptPreview(
    noteId: Uuid?,
    fallbackTranscript: String? = null,
    modifier: Modifier = Modifier,
) {
    val transcriptionState = LocalTranscriptionState.current
    val transcript =
        fallbackTranscript
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: noteId
                ?.let(transcriptionState.getTranscriptionText)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
            ?: return

    val excerpt = remember(transcript) { buildTranscriptExcerpt(transcript) } ?: return
    var isExpanded by remember(noteId, transcript) { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        if (isExpanded) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 168.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.md),
                ) {
                    Text(
                        text = transcript,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            Text(
                text = excerpt.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    TextButton(
        onClick = { isExpanded = !isExpanded },
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text =
                if (isExpanded) {
                    stringResource(Res.string.hide_transcript)
                } else {
                    stringResource(Res.string.preview_transcript)
                },
        )
    }
}
