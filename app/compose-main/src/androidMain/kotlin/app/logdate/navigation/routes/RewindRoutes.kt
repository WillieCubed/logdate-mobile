package app.logdate.navigation.routes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import app.logdate.feature.rewind.ui.ImageRewindPanelUiState
import app.logdate.feature.rewind.ui.RewindDetailUiState
import app.logdate.feature.rewind.ui.RewindDetailViewModel
import app.logdate.feature.rewind.ui.RewindOverviewScreen
import app.logdate.feature.rewind.ui.RewindPanelUiState
import app.logdate.navigation.scenes.HomeScene
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.routes.core.RewindDetailRoute
import app.logdate.navigation.routes.core.RewindList
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

fun MainAppNavigator.navigateToRewind(id: Uuid) {
    backStack.add(RewindDetailRoute(id))
}

/**
 * Provides the navigation routes for rewind-related screens.
 */
fun EntryProviderBuilder<NavKey>.rewindRoutes(
    onBack: () -> Unit,
    onNavigateToRewindDetail: (Uuid) -> Unit,
) {
    // Main Rewind overview screen - one of the primary tabs
    entry<RewindList>(
        metadata = HomeScene.homeScene() // Mark this as a home scene entry
    ) { _ ->
        RewindOverviewScreen(
            onOpenRewind = onNavigateToRewindDetail
        )
    }
    
    // Rewind detail screen
    entry<RewindDetailRoute>() { route ->
        PublicRewindDetailScreen(
            rewindId = route.id,
            onExitRewind = onBack
        )
    }
}

/**
 * Public adapter for the RewindDetailScreen.
 * 
 * Uses the actual immersive RewindDetailScreen component to provide a full-screen,
 * Instagram stories-like experience for viewing rewinds.
 */
@Composable
fun PublicRewindDetailScreen(
    rewindId: Uuid,
    onExitRewind: () -> Unit,
    viewModel: RewindDetailViewModel = koinViewModel()
) {
    // Use the actual RewindDetailScreen component
    app.logdate.feature.rewind.ui.detail.RewindDetailScreen(
        rewindId = rewindId,
        onExitRewind = onExitRewind,
        viewModel = viewModel,
        modifier = Modifier.fillMaxSize()
    )
}