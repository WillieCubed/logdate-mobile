package app.logdate.feature.timeline.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import kotlinx.datetime.Instant

@Composable
internal fun EventsSection(
    events: List<DayEvent>,
    onOpenEvent: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
) {

    Column(
        modifier = modifier.padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text("Events", style = MaterialTheme.typography.titleSmall)
        // TODO: Find way to make this work
//        LazyColumn {
//            items(events) { event ->
//                EventItem(event = event, onOpenEvent = { onOpenEvent(event.eventId) })
//            }
//        }
    }
}

data class DayEvent(
    val eventId: String,
    val title: String,
    val start: Instant,
    val end: Instant,
)

@Composable
private fun EventItem(
    event: DayEvent,
    onOpenEvent: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(Spacing.lg)
            .fillMaxWidth()
            .height(120.dp)
            .clickable { onOpenEvent() },
    ) {
        Text(event.title, style = MaterialTheme.typography.bodyMedium)
    }
}