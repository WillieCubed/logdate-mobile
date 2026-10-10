package app.logdate.feature.editor.handoff

import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class HandoffDraftGateTest {
    private val draftId = Uuid.random()

    @Test
    fun `a draft already on this device opens in the editor`() =
        runSkikoComposeUiTest(size = Size(390f, 780f)) {
            val receiver = HandoffDraftReceiver(hasDraft = { true }, refresher = { error("Nothing needs fetching") })
            setContent {
                LogDateTheme(dynamicColor = false) {
                    HandoffDraftGate(draftId, receiver) { opened -> Text("Editing $opened") }
                }
            }
            waitForIdle()
            onNodeWithText("Editing $draftId").assertExists()
        }

    @Test
    fun `a missing draft says why, and trying again fetches drafts again`() =
        runSkikoComposeUiTest(size = Size(390f, 780f)) {
            var fetches = 0
            var arrived = false
            val receiver =
                HandoffDraftReceiver(
                    hasDraft = { arrived },
                    refresher = {
                        fetches++
                        DraftRefreshResult.Unavailable
                    },
                )
            setContent {
                LogDateTheme(dynamicColor = false) {
                    HandoffDraftGate(draftId, receiver) { opened -> Text("Editing $opened") }
                }
            }
            waitUntil { fetches == 1 }
            onNodeWithText(BACKUP_UNAVAILABLE).assertExists()

            arrived = true
            onNodeWithText("Try again").performClick()
            waitForIdle()
            onNodeWithText("Editing $draftId").assertExists()
        }

    @Test
    fun `starting a new entry stops waiting and opens an empty editor`() =
        runSkikoComposeUiTest(size = Size(390f, 780f)) {
            val receiver =
                HandoffDraftReceiver(
                    hasDraft = { false },
                    refresher = { DraftRefreshResult.Refreshed },
                )
            setContent {
                LogDateTheme(dynamicColor = false) {
                    HandoffDraftGate(draftId, receiver) { opened -> Text("Editing $opened") }
                }
            }
            onNodeWithText("Opening your entry from another device").assertExists()
            onNodeWithText("Start a new entry").performClick()
            waitForIdle()
            onNodeWithText("Editing null").assertExists()
        }

    private companion object {
        const val BACKUP_UNAVAILABLE =
            "Backup is off or needs attention on this device. Entries move between devices through backup, so check Backup in Settings."
    }
}
