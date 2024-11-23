@file:OptIn(ExperimentalUuidApi::class)

package app.logdate.feature.rewind.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import app.logdate.feature.rewind.ui.overview.RewindOverviewViewModel
import kotlin.uuid.ExperimentalUuidApi

@Composable
fun RewindOverviewScreen(
    onOpenRewind: RewindOpenCallback,
    viewModel: RewindOverviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    RewindScreenContent(
        uiState,
        onOpenRewind = onOpenRewind,
    )
}