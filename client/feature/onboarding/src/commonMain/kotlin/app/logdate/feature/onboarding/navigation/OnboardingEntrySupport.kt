@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.onboarding.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.logdate.feature.onboarding.flow.OnboardingEntryMode
import app.logdate.feature.onboarding.flow.OnboardingStep
import app.logdate.feature.onboarding.ui.MemorySelectionScreen
import app.logdate.feature.onboarding.ui.MemorySelectionViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun FirstEntryMemorySelectionEntry(
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val viewModel = koinViewModel<MemorySelectionViewModel>()
    val coroutineScope = rememberCoroutineScope()
    MemorySelectionScreen(
        uiState = viewModel.uiState.collectAsState().value,
        onBack = onBack,
        onContinue = {
            coroutineScope.launch {
                viewModel.processSelectedMemories().onSuccess { onComplete() }
            }
        },
        onToggleMemorySelection = viewModel::toggleMemorySelection,
        onLoadMoreMemories = viewModel::loadMoreMemories,
        onRefreshMemories = viewModel::refreshMemories,
    )
}

internal fun routeForStep(step: OnboardingStep): OnboardingBaseRoute =
    when (step) {
        OnboardingStep.PERSONAL_INTRO -> PersonalIntro
        OnboardingStep.APP_OVERVIEW -> AppOverview
        OnboardingStep.MEMORY_IMPORT -> MemoryImport
        OnboardingStep.MEMORY_SELECTION -> MemorySelection
        OnboardingStep.ACCOUNT -> AccountCreation
        OnboardingStep.RECOVERY_PHRASE -> RecoveryPhrase
        OnboardingStep.BIRTHDAY -> BirthdayIntro
        OnboardingStep.RECOMMENDATIONS -> FeatureRecommendations
        OnboardingStep.DAY_BOUNDARIES -> FeatureDayBoundaries
        OnboardingStep.LOCATION -> FeatureLocationTimeline
        OnboardingStep.NOTIFICATIONS -> FeatureNotifications
        OnboardingStep.WATCH -> FeatureWatch
        OnboardingStep.COMPLETE -> OnboardingComplete
        OnboardingStep.WELCOME_BACK -> WelcomeBack
    }

internal fun terminalStepFor(entryMode: OnboardingEntryMode): OnboardingStep =
    when (entryMode) {
        OnboardingEntryMode.FRESH -> OnboardingStep.COMPLETE
        OnboardingEntryMode.CONTINUE_SETUP -> OnboardingStep.WELCOME_BACK
    }

/**
 * Applies window insets to onboarding screens that lay themselves out edge to edge.
 *
 * Most screens here sit inside a Scaffold that handles this; these four do not, so their
 * headings drew underneath the status bar and their actions under the navigation bar.
 */
@Composable
internal fun OnboardingInsets(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        content()
    }
}
