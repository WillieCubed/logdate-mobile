@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import app.logdate.shared.model.Place
import app.logdate.ui.theme.Spacing
import kotlin.uuid.Uuid

/**
 * Adaptive picker for choosing the place attached to an event.
 *
 * Renders as a [ModalBottomSheet] on compact widths and an [AlertDialog] on expanded widths,
 * mirroring the same adaptive switch used by `AddToJournalPicker`. The list shows every saved
 * [Place.UserDefined] plus a "No place" row at the top so the user can clear the selection.
 *
 * @param availablePlaces the user's saved places, typically from `ObserveUserPlacesUseCase`.
 * @param selectedPlaceId the currently linked place id, or `null` when no place is linked.
 * @param onPlaceSelected invoked when the user picks a place (or "No place"). The picker
 *   dismisses itself afterwards.
 * @param onDismiss invoked when the user dismisses the picker without choosing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventPlacePicker(
    availablePlaces: List<Place.UserDefined>,
    selectedPlaceId: Uuid?,
    onPlaceSelected: (Uuid?) -> Unit,
    onDismiss: () -> Unit,
) {
    val isExpanded =
        currentWindowAdaptiveInfo()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)

    if (isExpanded) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Choose a place") },
            text = {
                PlacePickerContent(
                    availablePlaces = availablePlaces,
                    selectedPlaceId = selectedPlaceId,
                    onPlaceSelected = { picked ->
                        onPlaceSelected(picked)
                        onDismiss()
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Done") }
            },
        )
    } else {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
                Text(
                    text = "Choose a place",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                )
                PlacePickerContent(
                    availablePlaces = availablePlaces,
                    selectedPlaceId = selectedPlaceId,
                    onPlaceSelected = { picked ->
                        onPlaceSelected(picked)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun PlacePickerContent(
    availablePlaces: List<Place.UserDefined>,
    selectedPlaceId: Uuid?,
    onPlaceSelected: (Uuid?) -> Unit,
) {
    if (availablePlaces.isEmpty()) {
        Text(
            text = "You don't have any saved places yet. Save one from your timeline first.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.lg),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item("none") {
            ListItem(
                headlineContent = { Text("No place") },
                leadingContent = {
                    RadioButton(
                        selected = selectedPlaceId == null,
                        onClick = { onPlaceSelected(null) },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(availablePlaces, key = { it.id }) { place ->
            ListItem(
                headlineContent = { Text(place.displayName) },
                supportingContent =
                    place.description?.let { description ->
                        { Text(description) }
                    },
                leadingContent = {
                    RadioButton(
                        selected = selectedPlaceId == place.id,
                        onClick = { onPlaceSelected(place.id) },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
