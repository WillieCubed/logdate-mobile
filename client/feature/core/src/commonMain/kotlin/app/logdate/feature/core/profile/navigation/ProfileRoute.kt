package app.logdate.feature.core.profile.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.logdate.feature.core.profile.ui.ProfileScreen
import kotlinx.serialization.Serializable

@Serializable
data object ProfileRoute

fun NavController.navigateToProfile() {
    navigate(ProfileRoute)
}

fun NavGraphBuilder.profileRoute(
    onGoBack: () -> Unit,
    onNavigateToBirthday: () -> Unit,
) {
    composable<ProfileRoute> {
        ProfileScreen(
            onBack = onGoBack,
            onNavigateToBirthday = onNavigateToBirthday,
        )
    }
}
