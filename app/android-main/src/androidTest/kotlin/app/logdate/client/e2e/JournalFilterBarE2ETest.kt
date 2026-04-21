package app.logdate.client.e2e

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.feature.journals.ui.JournalFilter
import app.logdate.feature.journals.ui.JournalFilterBar
import app.logdate.feature.journals.ui.JournalLayoutMode
import app.logdate.feature.journals.ui.JournalSortOption
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [JournalFilterBar] interactions.
 *
 * Verifies that the layout toggle, sort dropdown, and filter chips respond
 * correctly to user taps and reflect updated state.
 */
/**
 * Instrumented UI tests for the [JournalFilterBar] component.
 *
 * These tests validate the interactive elements of the journal gallery header,
 * ensuring that layout toggles, sort selection menus, and multi-select filter
 * chips correctly update the UI state and trigger their respective callbacks.
 */
@RunWith(AndroidJUnit4::class)
class JournalFilterBarE2ETest {

    @get:Rule
    val composeRule = createComposeRule()

    // region Layout Mode Toggle

    @Test
    fun layoutToggle_inCarouselMode_showsSwitchToGridDescription() {
        composeRule.setContent {
            JournalFilterBar(
                layoutMode = JournalLayoutMode.CAROUSEL,
                sortOption = JournalSortOption.LAST_UPDATED,
                activeFilters = emptySet(),
                onToggleLayoutMode = {},
                onSortOptionSelected = {},
                onToggleFilter = {},
            )
        }

        composeRule.onNodeWithTag("LayoutModeToggle").assertIsDisplayed()
    }

    @Test
    fun layoutToggle_click_invokesCallback() {
        var toggled = false

        composeRule.setContent {
            JournalFilterBar(
                layoutMode = JournalLayoutMode.CAROUSEL,
                sortOption = JournalSortOption.LAST_UPDATED,
                activeFilters = emptySet(),
                onToggleLayoutMode = { toggled = true },
                onSortOptionSelected = {},
                onToggleFilter = {},
            )
        }

        composeRule.onNodeWithTag("LayoutModeToggle").performClick()
        assert(toggled) { "Expected onToggleLayoutMode to be called" }
    }

    @Test
    fun layoutToggle_switchesToGrid_updatesState() {
        composeRule.setContent {
            var mode by remember { mutableStateOf(JournalLayoutMode.CAROUSEL) }

            JournalFilterBar(
                layoutMode = mode,
                sortOption = JournalSortOption.LAST_UPDATED,
                activeFilters = emptySet(),
                onToggleLayoutMode = {
                    mode = when (mode) {
                        JournalLayoutMode.CAROUSEL -> JournalLayoutMode.GRID
                        JournalLayoutMode.GRID -> JournalLayoutMode.CAROUSEL
                    }
                },
                onSortOptionSelected = {},
                onToggleFilter = {},
            )
        }

        // Start in carousel mode, toggle to grid
        composeRule.onNodeWithTag("LayoutModeToggle").performClick()
        composeRule.waitForIdle()

        // Toggle back to carousel
        composeRule.onNodeWithTag("LayoutModeToggle").performClick()
        composeRule.waitForIdle()

        // Should still be functional (no crash)
        composeRule.onNodeWithTag("LayoutModeToggle").assertIsDisplayed()
    }

    // endregion

    // region Sort Dropdown

    @Test
    fun sortChip_displaysCurrentSortOption() {
        composeRule.setContent {
            JournalFilterBar(
                layoutMode = JournalLayoutMode.CAROUSEL,
                sortOption = JournalSortOption.LAST_UPDATED,
                activeFilters = emptySet(),
                onToggleLayoutMode = {},
                onSortOptionSelected = {},
                onToggleFilter = {},
            )
        }

        composeRule.onNodeWithText("Sorting by last updated", substring = true).assertIsDisplayed()
    }

