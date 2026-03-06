package app.logdate.navigation.routes

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.logdate.client.domain.account.CheckUsernameAvailabilityUseCase
import app.logdate.client.domain.account.CreatePasskeyAccountUseCase
import app.logdate.client.domain.account.CreateRemoteAccountUseCase
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.feature.core.account.ui.CloudAccountSetupScreen
import app.logdate.feature.core.account.ui.CloudAccountSetupViewModel
import app.logdate.navigation.routes.routeEntry
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
    val isFromOnboarding: Boolean = false,
) : NavKey

/**
 * Extension function to add the consolidated cloud account setup route to an EntryProviderScope.
 *
 * Instead of multiple screens with separate routes, this uses a single route with a state
 * machine within the ViewModel to manage the multi-step flow.
 *
 * @param onBack Callback for handling back navigation
 * @param onSetupCompleted Callback when the entire setup is completed
 * @param onSkip Callback when user chooses to skip (only available during onboarding)
 */
fun EntryProviderScope<NavKey>.consolidatedCloudAccountSetup(
    onBack: () -> Unit,
    onSetupCompleted: () -> Unit,
    onSkip: () -> Unit,
) {
    routeEntry<ConsolidatedCloudAccountRoute> { route ->
        // Inject the required use cases
        val checkUsernameAvailabilityUseCase = koinInject<CheckUsernameAvailabilityUseCase>()
        val createPasskeyAccountUseCase = koinInject<CreatePasskeyAccountUseCase>()
        val createRemoteAccountUseCase = koinInject<CreateRemoteAccountUseCase>()
        val profileRepository = koinInject<ProfileRepository>()

        // Create the consolidated ViewModel
        val viewModel =
            CloudAccountSetupViewModel(
                checkUsernameAvailabilityUseCase = checkUsernameAvailabilityUseCase,
                createPasskeyAccountUseCase = createPasskeyAccountUseCase,
                createRemoteAccountUseCase = createRemoteAccountUseCase,
                profileRepository = profileRepository,
            )

        // Display the consolidated screen
        CloudAccountSetupScreen(
            viewModel = viewModel,
            onComplete = onSetupCompleted,
            onSkip = onSkip,
        )
    }
}
