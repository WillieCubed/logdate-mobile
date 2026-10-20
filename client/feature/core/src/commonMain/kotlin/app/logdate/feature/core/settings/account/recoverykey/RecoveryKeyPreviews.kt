@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.account.recoverykey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun RecoveryKeySetupPreview() {
    RecoveryKeyContent(
        state =
            RecoveryKeyUiState.Ready(
                handle = "alice.logdate.app",
                serverName = "LogDate Cloud",
                phrase = PhraseStatus.CONFIRMED,
                key = KeyStatus.OFF,
            ),
        destinations = RecoveryKeyDestinations(onBack = {}, onRecoveryPhrase = {}),
        actions = RecoveryKeyActions(),
    )
}

@Preview
@Composable
private fun RecoveryKeyOnPreview() {
    RecoveryKeyContent(
        state =
            RecoveryKeyUiState.Ready(
                handle = "alice.logdate.app",
                serverName = "LogDate Cloud",
                phrase = PhraseStatus.CONFIRMED,
                key = KeyStatus.ON,
            ),
        destinations = RecoveryKeyDestinations(onBack = {}, onRecoveryPhrase = {}),
        actions = RecoveryKeyActions(),
    )
}
