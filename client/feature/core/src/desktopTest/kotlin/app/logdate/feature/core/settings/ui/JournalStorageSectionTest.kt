package app.logdate.feature.core.settings.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.client.sync.space.migration.JournalDataKind
import app.logdate.client.sync.space.migration.JournalMoveProblem
import app.logdate.client.sync.space.migration.JournalMoveState
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class JournalStorageSectionTest {
    @Test
    fun `a paused upgrade says what is missing and tries again`() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            var retries = 0
            setContent {
                LogDateTheme {
                    JournalStorageSection(
                        state = JournalMoveState.Stopped(JournalMoveProblem.NotOnServer(JournalDataKind.DRAFTS, 2), retryAt = null),
                        onTryAgain = { retries++ },
                        onSwitchBack = {},
                        onEnterRecoveryPhrase = {},
                    )
                }
            }
            onNodeWithText("Drafts: 2 on this device haven’t been backed up yet", substring = true).assertIsDisplayed()
            onNodeWithText("Try again").performClick()
            assertEquals(1, retries)
        }

    @Test
    fun `a missing identity key leads to the recovery phrase`() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            var opened = false
            setContent {
                LogDateTheme {
                    JournalStorageSection(
                        state = JournalMoveState.Stopped(JournalMoveProblem.IdentityKeyMissing, retryAt = null),
                        onTryAgain = {},
                        onSwitchBack = {},
                        onEnterRecoveryPhrase = { opened = true },
                    )
                }
            }
            onNodeWithText("Enter recovery phrase").performClick()
            assertEquals(true, opened)
        }

    @Test
    fun `switching back asks first while the previous copy is kept`() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            var switched = 0
            setContent {
                LogDateTheme {
                    JournalStorageSection(
                        state = JournalMoveState.Moved(Instant.parse("2026-11-20T12:00:00Z")),
                        onTryAgain = {},
                        onSwitchBack = { switched++ },
                        onEnterRecoveryPhrase = {},
                    )
                }
            }
            onNodeWithText("Switch back to standard encryption").performClick()
            assertEquals(0, switched)
            onNodeWithText("Switch back").performClick()
            assertEquals(1, switched)
        }

    @Test
    fun `nothing shows when the upgrade isn't offered`() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            setContent {
                LogDateTheme {
                    JournalStorageSection(JournalMoveState.NotOffered, onTryAgain = {}, onSwitchBack = {}, onEnterRecoveryPhrase = {})
                }
            }
            onNodeWithTag(JournalStorageTags.SECTION).assertDoesNotExist()
        }
}
