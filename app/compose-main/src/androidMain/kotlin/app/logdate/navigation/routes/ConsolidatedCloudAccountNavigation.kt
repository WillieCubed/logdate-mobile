package app.logdate.navigation.routes

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import app.logdate.client.domain.account.CheckUsernameAvailabilityUseCase
import app.logdate.client.domain.account.CreatePasskeyAccountUseCase
import app.logdate.client.domain.account.CreateRemoteAccountUseCase
import app.logdate.feature.core.account.ui.CloudAccountSetupScreen
import app.logdate.feature.core.account.ui.CloudAccountSetupViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

/**
 * Consolidated route for the cloud account setup flow.
 * 
 * This single route replaces the previous multi-route approach by using a state machine
 * within the ViewModel to handle the various steps in the account creation process.
 * 
 * @property isFromOnboarding Whether this flow was started from the onboarding flow.
 */
@Serializable
data class ConsolidatedCloudAccountRoute(
    val isFromOnboarding: Boolean = false
) : NavKey

/**
 * Extension function to add the consolidated cloud account setup route to an EntryProviderBuilder.
 * 
 * Instead of multiple screens with separate routes, this uses a single route with a state
 * machine within the ViewModel to manage the multi-step flow.
 *
 * @param onBack Callback for handling back navigation
 * @param onSetupCompleted Callback when the entire setup is completed
 * @param onSkip Callback when user chooses to skip (only available during onboarding)
 */
fun EntryProviderBuilder<NavKey>.consolidatedCloudAccountSetup(
    onBack: () -> Unit,
    onSetupCompleted: () -> Unit,
    onSkip: () -> Unit
) {
    entry<ConsolidatedCloudAccountRoute>() { route ->
        // Inject the required use cases
        val checkUsernameAvailabilityUseCase = koinInject<CheckUsernameAvailabilityUseCase>()
        val createPasskeyAccountUseCase = koinInject<CreatePasskeyAccountUseCase>()
        val createRemoteAccountUseCase = koinInject<CreateRemoteAccountUseCase>()
        
        // Create the consolidated ViewModel
        val viewModel = CloudAccountSetupViewModel(
            checkUsernameAvailabilityUseCase = checkUsernameAvailabilityUseCase,
            createPasskeyAccountUseCase = createPasskeyAccountUseCase,
            createRemoteAccountUseCase = createRemoteAccountUseCase
        )
        
        // Display the consolidated screen
        CloudAccountSetupScreen(
            viewModel = viewModel,
            onComplete = onSetupCompleted,
            onSkip = onSkip
        )
    }
}