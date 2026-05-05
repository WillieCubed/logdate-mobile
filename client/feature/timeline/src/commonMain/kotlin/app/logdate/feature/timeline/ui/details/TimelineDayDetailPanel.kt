@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.timeline.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.ui.common.plus
import app.logdate.ui.common.scrollToTop
import app.logdate.ui.profiles.PersonUiState
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.util.toReadableDateShort
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logdate.client.feature.timeline.generated.resources.*
import logdate.client.feature.timeline.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineDayDetailPanel(
    uiState: TimelineDayUiState,
    onExit: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit = {},
    onAttachNoteToEvent: (noteId: String, eventId: String) -> Unit = { _, _ -> },
    visitedLocations: List<DayLocation> = listOf(),
    onOpenLocations: (() -> Unit)? = null,
    onOpenRewind: () -> Unit = {},
    onDecorate: (() -> Unit)? = null,
    onJournalClick: (Uuid) -> Unit = {},
    scrollState: LazyListState = rememberLazyListState(),
    /**
     * Optional UUID of an entry within this day to scroll to on first composition. Used by the
     * search-result tap fallback (transcription, ambient sound, sticker, place) so the user lands
     * near the entry instead of at the top of the day. Falls through to scroll-to-top when the
     * id doesn't match any visible section.
     */
    scrollToEntryId: String? = null,
    modifier: Modifier = Modifier,
) {
    val summary = uiState.summary
    val timestamp = uiState.date
    val people = uiState.people
    val resolvedVisitedLocations =
        visitedLocations.ifEmpty {
            uiState.placesVisited.mapNotNull { place ->
                val latitude = place.latitude ?: return@mapNotNull null
                val longitude = place.longitude ?: return@mapNotNull null
                DayLocation(
                    locationId = place.id,
                    name = place.title,
                    latitude = latitude,
                    longitude = longitude,
                )
            }
        }

    LaunchedEffect(uiState, scrollToEntryId) {
        val targetIndex =
            scrollToEntryId?.let { id ->
                targetSectionIndex(id, uiState, resolvedVisitedLocations)
            }
        if (targetIndex != null) {
            scrollState.animateScrollToItem(targetIndex)
        } else {
            scrollState.scrollToTop()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(timestamp.toReadableDateShort()) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(Res.string.close))
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors().copy(
                        containerColor = Color.Transparent,
                    ),
                actions = {
                    if (onDecorate != null) {
                        IconButton(onClick = onDecorate) {
                            Icon(Icons.Default.Brush, contentDescription = "Decorate")
                        }
                    }
                    IconButton(onClick = {
                        onOpenRewind()
                    }) {
                        Icon(Icons.Default.History, contentDescription = stringResource(Res.string.rewind))
                    }
                },
            )
        },
        containerColor = Color.Transparent,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = scrollState,
            contentPadding = contentPadding + PaddingValues(vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item(
                contentType = "tldr",
            ) {
                TldrSection(summary)
            }
            item(
                contentType = "people",
            ) {
                PeopleEncounteredSection(
                    people = people,
                )
            }
            item(
                contentType = "notes",
            ) {
                // We already logged this info earlier, don't need redundant logging

                NotesListSection(
                    notes = uiState.notes,
                    onJournalClick = onJournalClick,
                )
            }
            if (uiState.events.isNotEmpty()) {
                item(contentType = "events") {
                    EventsSection(
                        events = uiState.events,
                        onOpenEvent = onOpenEvent,
                        onAttachNoteToEvent = onAttachNoteToEvent,
                    )
                }
            }
            if (resolvedVisitedLocations.isNotEmpty()) {
                item(
                    contentType = "locations",
                ) {
                    LocationsSection(
                        locations = resolvedVisitedLocations,
                        onOpenLocations = onOpenLocations,
                    )
                }
            }
        }
    }
}

/**
 * Maps an entry UUID string to the LazyColumn item index of the section that displays it.
 *
 * The panel's LazyColumn order is: tldr (0), people (1), notes (2), events (3 — conditional),
 * locations (3 or 4 depending on whether events are present). The function only resolves
 * sections whose data the panel exposes; if the entryId belongs to a content type rendered
 * elsewhere (or not at all) it returns null and the caller falls back to scroll-to-top.
 */
internal fun targetSectionIndex(
    entryId: String,
    uiState: TimelineDayUiState,
    visitedLocations: List<DayLocation>,
): Int? {
    if (uiState.notes.any { it.noteId.toString() == entryId }) return 2
    if (visitedLocations.any { it.locationId.toString() == entryId }) {
        return if (uiState.events.isNotEmpty()) 4 else 3
    }
    return null
}

@Preview
@Composable
private fun TimelineDayDetailPanelPreview() {
    val uiState =
        TimelineDayUiState(
            summary = "I ate some cake downtown, went to a concert, and had a lot of fun with friends.",
            date =
                Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date,
            people =
                listOf(
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
    val uiState =
        TimelineDayUiState(
            summary = "I ate some cake downtown, went to a concert, and had a lot of fun with friends.",
            date =
                Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date,
        )
    TimelineDayDetailPanel(
        uiState = uiState,
        onExit = {},
        onOpenEvent = {},
    )
}
