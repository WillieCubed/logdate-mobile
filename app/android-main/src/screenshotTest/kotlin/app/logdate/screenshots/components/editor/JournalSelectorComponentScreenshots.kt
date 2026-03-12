package app.logdate.screenshots.components.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.common.JournalSelectorDropdown
import app.logdate.shared.model.Journal
import app.logdate.screenshots.common.ScreenshotTestData
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTestData.PHONE_LANDSCAPE
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Instant
import kotlin.uuid.Uuid

// ---------------------------------------------------------------------------
// Collapsed states — verifies colour, text, badge, and compact sizing
// ---------------------------------------------------------------------------

/** Selector in its default state: nothing assigned yet. */
@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun JournalSelector_Collapsed_NoSelection() {
    SelectorFrame {
        JournalSelectorDropdown(
            availableJournals = ScreenshotTestData.sampleJournals,
            selectedJournalIds = emptyList(),
            onSelectionChanged = {},
        )
    }
}

/** Selector showing a single assigned journal by title. */
@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun JournalSelector_Collapsed_OneSelected() {
    SelectorFrame {
        JournalSelectorDropdown(
            availableJournals = ScreenshotTestData.sampleJournals,
            selectedJournalIds = listOf(ScreenshotTestData.sampleJournal.id),
            onSelectionChanged = {},
        )
    }
}

/** Selector showing the multi-selection label and count badge. */
@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun JournalSelector_Collapsed_MultipleSelected() {
    SelectorFrame {
        JournalSelectorDropdown(
            availableJournals = ScreenshotTestData.sampleJournals,
            selectedJournalIds = listOf(
                ScreenshotTestData.sampleJournal.id,
                ScreenshotTestData.sampleJournal2.id,
            ),
            onSelectionChanged = {},
        )
    }
}

/**
 * Compact (landscape phone) variant of the collapsed selector: reduced icon and padding.
 * Verifies that [LocalEditorIsCompact] sizing applies correctly.
 */
@PreviewTest
@Preview(showBackground = true, device = PHONE_LANDSCAPE)
@Composable
fun JournalSelector_Compact_Collapsed() {
    SelectorFrame {
        JournalSelectorDropdown(
            availableJournals = ScreenshotTestData.sampleJournals,
            selectedJournalIds = listOf(ScreenshotTestData.sampleJournal.id),
            onSelectionChanged = {},
        )
    }
}

// ---------------------------------------------------------------------------
// Expanded states — verifies overlay behaviour, list rendering, and scrolling
// ---------------------------------------------------------------------------

/**
 * Picker open with a normal list of journals and one pre-selected.
 *
 * The expanded card should float above the editor content surface without
 * pushing it upward (no reflow). The header label and journal rows are visible.
 */
@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun JournalSelector_Expanded_WithSelection() {
    SelectorFrame {
        JournalSelectorDropdown(
            availableJournals = ScreenshotTestData.sampleJournals,
            selectedJournalIds = listOf(ScreenshotTestData.sampleJournal.id),
            onSelectionChanged = {},
            initialExpanded = true,
        )
    }
}

/**
 * Picker open when the user has no journals.
 *
 * The empty-state row ("No journals available") should be shown instead of the list.
 */
@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun JournalSelector_Expanded_EmptyState() {
    SelectorFrame {
        JournalSelectorDropdown(
            availableJournals = emptyList(),
            selectedJournalIds = emptyList(),
            onSelectionChanged = {},
            initialExpanded = true,
        )
    }
}

/**
 * Picker open with more journals than fit in the max height (~4 items).
 *
 * The list should be scrollable and the card must not exceed [journalSelectorExpandedHeight].
 * Journals beyond the visible area are accessible by scrolling — the cut-off at the bottom
 * indicates that more items exist.
 */
@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun JournalSelector_Expanded_Overflow() {
    SelectorFrame {
        JournalSelectorDropdown(
            availableJournals = manyJournals,
            selectedJournalIds = listOf(manyJournals.first().id),
            onSelectionChanged = {},
            initialExpanded = true,
        )
    }
}

/**
 * Compact (landscape) picker open with an overflowing list.
 *
 * The screen-height cap in [journalSelectorExpandedHeight] should reduce the max height
 * relative to the portrait case, keeping the list within the visible area.
 */
@PreviewTest
@Preview(showBackground = true, device = PHONE_LANDSCAPE)
@Composable
fun JournalSelector_Compact_Expanded_Overflow() {
    SelectorFrame {
        JournalSelectorDropdown(
            availableJournals = manyJournals,
            selectedJournalIds = listOf(manyJournals.first().id),
            onSelectionChanged = {},
            initialExpanded = true,
        )
    }
}

// ---------------------------------------------------------------------------
// Frame helper — places the selector at the bottom of a surface to make the
// overlay / no-reflow behaviour visible in screenshots
// ---------------------------------------------------------------------------

/**
 * Renders the journal selector inside a realistic editor-bottom context.
 *
 * A surface representing editor content sits above the selector; the expanded picker
 * should float over it without pushing it upward.
 */
@Composable
private fun SelectorFrame(content: @Composable () -> Unit) {
    ScreenshotTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            // Simulated editor content above the selector
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Today was a quiet one. Finished the last chapter of the book " +
                            "I've been carrying around for months.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Selector pinned to the bottom, matching how EditorBottomContent positions it
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                content()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Test data — extended journal list for overflow / scroll coverage
// ---------------------------------------------------------------------------

private val baseTime = Instant.fromEpochMilliseconds(1_740_000_000_000L)

/** Seven journals — enough to exceed the four-item max height and trigger scrolling. */
private val manyJournals = listOf(
    Journal(
        id = Uuid.parse("00000000-0000-0000-0000-000000000011"),
        title = "Daily Reflections",
        created = baseTime,
        lastUpdated = baseTime,
    ),
    Journal(
        id = Uuid.parse("00000000-0000-0000-0000-000000000012"),
        title = "Travel Log",
        created = baseTime,
        lastUpdated = baseTime,
    ),
    Journal(
        id = Uuid.parse("00000000-0000-0000-0000-000000000013"),
        title = "Recipe Notes",
        created = baseTime,
        lastUpdated = baseTime,
    ),
    Journal(
        id = Uuid.parse("00000000-0000-0000-0000-000000000014"),
        title = "Work Journal",
        created = baseTime,
        lastUpdated = baseTime,
    ),
    Journal(
        id = Uuid.parse("00000000-0000-0000-0000-000000000015"),
        title = "Book Notes",
        created = baseTime,
        lastUpdated = baseTime,
    ),
    Journal(
        id = Uuid.parse("00000000-0000-0000-0000-000000000016"),
        title = "Ideas & Concepts",
        created = baseTime,
        lastUpdated = baseTime,
    ),
    Journal(
        id = Uuid.parse("00000000-0000-0000-0000-000000000017"),
        title = "Fitness Tracker",
        created = baseTime,
        lastUpdated = baseTime,
    ),
)
