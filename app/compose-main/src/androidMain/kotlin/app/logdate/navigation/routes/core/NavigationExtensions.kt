package app.logdate.navigation.routes.core

import app.logdate.navigation.scenes.HomeTab
import app.logdate.navigation.MainAppNavigator
import kotlin.uuid.Uuid

/**
 * Common navigation extensions that provide safe and consistent navigation across the app.
 */

/**
 * Handles back navigation by safely removing the last entry from the backstack.
 * If removing the last entry would result in an empty backstack, we navigate to a home tab.
 */
fun MainAppNavigator.goBack() {
    // If we have more than one entry, we can safely remove the last one
    if (backStack.size > 1) {
        safelyRemoveLastEntry()
    } else {
        // Otherwise, find a suitable home tab to navigate to
        val mainTabs = HomeTab.entries.map { it.route }
        val existingMainTab = backStack.firstOrNull { it in mainTabs }
        
        // Determine which route to use as our fallback
        val homeRoute = existingMainTab ?: HomeTab.TIMELINE.route
        
        // Make sure it's in the backstack
        if (!backStack.contains(homeRoute)) {
            backStack.add(homeRoute)
        }
        
        // Make sure it's the only entry
        safelyPopBackstackTo(homeRoute, keepFirst = true)
    }
}

/**
 * Opens the entry editor, optionally with an existing entry to edit.
 * 
 * @param entryId The ID of an existing entry to edit, or null to create a new entry
 */
fun MainAppNavigator.openEntryEditor(
    entryId: Uuid? = null,
) {
    backStack.add(
        EntryEditor(id = entryId)
    )
}

/**
 * Navigates to the home screen from the onboarding flow.
 * This clears the backstack to prevent navigation back to onboarding.
 */
fun MainAppNavigator.navigateHomeFromOnboarding() {
    // Make sure the home route is in the backstack
    if (!backStack.contains(TimelineListRoute)) {
        backStack.add(TimelineListRoute)
    }
    
    // Navigate to home route, keeping it as the first (and only) entry
    safelyPopBackstackTo(TimelineListRoute, keepFirst = true)
}

/**
 * Navigates to the home screen from the launch state.
 */
fun MainAppNavigator.navigateHomeFromLaunch() {
    // Use the same safe implementation as navigateHomeFromOnboarding
    navigateHomeFromOnboarding()
}

/**
 * Switch to a specific tab in the HomeScene
 * 
 * According to Material Design guidelines, top-level destinations should replace each other
 * rather than creating a back stack between them. This implementation ensures that
 * navigating between main tabs doesn't add entries to the back stack.
 */
fun MainAppNavigator.switchToTab(tab: HomeTab) {
    // Check if we already have this tab in backstack
    val existingTabIndex = backStack.indexOfFirst { it == tab.route }
    
    if (existingTabIndex >= 0) {
        // If we have this tab but it's not the last item, use safelyPopBackstackTo
        // to navigate to it (removing everything after it)
        if (existingTabIndex < backStack.size - 1) {
            safelyPopBackstackTo(tab.route)
        }
        // If it's already the last item, nothing to do
    } else {
        // Get the current last entry to check if it's a main tab
        val currentEntry = backStack.lastOrNull()
        val isCurrentEntryMainTab = currentEntry != null && HomeTab.entries.any { it.route == currentEntry }
        
        // Get other main tabs in the backstack (excluding the current one if it's a main tab)
        val otherMainTabsIndices = if (isCurrentEntryMainTab) {
            backStack.mapIndexedNotNull { index, entry -> 
                if (entry != currentEntry && HomeTab.entries.any { it.route == entry }) index else null
            }
        } else {
            backStack.mapIndexedNotNull { index, entry -> 
                if (HomeTab.entries.any { it.route == entry }) index else null
            }
        }
        
        // If the current entry is a main tab, replace it instead of adding a new entry
        if (isCurrentEntryMainTab && backStack.size > 0) {
            // Replace the last entry with the new tab
            backStack.removeLastOrNull()
            backStack.add(tab.route)
            
            // Also remove any other main tabs from the backstack (we should only have one)
            // Starting from the end to avoid index shifting
            otherMainTabsIndices.sortedDescending().forEach { index ->
                if (index < backStack.size) { // Safety check
                    backStack.removeAt(index)
                }
            }
        } else {
            // Remove any existing main tabs from the backstack
            // Starting from the end to avoid index shifting
            otherMainTabsIndices.sortedDescending().forEach { index ->
                if (index < backStack.size) { // Safety check
                    backStack.removeAt(index)
                }
            }
            
            // Then add the new tab
            backStack.add(tab.route)
        }
    }
}