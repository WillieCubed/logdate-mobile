package app.logdate.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import app.logdate.feature.rewind.ui.RewindDetailViewModel
import app.logdate.feature.rewind.ui.RewindOverviewScreen
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
    navigator: MainAppNavigator? = null
) {
    // Main Rewind overview screen - one of the primary tabs
    entry<RewindList>(
        metadata = HomeScene.homeScene() // Mark this as a home scene entry
    ) { _ ->
        RewindOverviewScreen(
            onOpenRewind = { rewindId -> 
                navigator?.navigateToRewind(rewindId) 
            }
        )
    }
    
    // Rewind detail screen
    entry<RewindDetailRoute> { route ->
        PublicRewindDetailScreen(
            rewindId = route.id,
            onExitRewind = onBack
        )
    }
}

/**
 * Public adapter for the internal RewindDetailScreen.
 * 
 * This allows us to use the internal RewindDetailScreen from the navigation layer.
 * Implements a basic rewind detail view using a simpler, direct approach that doesn't
 * rely on the internal implementation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicRewindDetailScreen(
    rewindId: Uuid,
    onExitRewind: () -> Unit,
    viewModel: RewindDetailViewModel = koinViewModel()
) {
    // Get the UI state from the ViewModel
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Load the rewind data when the screen is first displayed
    LaunchedEffect(rewindId) {
        viewModel.loadRewind(rewindId)
    }
    
    // A simple implementation of the rewind detail screen
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rewind") },
                navigationIcon = {
                    IconButton(onClick = onExitRewind) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        // Show loading or content based on state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Viewing rewind $rewindId",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}