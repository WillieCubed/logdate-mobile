package app.logdate.feature.location.timeline.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * A bottom sheet that displays the user's location timeline.
 * 
 * @param isVisible Whether the bottom sheet is visible
 * @param onDismiss Callback invoked when the user dismisses the bottom sheet
 * @param viewModel The view model for the location timeline
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationTimelineBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocationTimelineViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    
    // Effect to handle the dismiss action from the parent
    LaunchedEffect(isVisible) {
        if (!isVisible && sheetState.isVisible) {
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
        }
    }
    
    // Only show the bottom sheet when it's visible
    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            modifier = modifier
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Simple title header instead of a full TopAppBar
                LocationHeaderWithClose(
                    onDismiss = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    }
                )
                
                // Content - always show location history, handle current location permissions in view
                LocationTimelineView(
                    uiState = uiState,
                    onDeleteLocation = viewModel::deleteLocationEntry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp) // Extra padding at the bottom
                )
            }
        }
    }
}

/**
 * A more compact header for the location timeline bottom sheet
 */
@Composable
private fun LocationHeaderWithClose(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Text(
            text = "Location Timeline",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close"
            )
        }
    }
}