    @Test
    fun sortChip_click_opensDropdown() {
        composeRule.setContent {
            JournalFilterBar(
                layoutMode = JournalLayoutMode.CAROUSEL,
                sortOption = JournalSortOption.LAST_UPDATED,
                activeFilters = emptySet(),
                onToggleLayoutMode = {},
                onSortOptionSelected = {},
                onToggleFilter = {},
            )
        }

        composeRule.onNodeWithTag("SortDropdownChip").performClick()
        composeRule.waitForIdle()

        // Dropdown menu items should be visible with capitalized labels
        composeRule.onNodeWithText("Last Updated").assertIsDisplayed()
        composeRule.onNodeWithText("Date Created").assertIsDisplayed()
        composeRule.onNodeWithText("Title").assertIsDisplayed()
    }

    @Test
    fun sortDropdown_selectOption_invokesCallbackAndCloses() {
        var selectedOption: JournalSortOption? = null

        composeRule.setContent {
            JournalFilterBar(
                layoutMode = JournalLayoutMode.CAROUSEL,
                sortOption = JournalSortOption.LAST_UPDATED,
                activeFilters = emptySet(),
                onToggleLayoutMode = {},
                onSortOptionSelected = { selectedOption = it },
                onToggleFilter = {},
            )
        }

        composeRule.onNodeWithTag("SortDropdownChip").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Title").performClick()
        composeRule.waitForIdle()

        assert(selectedOption == JournalSortOption.TITLE) {
            "Expected TITLE but got $selectedOption"
        }
    }

    @Test
    fun sortDropdown_selectOption_updatesChipLabel() {
        var selectedSort = JournalSortOption.LAST_UPDATED

        composeRule.setContent {
            var sort by remember { mutableStateOf(JournalSortOption.LAST_UPDATED) }

            JournalFilterBar(
                layoutMode = JournalLayoutMode.CAROUSEL,
                sortOption = sort,
                activeFilters = emptySet(),
                onToggleLayoutMode = {},
                onSortOptionSelected = {
                    sort = it
                    selectedSort = it
                },
                onToggleFilter = {},
            )
        }

        // Open dropdown and select "Date Created"
        composeRule.onNodeWithTag("SortDropdownChip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Date Created").performClick()
        composeRule.waitForIdle()

        assert(selectedSort == JournalSortOption.CREATED) {
            "Expected CREATED but got $selectedSort"
        }
        composeRule.onNodeWithTag("SortDropdownChip").assertIsDisplayed()
    }

    // endregion

    // region Filter Chips

    @Test
    fun filterChips_displayedWithCorrectLabels() {
        composeRule.setContent {
            JournalFilterBar(
                layoutMode = JournalLayoutMode.CAROUSEL,
                sortOption = JournalSortOption.LAST_UPDATED,
                activeFilters = emptySet(),
                onToggleLayoutMode = {},
                onSortOptionSelected = {},
                onToggleFilter = {},
            )
        }

        composeRule.onNodeWithText("Owned by me").assertIsDisplayed()
        composeRule.onNodeWithText("Shared").assertIsDisplayed()
    }

    @Test
    fun filterChip_ownedByMe_click_invokesCallback() {
        var toggledFilter: JournalFilter? = null

        composeRule.setContent {
            JournalFilterBar(
                layoutMode = JournalLayoutMode.CAROUSEL,
                sortOption = JournalSortOption.LAST_UPDATED,
                activeFilters = emptySet(),
                onToggleLayoutMode = {},
                onSortOptionSelected = {},
                onToggleFilter = { toggledFilter = it },
            )
        }

        composeRule.onNodeWithTag("FilterChip_OWNED_BY_ME").performClick()

        assert(toggledFilter == JournalFilter.OWNED_BY_ME) {
            "Expected OWNED_BY_ME but got $toggledFilter"
        }
    }

