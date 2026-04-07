package app.logdate.client.e2e

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.feature.journals.ui.share.ShareJournalContent
import app.logdate.feature.library.ui.detail.MediaDetailContent
import app.logdate.feature.library.ui.detail.MediaDetailUiState
import app.logdate.shared.model.Journal
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.timeline.MediaObjectUiState
import app.logdate.ui.timeline.TimelineSuggestionBlock
import app.logdate.ui.timeline.TimelineSuggestionBlockType
import app.logdate.ui.timeline.TimelineSuggestionBlockUiState
import kotlinx.datetime.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.uuid.Uuid
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharingEntryPointsE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ShareEntryPointHostActivity>()

    @Test
    fun timelineMemoryShareAction_emitsCurrentMemoryRecallState() {
        val memoryState =
            TimelineSuggestionBlockUiState(
                type = TimelineSuggestionBlockType.MEMORY_RECALL,
                message = "Trip to the coast",
                memoryDate = LocalDate(2024, 7, 4),
                mediaUris = listOf(MediaObjectUiState(uri = "content://media/coast.jpg", uid = "coast")),
                people = listOf("Lane"),
            )
        var sharedState: TimelineSuggestionBlockUiState? = null

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                LogDateTheme(dynamicColor = false) {
                    TimelineSuggestionBlock(
                        state = memoryState,
                        onStartWriting = {},
                        onOpenDraft = {},
                        onViewMemoryDay = {},
                        onShareMemory = { sharedState = it },
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline_memory_share_action").assertIsDisplayed().performClick()

        assertNotNull(sharedState)
        assertEquals(memoryState, sharedState)
    }

    @Test
    fun mediaDetailShareAction_emitsCurrentMediaReference() {
        val mediaRef = "content://media/external/images/media/42"
        var sharedMediaRef: String? = null

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                LogDateTheme(dynamicColor = false) {
                    MediaDetailContent(
                        state =
                            MediaDetailUiState.ImageContent(
                                mediaId = Uuid.random(),
                                mediaRef = mediaRef,
                                createdAt = Clock.System.now(),
                                location = null,
                            ),
                        isExpanded = false,
                        onBack = {},
                        onShare = { sharedMediaRef = it },
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_detail_share_action").assertIsDisplayed().performClick()

        assertEquals(mediaRef, sharedMediaRef)
    }

    @Test
    fun journalShareActions_emitCurrentJournalState() {
        val journal = Journal(title = "Road Trip")
        var qrJournal: Journal? = null
        var sharedJournal: Journal? = null

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                LogDateTheme(dynamicColor = false) {
                    ShareJournalContent(
                        journal = journal,
                        onShareToInstagram = {},
                        onShareQrCode = { qrJournal = journal },
                        onShareJournal = { sharedJournal = journal },
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("share_journal_qr_action").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("share_journal_sheet_action").assertIsDisplayed().performClick()

        assertEquals(journal, qrJournal)
        assertEquals(journal, sharedJournal)
    }
}
