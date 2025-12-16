@file:OptIn(ExperimentalUuidApi::class)

package app.logdate.feature.rewind.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.logdate.feature.rewind.ui.overview.RewindOverviewViewModel
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.ExperimentalUuidApi

/**
 * The main entry point for the Rewind feature's overview screen.
 * 
 * This composable serves as the connection between the navigation layer and the underlying
 * UI components, handling ViewModel integration and state management. It provides a clean
 * interface for displaying the user's weekly rewinds in the floating card design.
 * 
 * ## Integration Points:
 * - **Navigation**: Receives callback for rewind detail navigation
 * - **State Management**: Connects to RewindOverviewViewModel for data flow
 * - **UI Rendering**: Delegates to RewindScreenContent for actual presentation
 * 
 * ## Architecture Role:
 * This screen acts as a "thin controller" that:
 * 1. Injects the appropriate ViewModel using Koin
 * 2. Observes UI state changes from the ViewModel
 * 3. Passes the callback through to child components
 * 4. Applies standard screen-level modifiers
 * 
 * ## Usage:
 * ```kotlin
 * RewindOverviewScreen(
 *     onOpenRewind = { rewindId ->
 *         navController.navigate("rewind/$rewindId")
 *     }
 * )
 * ```
 * 
 * @param onOpenRewind Callback invoked when user selects a rewind card to view details
 * @param viewModel The ViewModel managing rewind data (injected by Koin by default)
 */
@Composable
fun RewindOverviewScreen(
    onOpenRewind: RewindOpenCallback,
    viewModel: RewindOverviewViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    RewindScreenContent(
        uiState,
        onOpenRewind = onOpenRewind,
        modifier = modifier.fillMaxSize(),
    )
}