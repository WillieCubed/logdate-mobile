package app.logdate.feature.onboarding.navigation

//const val ONBOARDING_ROUTE = "onboarding"
//const val ONBOARDING_INVITE = "onboarding/invite"
//const val ONBOARDING_EXPLAINER = "onboarding/app_overview"
//const val ONBOARDING_SIGN_IN = "onboarding/sign_in"
//const val ONBOARDING_SIGN_IN_2FA = "onboarding/2fa"
//const val ONBOARDING_NEW_ENTRY = "onboarding/new_entry"
//const val ONBOARDING_NEW_ENTRY_REVIEW = "onboarding/new_entry_review"
//const val ONBOARDING_IMPORT_MEMORIES = "onboarding/import"
//const val ONBOARDING_ENABLE_NOTIFICATIONS = "onboarding/notification_enable"
//const val ONBOARDING_NOTIFICATIONS_CONFIRMATION = "onboarding/notification_confirmation"
//const val ONBOARDING_BACKUP_SYNC_CONFIGURE = "onboarding/backup_sync_configure"
//const val ONBOARDING_BACKUP_SYNC_REVIEW = "onboarding/backup_sync_review"
//const val ONBOARDING_LAST = "onboarding/last"
//const val ONBOARDING_WELCOME_BACK = "onboarding/welcome_back"
//
///**
// * Launches the onboarding flow.
// *
// * This navigate the user to the onboarding flow, clearing any previous screens.
// */
//fun NavController.launchOnboarding() {
//    navigate(ONBOARDING_ROUTE) {
//        popUpTo(ONBOARDING_ROUTE) {
//            inclusive = true
//        }
//    }
//}
//
//fun NavGraphBuilder.onboardingGraph(
//    useCompactLayout: Boolean = true,
//    onNavigateBack: () -> Unit,
//    onGoToItem: (id: String) -> Unit,
//    onFinish: () -> Unit,
//) {
//    composable(route = ONBOARDING_ROUTE) {
//        OnboardingStartScreen(
//            onNext = {
//                onGoToItem(ONBOARDING_EXPLAINER)
//            },
//            onStartFromBackup = {
//                onGoToItem(ONBOARDING_LAST)
//            },
//        )
//    }
//    composable(route = ONBOARDING_INVITE) {
//    }
//    composable(route = ONBOARDING_EXPLAINER) {
//        OnboardingOverviewScreen(
//            onBack = onNavigateBack,
//            onNext = {
//                onGoToItem(ONBOARDING_NEW_ENTRY)
//            },
//        )
//    }
//    composable(route = ONBOARDING_SIGN_IN) {
//        onGoToItem(ONBOARDING_LAST)
//    }
//    composable(route = ONBOARDING_SIGN_IN_2FA) {
//    }
//    composable(route = ONBOARDING_NEW_ENTRY) {
//        EntryCreationScreenWrapper(
//            onBack = onNavigateBack,
//            onNext = {
//                onGoToItem(ONBOARDING_ENABLE_NOTIFICATIONS)
//            },
//            useCompactLayout = useCompactLayout,
//        )
//    }
//    composable(route = ONBOARDING_NEW_ENTRY_REVIEW) {
//    }
//    composable(route = ONBOARDING_ENABLE_NOTIFICATIONS) {
//        OnboardingNotificationScreen(
//            onBack = {
//                onNavigateBack()
//            },
//            onNext = {
//                onGoToItem(ONBOARDING_NOTIFICATIONS_CONFIRMATION)
//            },
//            useCompactLayout = useCompactLayout,
//        )
//    }
//    composable(route = ONBOARDING_NOTIFICATIONS_CONFIRMATION) {
//        OnboardingNotificationConfirmationScreen(
//            // TODO: Skip ONBOARDING_ENABLE_NOTIFICATIONS if notifications are already enabled
//            onBack = onNavigateBack,
//            onNext = {
//                onGoToItem(ONBOARDING_LAST)
//            },
//            useCompactLayout = useCompactLayout,
//        )
//    }
//    composable(route = ONBOARDING_IMPORT_MEMORIES) {
//    }
//    composable(route = ONBOARDING_BACKUP_SYNC_CONFIGURE) {
//    }
//    composable(route = ONBOARDING_BACKUP_SYNC_REVIEW) {
//    }
//    composable(route = ONBOARDING_LAST) {
//        OnboardingCompletionScreen(
//            onFinish = onFinish,
//        )
//    }
//    composable(
//        route = ONBOARDING_WELCOME_BACK,
//        exitTransition = {
//            scaleOut()
//        },
//    ) {
//        WelcomeBackScreen(onFinish = onFinish)
//    }
//}