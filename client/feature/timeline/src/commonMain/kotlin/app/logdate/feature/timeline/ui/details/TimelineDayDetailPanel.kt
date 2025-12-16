package app.logdate.feature.timeline.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import app.logdate.ui.common.plus
import app.logdate.ui.common.scrollToTop
import app.logdate.ui.profiles.PersonUiState
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.util.toReadableDateShort
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.uuid.Uuid


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineDayDetailPanel(
    uiState: TimelineDayUiState,
    onExit: () -> Unit,
    events: List<DayEvent> = listOf(),
    onOpenEvent: (eventId: String) -> Unit = {},
    visitedLocations: List<DayLocation> = listOf(),
    onOpenRewind: () -> Unit = {},
    scrollState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val (summary, timestamp, people) = uiState
    
    LaunchedEffect(uiState) {
        scrollState.scrollToTop()
    }

    Scaffold(
        modifier = modifier,
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
            state = scrollState,
            contentPadding = contentPadding + PaddingValues(vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item(
                contentType = "tldr"
            ) {
                TldrSection(summary)
            }
            item(
                contentType = "people"
            ) {
                PeopleEncounteredSection(
                    people = people,
                )
            }
            item(
                contentType = "notes"
            ){
                // We already logged this info earlier, don't need redundant logging
                
                NotesListSection(
                    notes = uiState.notes,
                )
            }
//            item {
//                EventsSection(
//                    events = events,
//                    onOpenEvent = onOpenEvent,
//                )
//            }
            item(
                contentType = "locations"
            ){
                LocationsSection(locations = visitedLocations, DayLocation.Origin)
            }
        }
    }
}


@Preview
@Composable
private fun TimelineDayDetailPanelPreview() {
    val uiState = TimelineDayUiState(
        summary = "I ate some cake downtown, went to a concert, and had a lot of fun with friends.",
        date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        people = listOf(
            PersonUiState(
                uid = Uuid.random(),
                name = "Margaret Belford",
            ),
            PersonUiState(
                uid = Uuid.random(),
                name = "Charles Averill",
            ),
            PersonUiState(
                uid = Uuid.random(),
                name = "Lane Hughes",
            ),
            PersonUiState(
                uid = Uuid.random(),
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
