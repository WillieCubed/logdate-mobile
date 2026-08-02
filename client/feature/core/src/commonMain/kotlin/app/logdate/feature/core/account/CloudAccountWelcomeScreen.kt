@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import app.logdate.feature.core.settings.ui.ServerPreset
import app.logdate.feature.core.settings.ui.ServerSelectionState
import app.logdate.ui.adaptive.FoldableTabletopLayout
import app.logdate.ui.AdaptiveLayout
import app.logdate.ui.theme.Spacing

@Composable
fun CloudAccountWelcomeScreen(
    onContinue: () -> Unit,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
    serverSelectionState: ServerSelectionState,
    onSelectServerPreset: (ServerPreset) -> Unit,
    onCustomServerUrlChange: (String) -> Unit,
    onShowCustomServerInfo: () -> Unit,
    isPasskeySupported: Boolean = true,
    modifier: Modifier = Modifier,
) {
    CloudAccountWelcomeContent(
        onContinue = onContinue,
        onSignIn = onSignIn,
        onSkip = onSkip,
        serverSelectionState = serverSelectionState,
        onSelectServerPreset = onSelectServerPreset,
        onCustomServerUrlChange = onCustomServerUrlChange,
        onShowCustomServerInfo = onShowCustomServerInfo,
        isPasskeySupported = isPasskeySupported,
        modifier = modifier,
    )
}

@Composable
fun CloudAccountWelcomeContent(
    onContinue: () -> Unit,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
    serverSelectionState: ServerSelectionState,
    onSelectServerPreset: (ServerPreset) -> Unit,
    onCustomServerUrlChange: (String) -> Unit,
    onShowCustomServerInfo: () -> Unit,
    isPasskeySupported: Boolean = true,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth < 700.dp) {
            CloudAccountWelcomeCompactContent(
                onContinue = onContinue,
                onSignIn = onSignIn,
                onSkip = onSkip,
                serverSelectionState = serverSelectionState,
                onSelectServerPreset = onSelectServerPreset,
                onCustomServerUrlChange = onCustomServerUrlChange,
                onShowCustomServerInfo = onShowCustomServerInfo,
                isPasskeySupported = isPasskeySupported,
            )
        } else {
            FoldableTabletopLayout(
                modifier = Modifier.fillMaxSize(),
                minPaneHeight = 260.dp,
                topPane = {
                    CloudAccountWelcomeIntroPane(
                        serverSelectionState = serverSelectionState,
                        onSelectServerPreset = onSelectServerPreset,
                        onCustomServerUrlChange = onCustomServerUrlChange,
                        onShowCustomServerInfo = onShowCustomServerInfo,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                bottomPane = {
                    CloudAccountWelcomeActionPane(
                        onContinue = onContinue,
                        onSignIn = onSignIn,
                        onSkip = onSkip,
                        isPasskeySupported = isPasskeySupported,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                standardContent = {
                    AdaptiveLayout(
                        useCompactLayout = false,
                        modifier = Modifier.fillMaxSize(),
                        supplementalContent = {
                            CloudAccountWelcomeIntroPane(
                                serverSelectionState = serverSelectionState,
                                onSelectServerPreset = onSelectServerPreset,
                                onCustomServerUrlChange = onCustomServerUrlChange,
                                onShowCustomServerInfo = onShowCustomServerInfo,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                        mainContent = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CloudAccountWelcomeActionPane(
                                    onContinue = onContinue,
                                    onSignIn = onSignIn,
                                    onSkip = onSkip,
                                    isPasskeySupported = isPasskeySupported,
                                    modifier = Modifier.widthIn(max = 560.dp).fillMaxSize(),
                                )
                            }
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun CloudAccountWelcomeCompactContent(
    onContinue: () -> Unit,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
    serverSelectionState: ServerSelectionState,
    onSelectServerPreset: (ServerPreset) -> Unit,
    onCustomServerUrlChange: (String) -> Unit,
    onShowCustomServerInfo: () -> Unit,
    isPasskeySupported: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        CloudAccountWelcomeIntro(
            serverSelectionState = serverSelectionState,
            onSelectServerPreset = onSelectServerPreset,
            onCustomServerUrlChange = onCustomServerUrlChange,
            onShowCustomServerInfo = onShowCustomServerInfo,
        )
        CloudAccountWelcomeActionCard(
            onContinue = onContinue,
            onSignIn = onSignIn,
            onSkip = onSkip,
            isPasskeySupported = isPasskeySupported,
        )
        CloudAccountWelcomeBenefits()
    }
}

@Preview
@Composable
private fun CloudAccountWelcomeScreenPreview() {
    MaterialTheme {
        Surface {
            CloudAccountWelcomeContent(
                onContinue = {},
                onSignIn = {},
                onSkip = {},
                serverSelectionState = ServerSelectionState(),
                onSelectServerPreset = {},
                onCustomServerUrlChange = {},
                onShowCustomServerInfo = {},
            )
        }
    }
}
