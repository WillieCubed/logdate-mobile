@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import app.logdate.ui.theme.Spacing
import app.logdate.util.localTime
import app.logdate.util.toReadableDateShort
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * "When" section of the event detail editor.
 *
 * Shows two rows of inline chips — one for the start, one for the end — plus a checkbox that
 * collapses the event to a point in time. Tapping any chip opens the corresponding Material 3
 * date or time picker. The end row hides itself when point-in-time is on.
 *
 * @param startTime current start instant of the draft.
 * @param endTime current end instant, or `null` when the event is point-in-time.
 * @param onStartTimeChange invoked when the user picks a new start date or time.
 * @param onEndTimeChange invoked when the user picks a new end date or time. Receives `null`
 *   when the user toggles to point-in-time.
 * @param onTogglePointInTime invoked when the user checks the point-in-time box.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventTimeRangeSection(
    startTime: Instant,
    endTime: Instant?,
    onStartTimeChange: (Instant) -> Unit,
    onEndTimeChange: (Instant?) -> Unit,
    onTogglePointInTime: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Spacing.lg),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = "When",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DateTimeChipRow(
                label = "Starts",
                instant = startTime,
                onPick = onStartTimeChange,
            )

            if (endTime != null) {
                DateTimeChipRow(
                    label = "Ends",
                    instant = endTime,
                    onPick = { picked -> onEndTimeChange(picked) },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Checkbox(
                    checked = endTime == null,
                    onCheckedChange = onTogglePointInTime,
                )
                Text(
                    text = "Point in time",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeChipRow(
    label: String,
    instant: Instant,
    onPick: (Instant) -> Unit,
) {
    val zone = TimeZone.currentSystemDefault()
    val localDateTime = remember(instant, zone) { instant.toLocalDateTime(zone) }
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    var timePickerOpen by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = { datePickerOpen = true },
                label = { Text(localDateTime.date.toReadableDateShort()) },
            )
            AssistChip(
                onClick = { timePickerOpen = true },
                label = { Text(instant.localTime) },
            )
        }
    }

    if (datePickerOpen) {
        val initialMillis =
            localDateTime.date.atStartOfDayIn(zone).toEpochMilliseconds()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerOpen = false
                        val selectedMillis = datePickerState.selectedDateMillis ?: return@TextButton
                        val newDate = Instant.fromEpochMilliseconds(selectedMillis).toLocalDateTime(zone).date
                        onPick(LocalDateTime(newDate, localDateTime.time).toInstant(zone))
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) { Text("Cancel") }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (timePickerOpen) {
        val timePickerState =
            rememberTimePickerState(
                initialHour = localDateTime.hour,
                initialMinute = localDateTime.minute,
                is24Hour = false,
            )
        // M3 1.11 doesn't ship a TimePickerDialog. We reuse DatePickerDialog purely as a
        // Material-styled dialog scaffold for the standalone TimePicker — it's the pattern
        // recommended in the M3 docs until a dedicated dialog ships.
        DatePickerDialog(
            onDismissRequest = { timePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        timePickerOpen = false
                        val newTime = LocalTime(timePickerState.hour, timePickerState.minute)
                        onPick(LocalDateTime(localDateTime.date, newTime).toInstant(zone))
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { timePickerOpen = false }) { Text("Cancel") }
            },
        ) {
            TimePicker(state = timePickerState, modifier = Modifier.padding(Spacing.lg))
        }
    }
}
