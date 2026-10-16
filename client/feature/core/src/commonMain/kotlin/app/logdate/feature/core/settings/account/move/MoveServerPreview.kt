@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.account.move

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.core.settings.ui.CheckedServer
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerDescriptor

@Preview
@Composable
private fun MoveServerReviewPreview() {
    val cloud = MoveEndpoint("https://cloud.logdate.app", null)
    MoveServerContent(
        state =
            MoveServerUiState.Review(
                source = cloud,
                destination =
                    CheckedServer(
                        origin = "https://journal.example.com",
                        descriptor =
                            ServerDescriptor(
                                serverOrigin = "https://journal.example.com",
                                apiBaseUrl = "https://journal.example.com/api/v1",
                                deploymentKind = DeploymentKind.SELF_HOSTED,
                                displayName = "Alex's journal server",
                            ),
                        version = "1.4.0",
                    ),
                survey = MoveSurvey(entries = 312, journals = 4, media = 48, drafts = 3, remoteOnlyMedia = 0),
            ),
        onClose = {},
        onAddressChange = {},
        onCheckServer = {},
        onBack = {},
        onContinueToAccount = {},
        onCreateAccount = {},
        onSignInInstead = {},
        onDeleteSource = {},
        onSignInToSourceAndDelete = {},
        onKeepSource = {},
    )
}
