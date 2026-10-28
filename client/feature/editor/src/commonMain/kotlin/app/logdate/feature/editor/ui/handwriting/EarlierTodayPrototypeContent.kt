@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.handwriting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.entry_page_earlier_today
import logdate.client.feature.editor.generated.resources.entry_page_sample_date
import org.jetbrains.compose.resources.stringResource

/** Read-only design fixtures; these never enter the draft's block list. */
@Composable
internal fun EarlierTodayPrototypeContent() {
    Column(Modifier.fillMaxWidth().testTag("earlier_today"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.entry_page_earlier_today), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(Res.string.entry_page_sample_date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                earlierTodayMoments.forEach { EarlierMoment(it.time, it.title, it.excerpt) }
            }
        }
    }
}

@Composable
private fun EarlierMoment(
    time: String,
    title: String,
    excerpt: String,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            time,
            Modifier.widthIn(min = 60.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(excerpt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun EarlierTodayCaptureContext() {
    Column(
        Modifier
            .widthIn(max = 680.dp)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .testTag("earlier_capture_context"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        earlierTodayMoments.forEach { moment ->
            EarlierMoment(moment.time, moment.title, moment.excerpt)
        }
    }
}

private data class EarlierMomentPreview(
    val time: String,
    val title: String,
    val excerpt: String,
)

private val earlierTodayMoments =
    listOf(
        EarlierMomentPreview("8:12 AM", "A slower start", "Coffee outside before the day got busy."),
        EarlierMomentPreview("12:46 PM", "A good conversation", "Lunch ran longer than planned. Glad it did."),
    )
