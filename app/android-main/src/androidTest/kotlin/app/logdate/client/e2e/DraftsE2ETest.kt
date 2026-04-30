package app.logdate.client.e2e

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.EditorActivity
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.di.appModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * End-to-end tests for the drafts system in the editor.
 *
 * These tests verify the full user flows:
 * - Writing content, saving, and verifying drafts are cleaned up
 * - Using "Save Draft" from the exit confirmation dialog
 * - Discarding changes and verifying no draft is persisted
 * - Loading a pre-existing draft and publishing it
 *
 * Run with:
 * ```
 * ./gradlew :app:android-main:smokeDevicesGroupDebugAndroidTest -Plogdate.androidTestClass=app.logdate.client.e2e.DraftsE2ETest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class DraftsE2ETest {
    private val fakeDraftRepository = FakeDraftRepository()

    private val testModule = module {
        single<EntryDraftRepository> { fakeDraftRepository }
    }

    private val koinRule = DraftsKoinModuleOverrideRule(testModule)
    private val composeRule = createAndroidComposeRule<EditorActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(koinRule).around(composeRule)

    /**
     * Test: Write content → save → verify draft is cleaned up.
     *
     * This is the primary regression test for the bug where drafts were
     * never deleted after publishing because the combine block overwrote draftId.
     */
    @Test
    fun writeContentAndSave_draftIsDeletedAfterPublish() {
        startTextEntry()

        // Type some content
        composeRule.onNodeWithTag("editor_text_input").performTextInput("My journal entry for today")
        composeRule.waitForIdle()

        // Wait for auto-save to kick in (2s debounce + processing)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            fakeDraftRepository.currentDrafts.isNotEmpty()
        }

        // Verify a draft was auto-saved
        assert(fakeDraftRepository.currentDrafts.size == 1) {
            "Expected 1 draft, found ${fakeDraftRepository.currentDrafts.size}"
        }

        // Save the entry
        composeRule.onNodeWithTag("editor_save_button").performClick()

        // Wait for save to complete and draft to be cleaned up
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fakeDraftRepository.currentDrafts.isEmpty()
        }

        // Verify draft was deleted
        assert(fakeDraftRepository.currentDrafts.isEmpty()) {
            "Draft should be deleted after publishing, but ${fakeDraftRepository.currentDrafts.size} remain"
        }
    }

    /**
     * Test: Write content → auto-save → back → draft persists.
     *
     * The current editor flow persists drafts via auto-save before exit rather than
     * requiring an explicit "Save Draft" confirmation.
     */
    @Test
    fun writeContentAndSaveAsDraft_draftPersistsAfterExit() {
        startTextEntry()

        // Type content
        composeRule.onNodeWithTag("editor_text_input").performTextInput("Draft content to save")
        composeRule.waitForIdle()

        // Wait for auto-save to persist the draft before exiting.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fakeDraftRepository.currentDrafts.isNotEmpty()
        }

        // Use the toolbar back action so the editor's current exit flow runs.
        composeRule.onNodeWithContentDescription("Back").performClick()

        // Verify draft was saved
        assert(fakeDraftRepository.currentDrafts.isNotEmpty()) {
            "Draft should persist after 'Save Draft' was tapped"
        }
    }

    /**
     * Test: Write content → back immediately → no draft persisted.
     *
     * The current editor exits immediately when unsaved text has not yet been auto-saved,
     * so this verifies that the quick-exit path does not create an orphan draft.
     */
    @Test
    fun writeContentAndDiscard_noNewDraftsAfterDiscard() {
        val draftsBeforeTest = fakeDraftRepository.currentDrafts.size

        startTextEntry()

        // Type content quickly (before auto-save debounce fires)
        composeRule.onNodeWithTag("editor_text_input").performTextInput("Temporary content")
        composeRule.waitForIdle()

        // Use the toolbar back action so the editor's current exit flow runs.
        composeRule.onNodeWithContentDescription("Back").performClick()

        // Give the auto-save debounce window time to expire after exit.
        Thread.sleep(3_000)

        assert(fakeDraftRepository.currentDrafts.size == draftsBeforeTest) {
            "Expected no new drafts after quick exit, but found ${fakeDraftRepository.currentDrafts.size - draftsBeforeTest} new draft(s)"
        }
    }

    /**
     * Test: Pre-seed a draft → open drafts → select it → content loads → save → draft deleted.
     *
     * Tests the full draft lifecycle from loading through publishing.
     */
    @Test
    fun loadDraftAndPublish_draftIsDeletedAfterSave() {
        // Pre-seed a draft
        val draftContent = "Previously saved draft content"
        val draftId = fakeDraftRepository.seedDraft(draftContent)

        openDraftsDialog()

        // Verify draft appears in the list with correct preview text
        composeRule.onNodeWithText(draftContent, substring = true).assertIsDisplayed()

        // Select the draft
        composeRule.onNodeWithText(draftContent, substring = true).performClick()
        composeRule.waitForIdle()

        // Verify text content was loaded into the editor
        composeRule.onNodeWithTag("editor_text_input")
            .assertTextContains(draftContent)

        // Save the entry
        composeRule.onNodeWithTag("editor_save_button").performClick()

        // Wait for draft to be cleaned up
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fakeDraftRepository.currentDrafts.isEmpty()
        }

        // Verify draft was deleted after publishing
        assert(fakeDraftRepository.currentDrafts.isEmpty()) {
            "Draft should be deleted after publishing"
        }
    }

    /**
     * Test: Open drafts dialog when no drafts exist → empty state shown.
     */
    @Test
    fun openDraftsDialog_emptyStateShown() {
        // Ensure no drafts exist
        assert(fakeDraftRepository.currentDrafts.isEmpty())

        openDraftsDialog()

        // Verify empty state is displayed
        composeRule.onNodeWithTag("drafts_empty_state").assertIsDisplayed()
    }

    private fun openDraftsDialog() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("More options")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Manage drafts").performClick()
        composeRule.waitForIdle()
    }

    private fun startTextEntry() {
        composeRule.onNodeWithContentDescription("Start text entry").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("editor_text_input")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}

/**
 * Fake [EntryDraftRepository] for E2E tests.
 * Provides direct access to draft state for assertions.
 */
private class FakeDraftRepository : EntryDraftRepository {
    private val drafts = MutableStateFlow<List<EntryDraft>>(emptyList())

    val currentDrafts: List<EntryDraft>
        get() = drafts.value

    fun seedDraft(textContent: String): Uuid {
        val now = Clock.System.now()
        val id = Uuid.random()
        val draft = EntryDraft(
            id = id,
            notes = listOf(
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = now,
                    lastUpdated = now,
                    content = textContent,
                ),
            ),
            createdAt = now,
            updatedAt = now,
        )
        drafts.value = drafts.value + draft
        return id
    }

    override fun getDrafts(): Flow<List<EntryDraft>> = drafts

    override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> {
        val draft = drafts.value.firstOrNull { it.id == uid }
        return if (draft != null) {
            flowOf(Result.success(draft))
        } else {
            flowOf(Result.failure(NoSuchElementException("Draft not found")))
        }
    }

    override suspend fun createDraft(notes: List<JournalNote>): Uuid {
        val now = Clock.System.now()
        val draft = EntryDraft(
            id = Uuid.random(),
            notes = notes,
            createdAt = now,
            updatedAt = now,
        )
        drafts.value = drafts.value + draft
        return draft.id
    }

    override suspend fun updateDraft(uid: Uuid, notes: List<JournalNote>): Uuid {
        val now = Clock.System.now()
        val existing = drafts.value.firstOrNull { it.id == uid }
        val updated = if (existing != null) {
            existing.copy(notes = notes, updatedAt = now)
        } else {
            EntryDraft(id = uid, notes = notes, createdAt = now, updatedAt = now)
        }
        drafts.value = drafts.value.filterNot { it.id == uid } + updated
        return uid
    }

    override suspend fun setPendingMedia(
        uid: Uuid,
        pendingMedia: List<app.logdate.client.repository.journals.PendingMediaRecord>,
    ) = Unit

    override suspend fun deleteDraft(uid: Uuid) {
        drafts.value = drafts.value.filterNot { it.id == uid }
    }

    override suspend fun deleteAllDrafts() {
        drafts.value = emptyList()
    }

    override suspend fun deleteExpiredDrafts(maxAge: Duration): Int = 0
}

private class DraftsKoinModuleOverrideRule(
    private val module: Module,
) : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                if (GlobalContext.getOrNull() == null) {
                    startKoin {
                        androidContext(context)
                        modules(appModule)
                    }
                }
                loadKoinModules(module)
                base.evaluate()
            }
        }
}
