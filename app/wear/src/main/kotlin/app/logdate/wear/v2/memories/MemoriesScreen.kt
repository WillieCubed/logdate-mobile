package app.logdate.wear.v2.memories

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.R

object MemoriesTags {
    const val LIST = "memories_list"
}

/** Revisit a past day or this week's Rewind without scrolling through recent moments. */
@Composable
fun MemoriesScreen(
    cards: MemoryCards,
    onOpenMoment: ((JournalNote) -> Unit)?,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.testTag(MemoriesTags.LIST)) {
            item {
                ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(stringResource(R.string.wear_memory_title))
                }
            }
            if ((cards.onThisDay == null && cards.rewind == null) || onOpenMoment == null) {
                item {
                    Text(
                        text = stringResource(R.string.wear_memory_empty),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                memoryCardItems(cards, spec, onOpenMoment)
            }
        }
    }
}
