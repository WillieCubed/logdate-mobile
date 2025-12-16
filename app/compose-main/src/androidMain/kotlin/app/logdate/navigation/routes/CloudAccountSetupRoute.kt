package app.logdate.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.logdate.client.domain.account.CheckUsernameAvailabilityUseCase
import app.logdate.client.domain.account.CreatePasskeyAccountUseCase
import app.logdate.client.domain.account.CreateRemoteAccountUseCase
import app.logdate.feature.core.account.ui.CloudAccountSetupScreen
import app.logdate.feature.core.account.ui.CloudAccountSetupViewModel
import org.koin.compose.koinInject

/**
 * Route constants for cloud account setup.
 */
object CloudAccountSetupDestinations {
    const val CLOUD_ACCOUNT_SETUP_ROUTE = "cloud_account_setup"
}

/**
 * Adds the consolidated cloud account setup route to the navigation graph.
 */
fun NavGraphBuilder.cloudAccountSetupGraph(
    navController: NavController,
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    composable(route = CloudAccountSetupDestinations.CLOUD_ACCOUNT_SETUP_ROUTE) { entry ->
        CloudAccountSetupRoute(
            navController = navController,
            onComplete = onComplete,
            onSkip = onSkip,
            entry = entry
        )
    }
}

/**
 * Cloud account setup route that uses the consolidated ViewModel.
 */
@Composable
fun CloudAccountSetupRoute(
    navController: NavController,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    entry: NavBackStackEntry
) {
    // Inject all required dependencies
    val checkUsernameAvailabilityUseCase = koinInject<CheckUsernameAvailabilityUseCase>()
    val createPasskeyAccountUseCase = koinInject<CreatePasskeyAccountUseCase>()
    val createRemoteAccountUseCase = koinInject<CreateRemoteAccountUseCase>()
    
    // Create the consolidated ViewModel
    val viewModel = remember {
        CloudAccountSetupViewModel(
            checkUsernameAvailabilityUseCase = checkUsernameAvailabilityUseCase,
            createPasskeyAccountUseCase = createPasskeyAccountUseCase,
            createRemoteAccountUseCase = createRemoteAccountUseCase
        )
    }
    
    CloudAccountSetupScreen(
        viewModel = viewModel,
        onComplete = onComplete,
        onSkip = onSkip
    )
}