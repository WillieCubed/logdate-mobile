package app.logdate.feature.core.account.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.logdate.feature.core.account.ui.CloudAccountSetupScreen
import app.logdate.feature.core.account.ui.CloudAccountSetupViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
data object CloudAccountSetupRoute

fun NavController.navigateToCloudAccountSetup() {
    navigate(CloudAccountSetupRoute)
}

fun NavGraphBuilder.cloudAccountSetupRoute(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
) {
    composable<CloudAccountSetupRoute> {
        val viewModel: CloudAccountSetupViewModel = koinViewModel()
        CloudAccountSetupScreen(
            viewModel = viewModel,
            onComplete = onComplete,
            onSkip = onSkip,
        )
    }
}
