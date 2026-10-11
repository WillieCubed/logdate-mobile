package app.logdate.wear.v2.memories

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.R
import app.logdate.wear.v2.moments.momentLabel

object MemoryCardTags {
    const val ON_THIS_DAY = "memory_on_this_day"
    const val REWIND = "memory_rewind"
}

/** On this day and this week's Rewind, each as a card that opens its moment. No cards without [onOpenMoment]. */
fun TransformingLazyColumnScope.memoryCardItems(
    cards: MemoryCards,
    spec: TransformationSpec,
    onOpenMoment: ((JournalNote) -> Unit)?,
) {
    val open = onOpenMoment ?: return
    cards.onThisDay?.let { day ->
        item {
            OnThisDayCard(day, open, Modifier.transformedHeight(this, spec), SurfaceTransformation(spec))
        }
    }
    cards.rewind?.let { rewind ->
        item {
            RewindCard(rewind, open, Modifier.transformedHeight(this, spec), SurfaceTransformation(spec))
        }
    }
}

@Composable
private fun OnThisDayCard(
    day: OnThisDay,
    open: (JournalNote) -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val body = if (day.summary.isBlank()) momentLabel(day.leadMoment) else day.summary
    TitleCard(
        onClick = { open(day.leadMoment) },
        title = { Text(stringResource(R.string.wear_memory_on_this_day)) },
        modifier = modifier.fillMaxWidth().testTag(MemoryCardTags.ON_THIS_DAY),
        subtitle = { Text(pluralStringResource(R.plurals.wear_memory_years_ago, day.yearsAgo, day.yearsAgo)) },
        transformation = transformation,
    ) {
        Text(text = body, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RewindCard(
    rewind: WeeklyRewind,
    open: (JournalNote) -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    TitleCard(
        onClick = { open(rewind.leadMoment) },
        title = { Text(stringResource(R.string.wear_memory_rewind)) },
        modifier = modifier.fillMaxWidth().testTag(MemoryCardTags.REWIND),
        transformation = transformation,
    ) {
        Text(text = rewind.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
