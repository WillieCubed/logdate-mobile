package app.logdate.feature.core.main

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.feature.core.sync.AccountSyncStatus
import app.logdate.feature.core.sync.SyncAction
import app.logdate.feature.core.sync.SyncPresentation
import app.logdate.ui.streak.CampfirePhase
import app.logdate.ui.streak.CampfirePresentation
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.workspace.WorkspaceAppBar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HomeWorkspaceAccountActionTest {
    @Test fun syncFactsAndStreakRemainAvailableWithoutAStatusScreen() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            var action: SyncAction? = null
            var openedStreak = false
            setContent {
                LogDateTheme {
                    WorkspaceAppBar("Timeline", {}, actions = {
                        HomeWorkspaceAccountAction(
                            SyncPresentation.Pending(2),
                            CampfirePresentation(CampfirePhase.BURNING, runDays = 4),
                            {},
                            { openedStreak = true },
                            { action = it },
                        )
                    })
                }
            }
            onNodeWithTag("logdate_home_sync_status").assertDoesNotExist()
            onNodeWithText("Journaling streak").assertDoesNotExist()
            onNodeWithTag("workspace_account").performClick()
            onNodeWithText("Preparing to sync…").assertExists()
            onNodeWithText("Backup status").assertDoesNotExist()
            onNodeWithText("2 items waiting to back up").assertDoesNotExist()
            assertEquals(null, action)
            onNodeWithText("Journaling streak").performClick()
            assertTrue(openedStreak)
        }

    @Test fun wifiWaitOffersConsentInAccountMenu() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            var action: SyncAction? = null
            setContent {
                LogDateTheme {
                    HomeWorkspaceAccountAction(
                        SyncPresentation.Pending(1),
                        null,
                        {},
                        {},
                        { action = it },
                        accountStatus = AccountSyncStatus.WAITING_FOR_WIFI,
                    )
                }
            }
            onNodeWithTag("workspace_account").performClick()
            onNodeWithText("Sync using mobile data").performClick()
            assertEquals(SyncAction.UseMobileData, action)
        }

    @Test fun lockedEntriesWaitWithoutAnUnusableExplanation() =
        runDesktopComposeUiTest(width = 720, height = 900) {
            setContent {
                LogDateTheme {
                    HomeWorkspaceAccountAction(
                        SyncPresentation.NeedsRecovery,
                        null,
                        {},
                        {},
                        {},
                        accountStatus = AccountSyncStatus.DEVICE_ACCESS_REQUIRED,
                    )
                }
            }
            onNodeWithTag("workspace_account").performClick()
            onNodeWithText("Some existing entries are not unlocked on this device yet.").assertDoesNotExist()
            onNodeWithText("Sync could not finish. LogDate will try again automatically.").assertExists()
            onNodeWithText("Enter phrase").assertDoesNotExist()
        }
}