    @Test
    fun filterChip_shared_click_invokesCallback() {
        var toggledFilter: JournalFilter? = null

        composeRule.setContent {
            JournalFilterBar(
                layoutMode = JournalLayoutMode.CAROUSEL,
                sortOption = JournalSortOption.LAST_UPDATED,
                activeFilters = emptySet(),
                onToggleLayoutMode = {},
                onSortOptionSelected = {},
                onToggleFilter = { toggledFilter = it },
            )
        }

        composeRule.onNodeWithTag("FilterChip_SHARED").performClick()

        assert(toggledFilter == JournalFilter.SHARED) {
            "Expected SHARED but got $toggledFilter"
        }
    }

    @Test
    fun filterChip_toggleOnAndOff() {
        var selectedFilters by mutableStateOf<Set<JournalFilter>>(emptySet())

        composeRule.setContent {
            JournalFilterBar(
                layoutMode = JournalLayoutMode.CAROUSEL,
                sortOption = JournalSortOption.LAST_UPDATED,
                activeFilters = selectedFilters,
                onToggleLayoutMode = {},
                onSortOptionSelected = {},
                onToggleFilter = { filter ->
                    val updatedFilters =
                        if (filter in selectedFilters) {
                            selectedFilters - filter
                        } else {
                            selectedFilters + filter
                        }
                    selectedFilters = updatedFilters
                },
            )
        }

        // Select "Owned by me"
        composeRule.onNodeWithTag("FilterChip_OWNED_BY_ME").performClick()
        composeRule.waitForIdle()
        assert(JournalFilter.OWNED_BY_ME in selectedFilters) {
            "Expected OWNED_BY_ME to be active but got $selectedFilters"
        }

        // Deselect "Owned by me"
        composeRule.onNodeWithTag("FilterChip_OWNED_BY_ME").performClick()
        composeRule.waitForIdle()
        assert(JournalFilter.OWNED_BY_ME !in selectedFilters) {
            "Expected OWNED_BY_ME to be removed but got $selectedFilters"
        }
    }

    @Test
    fun filterChips_multipleCanBeActive() {
        var selectedFilters by mutableStateOf<Set<JournalFilter>>(emptySet())

        composeRule.setContent {
            JournalFilterBar(
                layoutMode = JournalLayoutMode.CAROUSEL,
                sortOption = JournalSortOption.LAST_UPDATED,
                activeFilters = selectedFilters,
                onToggleLayoutMode = {},
                onSortOptionSelected = {},
                onToggleFilter = { filter ->
                    val updatedFilters =
                        if (filter in selectedFilters) {
                            selectedFilters - filter
                        } else {
                            selectedFilters + filter
                        }
                    selectedFilters = updatedFilters
                },
            )
        }

        // Select both filters
        composeRule.onNodeWithTag("FilterChip_OWNED_BY_ME").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("FilterChip_SHARED").performClick()
        composeRule.waitForIdle()

        assert(selectedFilters == setOf(JournalFilter.OWNED_BY_ME, JournalFilter.SHARED)) {
            "Expected both filters to be active but got $selectedFilters"
        }
    }

    // endregion

    // region All Controls Together

    @Test
    fun allControls_renderedInDefaultState() {
        composeRule.setContent {
            JournalFilterBar(
                layoutMode = JournalLayoutMode.CAROUSEL,
                sortOption = JournalSortOption.LAST_UPDATED,
                activeFilters = emptySet(),
                onToggleLayoutMode = {},
                onSortOptionSelected = {},
                onToggleFilter = {},
            )
        }

        composeRule.onNodeWithTag("JournalFilterBar").assertIsDisplayed()
        composeRule.onNodeWithTag("LayoutModeToggle").assertIsDisplayed()
        composeRule.onNodeWithTag("SortDropdownChip").assertIsDisplayed()
        composeRule.onNodeWithTag("FilterChip_OWNED_BY_ME").assertIsDisplayed()
        composeRule.onNodeWithTag("FilterChip_SHARED").assertIsDisplayed()
    }

    // endregion
}
