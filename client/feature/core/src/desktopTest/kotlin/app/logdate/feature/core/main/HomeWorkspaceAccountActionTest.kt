package app.logdate.feature.core.main

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
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
    @Test fun backupAndStreakRemainAvailableWithoutDedicatedHeaderControls() =
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
            onNodeWithText("Backup status").performClick()
            assertEquals(SyncAction.OpenStatus, action)
            onNodeWithTag("workspace_account").performClick()
            onNodeWithText("Journaling streak").performClick()
            assertTrue(openedStreak)
        }

    @Test fun recoveryCanBeOpenedFromTheQuietAccountIndicator() =
        runDesktopComposeUiTest(width = 720, height = 900) {
            var action: SyncAction? = null
            setContent {
                LogDateTheme { HomeWorkspaceAccountAction(SyncPresentation.NeedsRecovery, null, {}, {}, { action = it }) }
            }
            onNodeWithTag("workspace_account").performClick()
            onNodeWithText("Enter phrase").performClick()
            assertEquals(SyncAction.EnterRecoveryPhrase, action)
        }
}
