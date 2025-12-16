package app.logdate.feature.core.settings.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Format a LocalDate according to the current locale.
 * This is implemented differently on each platform.
 */
expect fun formatDateLocalized(date: LocalDate): String

/**
 * A component for selecting a birthday date.
 *
 * @param birthday The current birthday value
 * @param onBirthdaySelected Callback when a new birthday is selected
 * @param modifier Modifier for the component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdaySelector(
    birthday: Instant,
    onBirthdaySelected: (Instant) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    
    // Format the birthday for display
    val formattedBirthday = if (birthday == Instant.DISTANT_PAST) {
        "Not set"
    } else {
        // Use kotlinx.datetime.format for properly localized date
        val localDate = birthday.toLocalDateTime(TimeZone.currentSystemDefault()).date
        formatDateLocalized(localDate)
    }
    
    // The list item that shows the current birthday and opens the date picker
    ListItem(
        headlineContent = { Text("Birthday") },
        supportingContent = { Text(formattedBirthday) },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null
            )
        },
        modifier = modifier.clickable { showDatePicker = true }
    )
    
    if (showDatePicker) {
        // Initialize date picker with current birthday or current date if not set
        val initialMillis = if (birthday == Instant.DISTANT_PAST) {
            Clock.System.now().toEpochMilliseconds()
        } else {
            birthday.toEpochMilliseconds()
        }
        
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onBirthdaySelected(Instant.fromEpochMilliseconds(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
