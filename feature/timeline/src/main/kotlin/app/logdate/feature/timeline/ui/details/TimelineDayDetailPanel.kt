package app.logdate.feature.timeline.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import app.logdate.feature.timeline.ui.ImageNoteUiState
import app.logdate.feature.timeline.ui.TextNoteUiState
import app.logdate.feature.timeline.ui.TimelineDayUiState
import app.logdate.ui.profiles.PersonUiState
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateShort
import coil3.compose.AsyncImage
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineDayDetailPanel(
    uiState: TimelineDayUiState,
    onExit: () -> Unit,
    events: List<DayEvent> = listOf(),
    onOpenEvent: (eventId: String) -> Unit,
    visitedLocations: List<DayLocation> = listOf(),
    onOpenRewind: () -> Unit = {},
) {
    val (summary, timestamp, people) = uiState
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(timestamp.toReadableDateShort()) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                actions = {
                    IconButton(onClick = {
                        onOpenRewind()
                    }) {
                        Icon(Icons.Default.History, contentDescription = "Rewind")
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding + PaddingValues(vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item {
                TldrSection(summary)
            }
            item {
                PeopleEncounteredSection(
                    people = people,
                )
            }
            item {
                Text("Notes")
                uiState.notes.forEach { note ->
                    when (note) {
                        is TextNoteUiState -> TextNoteSnippet(note)
                        is ImageNoteUiState -> ImageNoteSnippet(note)
                    }
                }
            }
//            item {
//                EventsSection(
//                    events = events,
//                    onOpenEvent = onOpenEvent,
//                )
//            }
            item {
                LocationsSection(locations = visitedLocations, DayLocation.Origin)
            }
        }
    }
}

@Composable
private fun TextNoteSnippet(uiState: TextNoteUiState) {
    Box(modifier = Modifier.padding(vertical = Spacing.md)) {
        Text(uiState.text)
    }
}

@Composable
private fun ImageNoteSnippet(uiState: ImageNoteUiState) {
    AsyncImage(
        model = uiState.uri,
        contentDescription = null,
    )
}

operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = PaddingValues(
    start = this.calculateStartPadding(LayoutDirection.Ltr) +
            other.calculateStartPadding(LayoutDirection.Ltr),
    top = this.calculateTopPadding() + other.calculateTopPadding(),
    end = this.calculateEndPadding(LayoutDirection.Ltr) +
            other.calculateEndPadding(LayoutDirection.Ltr),
    bottom = this.calculateBottomPadding() + other.calculateBottomPadding(),
)

@Preview
@Composable
private fun TimelineDayDetailPanelPreview() {
    val uiState = TimelineDayUiState(
        summary = "I ate some cake downtown, went to a concert, and had a lot of fun with friends.",
        date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        people = listOf(
            PersonUiState(
                personId = "1",
                name = "Margaret Belford",
            ),
            PersonUiState(
                personId = "2",
                name = "Charles Averill",
            ),
            PersonUiState(
                personId = "3",
                name = "Lane Hughes",
            ),
            PersonUiState(
                personId = "4",
                name = "Haley Wheatley",
            ),
        ),
    )
    TimelineDayDetailPanel(
        uiState = uiState,
        onExit = {},
        onOpenEvent = {},
    )
}

@Preview
@Composable
private fun TimelineDayDetailPanelPreview_NoPeople() {
    val uiState = TimelineDayUiState(
        summary = "I ate some cake downtown, went to a concert, and had a lot of fun with friends.",
        date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    )
    TimelineDayDetailPanel(
        uiState = uiState,
        onExit = {},
        onOpenEvent = {},
    )
}
