package app.logdate.feature.library.ui.location

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * A screen that displays a log of the user's location history.
 */
@Composable
fun LocationLogScreen(
    viewModel: LocationLogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
}

@Composable
fun LocationLogContentPane(uiState: LocationLogUiState) {
    LocationLogList(uiState.records)
}

@Composable
internal fun LocationLogList(
    records: List<LocationLogEntryUiState>,
) {
    LazyColumn {
        items(records) { record ->
            LocationLogEntryItem(record)
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocationLogEntryItem(record: LocationLogEntryUiState) {
    BoxWithConstraints {
        if (maxWidth > 600.dp) {
            LocationLogEntryWide(record)
        } else {
            LocationLogEntryNarrow(record)
        }
    }
}

@Composable
private fun LocationLogEntryWide(record: LocationLogEntryUiState) {
    Row(
        modifier = Modifier.heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column { // This is a column
            Text(
                record.placeName ?: "Unknown place",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            // Now the latitude and longitude in proper format
            Text(
                formatCoordinates(record.latitude, record.longitude),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // Start time
        Text(
            record.start.toString(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(72.dp),
        )
        // End time
        Text(
            record.end?.toString() ?: "right now",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(72.dp),
        )
    }
}

@Composable
private fun LocationLogEntryNarrow(record: LocationLogEntryUiState) {
    Column {
        Text(
            record.placeName ?: "Unknown place",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        // Now the latitude and longitude in proper format
        Text(
            formatCoordinates(record.latitude, record.longitude),
            style = MaterialTheme.typography.bodyMedium,
        )
        // Start to end time
        Text(
            "${record.start} - ${record.end ?: "right now"}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// TODO: Move this to a utility module
fun formatCoordinates(latitude: Double, longitude: Double): String {
    return "Lat: %.6f, Long: %.6f".format(latitude, longitude)
}