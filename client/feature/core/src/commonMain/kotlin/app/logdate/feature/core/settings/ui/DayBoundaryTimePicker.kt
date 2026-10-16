@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import app.logdate.util.asTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import logdate.client.ui.generated.resources.common_cancel
import logdate.client.ui.generated.resources.common_confirm
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import logdate.client.ui.generated.resources.Res as UiRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerDialog(
    initialHour: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = 0)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour) }) { Text(stringResource(UiRes.string.common_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiRes.string.common_cancel)) } },
        text = { TimePicker(state = state) },
    )
}

internal fun formatHour(hour: Int): String {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.todayIn(tz)
    return LocalDateTime(today, LocalTime(hour, 0)).toInstant(tz).asTime
}
