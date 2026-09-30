@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.client.domain.location.history.LocationHistorySnapshot
import app.logdate.shared.model.location.PlaceVisit
import app.logdate.shared.model.location.SemanticPlace
import app.logdate.util.toReadableDateShort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HumanPlaceDetail(
    placeId: String,
    snapshot: LocationHistorySnapshot,
    onVisit: (PlaceVisit) -> Unit,
    onMemory: (String) -> Unit,
    onMerge: (String, SemanticPlace) -> Unit,
    onDismiss: () -> Unit,
) {
    val row = snapshot.placeRows().firstOrNull { placeId in it.sourceIds }
    val visits =
        snapshot.items.filterIsInstance<PlaceVisit>().filter {
            (it.place?.id ?: "evidence:${it.evidenceIds.first()}") in row?.sourceIds.orEmpty()
        }
    var merging by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<SemanticPlace?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text(row?.title ?: "Your place")
            row?.supportingText?.let { Text(it) }
            Text("Visits")
            if (visits.isEmpty()) Text("There are memories here, but no recorded visits in this date range.")
            visits.forEach { visit ->
                TextButton(
                    onClick = { onVisit(visit) },
                ) { Text("${visit.start.toReadableDateShort()} · ${visit.toHistoryUi(emptyList()).timeLabel}") }
            }
            Text("Memories")
            row?.memories?.forEach { HistoryMemoryPreview(it, onMemory) }
            if (snapshot.places.any { it.id == placeId }) {
                TextButton(onClick = { merging = !merging }) { Text("Merge with another place") }
                if (merging) {
                    snapshot.collectionPlaces().filter { it.id != placeId }.forEach { place ->
                        TextButton(onClick = { target = place }) { Text(place.name) }
                    }
                }
            }
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    }
    target?.let { place ->
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text("Merge these places?") },
            text = { Text("Visits will stay separate and appear under ${place.name}. Your journal memories will be kept.") },
            confirmButton = {
                TextButton(onClick = {
                    onMerge(placeId, place)
                    target = null
                    onDismiss()
                }) { Text("Merge places") }
            },
            dismissButton = { TextButton(onClick = { target = null }) { Text("Cancel") } },
        )
    }
}
