package app.logdate.mobile.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import app.logdate.ui.theme.Spacing

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    fun handleFinish() {
        viewModel.finishOnboarding(onFinish)
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl)
            ) {
                Text("Welcome to LogDate.", style = MaterialTheme.typography.displaySmall)
                Text(
                    "A new home for your memories.",
                    style = MaterialTheme.typography.headlineMedium,
                )
                ExtendedFloatingActionButton(
                    onClick = ::handleFinish,
                    text = { Text("Get started") },
                    icon = { Icon(Icons.Outlined.Star, contentDescription = "") },
                )
            }
        }
    }
}