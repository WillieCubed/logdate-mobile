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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import app.logdate.feature.onboarding.R
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A screen shown to returning users after app setup.
 */
@Composable
internal fun WelcomeBackScreen(
    onFinish: () -> Unit,
    viewModel: WelcomeBackViewModel = hiltViewModel(),
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
            stringResource(R.string.onboarding_welcome_back_title, name),
            style = MaterialTheme.typography.headlineMedium,
        )
        // TODO: Actually reset streak
        Text(
            stringResource(R.string.onboarding_welcome_back_description_streak_reset),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * A view model that exposes state for the [WelcomeBackScreen].
 */
@HiltViewModel
internal class WelcomeBackViewModel @Inject constructor(
    // TODO: Use repository to get user's actual name
) : ViewModel() {
    private val _nameState = MutableStateFlow("user")
    val nameState: StateFlow<String> = _nameState
}

@Preview
@Composable
private fun WelcomeBackScreenPreview() {
    LogDateTheme {
        WelcomeBackScreenContent(name = "Willie")
    }
}