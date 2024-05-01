package app.logdate.feature.onboarding.navigation

import androidx.compose.animation.scaleOut
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.logdate.feature.onboarding.ui.EntryCreationScreenWrapper
import app.logdate.feature.onboarding.ui.OnboardingCompletionScreen
import app.logdate.feature.onboarding.ui.OnboardingOverviewScreen
import app.logdate.feature.onboarding.ui.OnboardingStartScreen
import app.logdate.feature.onboarding.ui.WelcomeBackScreen

const val ONBOARDING_ROUTE = "onboarding"
const val ONBOARDING_INVITE = "onboarding/invite"
const val ONBOARDING_EXPLAINER = "onboarding/app_overview"
const val ONBOARDING_SIGN_IN = "onboarding/sign_in"
const val ONBOARDING_SIGN_IN_2FA = "onboarding/2fa"
const val ONBOARDING_NEW_ENTRY = "onboarding/new_entry"
const val ONBOARDING_NEW_ENTRY_REVIEW = "onboarding/new_entry_review"
const val ONBOARDING_IMPORT_MEMORIES = "onboarding/import"
const val ONBOARDING_BACKUP_SYNC_CONFIGURE = "onboarding/backup_sync_configure"
const val ONBOARDING_BACKUP_SYNC_REVIEW = "onboarding/backup_sync_review"
const val ONBOARDING_LAST = "onboarding/last"
const val ONBOARDING_WELCOME_BACK = "onboarding/welcome_back"

fun NavGraphBuilder.onboardingGraph(
    useCompactLayout: Boolean = true,
    onNavigateBack: () -> Unit,
    onGoToItem: (id: String) -> Unit,
    onFinish: () -> Unit,
) {
    composable(route = ONBOARDING_ROUTE) {
        OnboardingStartScreen(
            onNext = {
                onGoToItem(ONBOARDING_EXPLAINER)
            },
            onStartFromBackup = {
                onGoToItem(ONBOARDING_LAST)
            },
        )
    }
    composable(route = ONBOARDING_INVITE) {
    }
    composable(route = ONBOARDING_EXPLAINER) {
        OnboardingOverviewScreen(
            onBack = onNavigateBack,
            onNext = {
                onGoToItem(ONBOARDING_NEW_ENTRY)
            },
        )
    }
    composable(route = ONBOARDING_SIGN_IN) {
        onGoToItem(ONBOARDING_LAST)
    }
    composable(route = ONBOARDING_SIGN_IN_2FA) {
    }
    composable(route = ONBOARDING_NEW_ENTRY) {
        EntryCreationScreenWrapper(
            onBack = onNavigateBack,
            onNext = {
                onGoToItem(ONBOARDING_LAST)
            },
            useCompactLayout = useCompactLayout,
        )
    }
    composable(route = ONBOARDING_NEW_ENTRY_REVIEW) {
    }
    composable(route = ONBOARDING_IMPORT_MEMORIES) {
    }
    composable(route = ONBOARDING_BACKUP_SYNC_CONFIGURE) {
    }
    composable(route = ONBOARDING_BACKUP_SYNC_REVIEW) {
    }
    composable(route = ONBOARDING_LAST) {
        OnboardingCompletionScreen(
            onFinish = onFinish,
        )
    }
    composable(
        route = ONBOARDING_WELCOME_BACK,
        exitTransition = {
            scaleOut()
        },
    ) {
        WelcomeBackScreen(onFinish = onFinish)
    }
}