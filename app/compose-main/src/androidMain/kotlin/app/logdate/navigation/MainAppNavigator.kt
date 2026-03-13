package app.logdate.navigation

import androidx.navigation3.runtime.NavKey
import io.github.aakira.napier.Napier

/**
 * MainAppNavigator manages the app's navigation state and provides methods for
 * safely manipulating the back stack.
 *
 * This class handles:
 * 1. Maintaining the navigation back stack
 * 2. Providing safe navigation operations that prevent empty back stack states
 * 3. Supporting specialized navigation patterns for different app sections
 *
 * @property backStack The navigation back stack, typically provided by [rememberNavBackStack]
 *   so state survives configuration changes and process death.
 */
class MainAppNavigator(
    val backStack: MutableList<NavKey>,
) {
    /**
     * Safely removes the last entry from the back stack, ensuring we never have
     * an empty stack. If removing the last entry would result in an empty stack,
     * this method does nothing.
     *
     * @return true if the entry was removed, false otherwise
     */
    fun safelyRemoveLastEntry(): Boolean {
        // If we have more than one entry, we can safely remove the last one
        return if (backStack.size > 1) {
            val removedEntry = backStack.lastOrNull()
            backStack.removeLastOrNull()
            Napier.i("Navigation: Removed $removedEntry from backstack")
            true
        } else {
            // If we only have one entry, we can't remove it (would result in empty stack)
            Napier.i("Navigation: Cannot remove last entry from backstack - would result in empty stack")
            false
        }
    }

    /**
     * Safely clears the entire back stack and sets the provided entry as the new
     * root entry. This ensures we never have an empty back stack.
     *
     * @param rootEntry The entry to set as the new root of the back stack
     */
    fun safelyClearBackstack(rootEntry: NavKey) {
        Napier.i("Navigation: Clearing backstack and setting root to $rootEntry")
        // First add the new root entry to ensure we never have an empty stack
        backStack.add(rootEntry)

        // Then clear all other entries
        while (backStack.size > 1) {
            // Always remove the first entry (index 0) until only rootEntry remains
            val removedEntry = backStack[0]
            backStack.removeAt(0)
            Napier.d("Navigation: Removed $removedEntry during backstack clearing")
        }
    }

    /**
     * Safely pops the back stack to a specific entry, removing all entries after it.
     * If the specified entry is not found, this method does nothing.
     *
     * @param entry The entry to pop back to
     * @param keepFirst Whether to keep the entry as the first item in the backstack (default: false)
     * @return true if the operation succeeded, false otherwise
     */
    fun safelyPopBackstackTo(
        entry: NavKey,
        keepFirst: Boolean = false,
    ): Boolean {
        val index = backStack.indexOfFirst { it == entry }
        Napier.i("Navigation: Attempting to pop backstack to $entry (keepFirst=$keepFirst), found at index $index")

        // If the entry exists and it's not the only entry
        if (index >= 0 && backStack.size > 1) {
            if (keepFirst && index > 0) {
                // Move the entry to the front of the backstack
                val targetEntry = backStack[index]
                backStack.removeAt(index)
                Napier.d("Navigation: Removed $targetEntry from index $index to move to front")

                // Clear everything except the target entry
                backStack.add(0, targetEntry) // Add at front
                Napier.d("Navigation: Added $targetEntry to front of backstack")

                // Remove all other entries
                while (backStack.size > 1) {
                    val removedEntry = backStack[1]
                    backStack.removeAt(1)
                    Napier.d("Navigation: Removed $removedEntry to keep only target entry")
                }
            } else {
                // Standard behavior: remove all entries after the specified entry
                while (backStack.size > index + 1) {
                    val removedEntry = backStack.lastOrNull()
                    backStack.removeLastOrNull()
                    Napier.d("Navigation: Removed $removedEntry when popping back to target")
                }
            }
            Napier.i("Navigation: Successfully popped backstack to $entry")
            return true
        }

        Napier.w("Navigation: Failed to pop backstack to $entry - entry not found or is the only entry")
        return false
    }
}
