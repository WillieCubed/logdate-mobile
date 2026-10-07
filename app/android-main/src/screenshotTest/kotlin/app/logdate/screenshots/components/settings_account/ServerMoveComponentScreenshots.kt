package app.logdate.screenshots.components.settings_account

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.core.settings.account.ConnectedServerInfo
import app.logdate.feature.core.settings.account.ServerHealth
import app.logdate.feature.core.settings.account.hosting.HostingContent
import app.logdate.feature.core.settings.account.hosting.HostingUiState
import app.logdate.feature.core.settings.account.move.AccountProblem
import app.logdate.feature.core.settings.account.move.ChooseProblem
import app.logdate.feature.core.settings.account.move.Cleanup
import app.logdate.feature.core.settings.account.move.MoveEndpoint
import app.logdate.feature.core.settings.account.move.MoveProgress
import app.logdate.feature.core.settings.account.move.MoveServerContent
import app.logdate.feature.core.settings.account.move.MoveServerUiState
import app.logdate.feature.core.settings.account.move.MoveSurvey
import app.logdate.feature.core.settings.account.move.ServerMoveRecord
import app.logdate.feature.core.settings.ui.CheckedServer
import app.logdate.feature.core.settings.ui.ServerProblem
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerDescriptor
import com.android.tools.screenshot.PreviewTest

// ─── Move to another server ─────────────────────────────────────────────────────

private val moveSource = MoveEndpoint("https://cloud.logdate.app", null)
private val moveDestination =
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
    )
private val moveRecord =
    ServerMoveRecord(
        from = moveSource,
        to = MoveEndpoint(moveDestination.origin, moveDestination.descriptor),
        phase = ServerMoveRecord.Phase.UPLOADING,
        uploadTotal = 368,
    )

@Composable
private fun MoveStep(state: MoveServerUiState) {
    ScreenshotTheme {
        MoveServerContent(
            state = state,
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
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun MoveServer_Choose() =
    MoveStep(
        MoveServerUiState.ChooseServer(
            source = moveSource,
            address = "journal.example",
            problem = ChooseProblem.Server(ServerProblem.UNREACHABLE),
        ),
    )

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun MoveServer_Review() =
    MoveStep(
        MoveServerUiState.Review(
            source = moveSource,
            destination = moveDestination,
            survey = MoveSurvey(entries = 312, journals = 4, media = 48, drafts = 3, remoteOnlyMedia = 0),
        ),
    )

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun MoveServer_CreateAccount() =
    MoveStep(
        MoveServerUiState.CreateAccount(
            source = moveSource,
            destination = moveDestination,
            survey = MoveSurvey(entries = 312, journals = 4, media = 48, drafts = 3, remoteOnlyMedia = 0),
            username = "alex",
            problem = AccountProblem.USERNAME_TAKEN,
        ),
    )

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun MoveServer_Uploading() =
    MoveStep(
        MoveServerUiState.Uploading(
            record = moveRecord,
            progress = MoveProgress(remaining = 120, failed = 0, isSyncing = true, syncedSinceSwitch = false),
        ),
    )

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun MoveServer_Finished() = MoveStep(MoveServerUiState.Finished(record = moveRecord, cleanup = Cleanup.Offered))

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun Hosting_WithMove() {
    ScreenshotTheme {
        HostingContent(
            state =
                HostingUiState(
                    server =
                        ConnectedServerInfo(
                            origin = "https://cloud.logdate.app",
                            displayName = "LogDate Cloud",
                            isLogDateCloud = true,
                            publishesIdentityChanges = false,
                        ),
                    health = ServerHealth.Reachable("1.4.0"),
                    canMoveAccount = true,
                ),
            onBack = {},
            onCheckAgain = {},
        )
    }
}
