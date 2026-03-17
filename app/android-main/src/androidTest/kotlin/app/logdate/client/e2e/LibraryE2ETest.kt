package app.logdate.client.e2e

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.media.display.RemoteDisplayManager
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.media.IndexedMediaRepository
import app.logdate.di.appModule
import app.logdate.feature.library.ui.LibraryContent
import app.logdate.feature.library.ui.LibraryUiState
import app.logdate.feature.library.ui.detail.MediaDetailContent
import app.logdate.feature.library.ui.detail.MediaDetailUiState
import app.logdate.feature.library.ui.detail.PresenterState
import app.logdate.feature.library.ui.detail.PresenterMediaItem
import app.logdate.screenshots.components.library.LibraryScreenshotData
import app.logdate.ui.theme.LogDateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

/**
 * End-to-end tests for the Library feature.
 *
 * Tests cover grid browsing, detail view, presenter mode, and adaptive layout.
 * Uses stateless content composables with canned data for isolation.
 *
 * Run with:
 * ```
 * ./gradlew :app:android-main:connectedAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.client.e2e.LibraryE2ETest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class LibraryE2ETest {
    @get:Rule
    val composeRule = createComposeRule()

    // ── Grid browsing ────────────────────────────────────────────────────────

    @Test
    fun emptyStateShowsPrompt() {
        composeRule.setContent {
            LogDateTheme {
                LibraryContent(
                    state = LibraryUiState.Empty,
                    columnCount = 3,
                    onItemClick = {},
                )
            }
        }
        composeRule.onNodeWithText("Your memories live here").assertIsDisplayed()
    }

    @Test
    fun gridShowsMonthHeaders() {
        composeRule.setContent {
            LogDateTheme {
                LibraryContent(
                    state = LibraryScreenshotData.gridContent,
                    columnCount = 3,
                    onItemClick = {},
                )
            }
        }
        composeRule.onNodeWithText("March 2026").assertIsDisplayed()
        composeRule.onNodeWithText("February 2026").assertIsDisplayed()
    }

    @Test
    fun videoBadgeShowsOnVideoItems() {
        composeRule.setContent {
            LogDateTheme {
                LibraryContent(
                    state = LibraryScreenshotData.gridContent,
                    columnCount = 3,
                    onItemClick = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Video").assertIsDisplayed()
    }

    // ── Detail view ──────────────────────────────────────────────────────────

    @Test
    fun detailShowsMetadata() {
        composeRule.setContent {
            LogDateTheme {
                MediaDetailContent(
                    state = LibraryScreenshotData.imageDetail,
                    isExpanded = false,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Details").assertIsDisplayed()
    }

    @Test
    fun detailShowsExifWhenAvailable() {
        composeRule.setContent {
            LogDateTheme {
                MediaDetailContent(
                    state = LibraryScreenshotData.imageDetail,
                    isExpanded = true,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Camera").assertIsDisplayed()
        composeRule.onNodeWithText("Google Pixel 9 Pro", substring = true).assertIsDisplayed()
    }

    @Test
    fun detailShowsLocationName() {
        composeRule.setContent {
            LogDateTheme {
                MediaDetailContent(
                    state = LibraryScreenshotData.imageDetail,
                    isExpanded = true,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("San Francisco, CA").assertIsDisplayed()
    }

    @Test
    fun detailShowsAppearsInJournals() {
        composeRule.setContent {
            LogDateTheme {
                MediaDetailContent(
                    state = LibraryScreenshotData.imageDetail,
                    isExpanded = true,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Appears in").assertIsDisplayed()
        composeRule.onNodeWithText("Trip to California").assertIsDisplayed()
    }

    @Test
    fun shareButtonIsVisible() {
        composeRule.setContent {
            LogDateTheme {
                MediaDetailContent(
                    state = LibraryScreenshotData.imageDetail,
                    isExpanded = false,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Share").assertIsDisplayed()
    }

    // ── Presenter mode ───────────────────────────────────────────────────────

    @Test
    fun presentButtonHiddenWhenNoDisplay() {
        composeRule.setContent {
            LogDateTheme {
                MediaDetailContent(
                    state = LibraryScreenshotData.imageDetail,
                    presenterState = PresenterState(isExternalDisplayAvailable = false),
                    isExpanded = false,
                    onBack = {},
                )
            }
        }
        composeRule
            .onNodeWithContentDescription("Present")
            .assertDoesNotExist()
    }

    @Test
    fun presentButtonVisibleWhenDisplayConnected() {
        composeRule.setContent {
            LogDateTheme {
                MediaDetailContent(
                    state = LibraryScreenshotData.imageDetail,
                    presenterState = PresenterState(isExternalDisplayAvailable = true),
                    isExpanded = false,
                    onBack = {},
                )
            }
        }
        composeRule
            .onNodeWithContentDescription("Present")
            .assertIsDisplayed()
    }

    @Test
    fun presenterStripShowsCounter() {
        composeRule.setContent {
            LogDateTheme {
                MediaDetailContent(
                    state = LibraryScreenshotData.imageDetail,
                    presenterState = LibraryScreenshotData.presenterActive,
                    isExpanded = false,
                    onBack = {},
                )
            }
        }
        composeRule
            .onNodeWithText("Presenting", substring = true)
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("3 of 9", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun stopPresentingButtonVisible() {
        composeRule.setContent {
            LogDateTheme {
                MediaDetailContent(
                    state = LibraryScreenshotData.imageDetail,
                    presenterState = LibraryScreenshotData.presenterActive,
                    isExpanded = false,
                    onBack = {},
                )
            }
        }
        composeRule
            .onNodeWithText("Stop Presenting")
            .assertIsDisplayed()
    }

    @Test
    fun tapStopCallsCallback() {
        var stopped = false
        composeRule.setContent {
            LogDateTheme {
                MediaDetailContent(
                    state = LibraryScreenshotData.imageDetail,
                    presenterState = LibraryScreenshotData.presenterActive,
                    isExpanded = false,
                    onBack = {},
                    onStopPresenting = { stopped = true },
                )
            }
        }
        composeRule.onNodeWithText("Stop Presenting").performClick()
        assert(stopped) { "Expected onStopPresenting to be called" }
    }

    // ── Adaptive layout ──────────────────────────────────────────────────────

    @Test
    fun expandedLayoutShowsMetadataSideBySide() {
        composeRule.setContent {
            LogDateTheme {
                MediaDetailContent(
                    state = LibraryScreenshotData.imageDetail,
                    isExpanded = true,
                    onBack = {},
                )
            }
        }
        // In expanded, metadata panel is directly visible (not behind sheet)
        composeRule.onNodeWithText("Details").assertIsDisplayed()
        composeRule.onNodeWithText("Location").assertIsDisplayed()
    }

    @Test
    fun errorStateShowsMessage() {
        composeRule.setContent {
            LogDateTheme {
                MediaDetailContent(
                    state = MediaDetailUiState.Error("Something went wrong"),
                    isExpanded = false,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Something went wrong").assertIsDisplayed()
        composeRule.onNodeWithText("Go back").assertIsDisplayed()
    }
}
