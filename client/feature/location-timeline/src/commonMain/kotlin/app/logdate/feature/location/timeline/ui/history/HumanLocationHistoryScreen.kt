@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.client.location.tracking.LocationCaptureStatus
import app.logdate.shared.model.location.HistoryField
import app.logdate.shared.model.location.PlaceVisit
import app.logdate.shared.model.location.TravelMode
import app.logdate.shared.model.location.VisitMemoryContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HumanLocationHistoryScreen(
    onOpenNote: (Uuid) -> Unit,
    onAddMemory: (VisitMemoryContext) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HumanLocationHistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val access = rememberHistoryRecordingAccess()
    val recordingEnabled by viewModel.recordingEnabled.collectAsStateWithLifecycle()
    val completeDayEnabled by viewModel.completeDayEnabled.collectAsStateWithLifecycle()
    val interrupted =
        completeDayEnabled &&
            access.ready &&
            (access.captureStatus == LocationCaptureStatus.Stopped || access.captureStatus == LocationCaptureStatus.Failed)
    var calendar by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Pair<String?, HistoryEditAction>?>(null) }
    var reviewingChanges by remember { mutableStateOf(false) }
    var openedPlace by remember { mutableStateOf<String?>(null) }
    var linkingVisit by remember { mutableStateOf<String?>(null) }
    var moreMenu by remember { mutableStateOf(false) }
    Column(modifier) {
        HumanLocationHistoryContent(
            when {
                state.recoveryActionLabel == "Try again" -> state
                recordingEnabled && !access.ready ->
                    state.copy(recoveryMessage = access.message, recoveryActionLabel = access.actionLabel)
                interrupted ->
                    state.copy(
                        recoveryMessage =
                            "Recording was interrupted. Your saved history is still here, but part of your day may be missing.",
                        recoveryActionLabel = "Resume recording",
                    )
                else -> state
            },
            HumanLocationHistoryActions(
                onSelectItem = { viewModel.select(it) },
                onOpenDetail = { viewModel.select(it, true) },
                onCloseDetail = viewModel::dismiss,
                onDayOffset = viewModel::changeDay,
                onCalendar = { calendar = true },
                onTab = viewModel::setTab,
                onPlacesQuery = viewModel::setQuery,
                onPlacesMapVisible = viewModel::setMapVisible,
                onPlacesFilter = viewModel::cycleRange,
                onOpenPlace = { openedPlace = it },
                onOpenMemory = { Uuid.parseOrNull(it)?.let(onOpenNote) },
                onEdit = { id, action ->
                    when {
                        action == HistoryEditAction.AddMemory && id != null -> viewModel.contextFor(id)?.let(onAddMemory)
                        action == HistoryEditAction.LinkMemory && id != null -> {
                            viewModel.dismiss()
                            linkingVisit = id
                        }
                        action == HistoryEditAction.Delete && id != null -> viewModel.delete(id)
                        else -> edit = id to action
                    }
                },
                onReplayPlaying = if (access.reducedMotion) null else viewModel::replay,
                onRecover = {
                    when {
                        state.recoveryActionLabel == "Try again" -> viewModel.retry()
                        recordingEnabled && !access.ready -> access.resolve()
                        interrupted -> access.resolve()
                        snapshot?.conflicts?.isNotEmpty() == true -> reviewingChanges = true
                        else -> recording = true
                    }
                },
            ),
            modifier = Modifier.weight(1f),
            toolbarActions = {
                IconButton(onClick = { moreMenu = true }) {
                    Icon(Icons.Default.MoreVert, "More location options", Modifier.size(20.dp))
                }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(text = { Text("Recording settings") }, onClick = {
                        moreMenu = false
                        recording = true
                    })
                    if (state.tab == HistoryTab.Places) {
                        DropdownMenuItem(text = { Text("Through ${state.dateLabel}") }, onClick = {
                            moreMenu = false
                            calendar = true
                        })
                    }
                    if (snapshot?.conflicts?.isNotEmpty() == true) {
                        DropdownMenuItem(text = { Text("Review changes") }, onClick = {
                            moreMenu = false
                            reviewingChanges = true
                        })
                    }
                    snapshot?.recordingDevices?.takeIf { it.size > 1 }?.forEachIndexed { index, device ->
                        DropdownMenuItem(text = { Text("Device ${index + 1}") }, onClick = {
                            moreMenu = false
                            viewModel.selectSource(device)
                        })
                    }
                }
            },
            mapContent = { mapModifier ->
                val mapItems =
                    if (state.tab == HistoryTab.Places) {
                        val visiblePlaceIds = state.filteredPlaces().flatMapTo(mutableSetOf()) { it.sourceIds }
                        snapshot
                            ?.collectionPlaces()
                            .orEmpty()
                            .filter { place -> place.id in visiblePlaceIds }
                            .map {
                                PlaceVisit(
                                    it.id,
                                    viewModel.date.atStartOfDayIn(TimeZone.currentSystemDefault()),
                                    viewModel.date.atStartOfDayIn(TimeZone.currentSystemDefault()),
                                    listOf(it.id),
                                    it.latitude,
                                    it.longitude,
                                    false,
                                    it,
                                )
                            }
                    } else {
                        snapshot?.items.orEmpty()
                    }
                HumanHistoryMap(
                    mapItems,
                    state.selectedItemId,
                    {
                        if (state.tab == HistoryTab.Places) openedPlace = it else viewModel.select(it)
                    },
                    mapModifier,
                    viewModel::pauseReplay,
                )
            },
        )
    }
    if (calendar) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = viewModel.date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
        DatePickerDialog(onDismissRequest = { calendar = false }, confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let {
                    viewModel.setDate(
                        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date,
                        state.tab,
                    )
                }
                calendar =
                    false
            }) { Text("Show day") }
        }, dismissButton = { TextButton(onClick = { calendar = false }) { Text("Cancel") } }) { DatePicker(picker) }
    }
    linkingVisit?.let { visitId ->
        val assigned =
            snapshot
                ?.items
                .orEmpty()
                .filterIsInstance<PlaceVisit>()
                .flatMap { it.memoryIds }
                .toSet()
        val available = snapshot?.notes.orEmpty().filterNot { it.uid.toString() in assigned }
        AlertDialog(
            onDismissRequest = { linkingVisit = null },
            title = { Text("Link a memory from this day") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Choose a memory to keep with this visit. Its original date and location will stay the same.")
                    if (available.isEmpty()) Text("There are no unlinked memories for this day.")
                    available.forEach { memory ->
                        TextButton(onClick = {
                            viewModel.linkMemory(visitId, memory.uid.toString())
                            linkingVisit = null
                        }) { Text(memory.toHistoryMemory().title.take(160)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { linkingVisit = null }) { Text("Cancel") } },
        )
    }
    if (recording) {
        AlertDialog(
            onDismissRequest = { recording = false },
            title = { Text("Remember your day") },
            text = {
                Column {
                    Text(access.message)
                    access.openSettings?.let { open ->
                        TextButton(onClick = open) { Text("App permission settings") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (access.ready) {
                        viewModel.enableRecording()
                        access.resolve()
                        recording = false
                    } else {
                        access.resolve()
                    }
                }) { Text(access.actionLabel) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.pauseRecording()
                    recording = false
                }) { Text("Pause recording") }
            },
        )
    }
    edit?.let { (id, action) ->
        HistoryEditDialog(
            action,
            id?.let { key -> snapshot?.items?.firstOrNull { it.id == key } },
            snapshot?.collectionPlaces().orEmpty(),
            viewModel,
            onDismiss = { edit = null },
        )
    }
    openedPlace?.let { placeId ->
        snapshot?.let { data ->
            HumanPlaceDetail(
                placeId,
                data,
                onVisit = { visit ->
                    viewModel.setDate(visit.start.toLocalDateTime(TimeZone.currentSystemDefault()).date)
                    viewModel.select(visit.id, true)
                    openedPlace = null
                },
                onMemory = { Uuid.parseOrNull(it)?.let(onOpenNote) },
                onMerge = viewModel::mergePlace,
                onDismiss = { openedPlace = null },
            )
        }
    }
    if (reviewingChanges) {
        AlertDialog(
            onDismissRequest = { reviewingChanges = false },
            title = { Text("Choose the change to keep") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("These details were changed differently on your devices. Both versions have been kept until you choose.")
                    snapshot?.conflicts.orEmpty().groupBy { it.targetEvidenceId to it.field }.forEach { (key, edits) ->
                        Text(
                            when (key.second) {
                                HistoryField.PLACE -> "Place"
                                HistoryField.LABEL -> "Place name"
                                HistoryField.ACTIVITY -> "Activity"
                                HistoryField.START -> "Arrival"
                                HistoryField.END -> "Departure"
                                HistoryField.DELETE -> "Deleted visit"
                            },
                        )
                        edits.distinctBy { it.value }.forEach { edit ->
                            val label =
                                when (edit.field) {
                                    HistoryField.PLACE -> snapshot?.places?.firstOrNull { it.id == edit.value }?.name ?: "Saved place"
                                    HistoryField.ACTIVITY ->
                                        TravelMode.entries.firstOrNull { it.name == edit.value }?.humanName()
                                            ?: "Travelling"
                                    else -> edit.value
                                }
                            TextButton(
                                onClick = { viewModel.resolveConflict(edit.targetEvidenceId, edit.field, edit.value) },
                            ) { Text(label) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { reviewingChanges = false }) { Text("Done") } },
        )
    }
}
