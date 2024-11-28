package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.feature.onboarding.generated.resources.onboarding_welcome_back_description_streak_reset
import logdate.client.feature.onboarding.generated.resources.onboarding_welcome_back_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

/**
 * A screen shown to returning users after app setup.
 */
@Composable
internal fun WelcomeBackScreen(
    onFinish: () -> Unit,
    viewModel: WelcomeBackViewModel = koinViewModel(),
) {
    // I can't believe we have to use a view model for this
    val name by viewModel.nameState.collectAsState()

    LaunchedEffect(Unit) {
        launch {
            delay(1_000)
            onFinish()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        WelcomeBackScreenContent(name = name)
    }
}

@Composable
private fun WelcomeBackScreenContent(name: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xl, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.onboarding_welcome_back_title, name),
            style = MaterialTheme.typography.headlineMedium,
        )
        // TODO: Actually reset streak
        Text(
            stringResource(Res.string.onboarding_welcome_back_description_streak_reset),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview
@Composable
private fun WelcomeBackScreenPreview() {
    LogDateTheme {
        WelcomeBackScreenContent(name = "Willie")
    }
}