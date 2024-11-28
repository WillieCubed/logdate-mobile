package app.logdate.feature.rewind.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * A utility function for remembering a [RewindNavigationState].
 *
 * This should be used to navigate between panels in a Rewind.
 *
 * @param totalPanels The total number of panels in the Rewind.
 * @param startingPanel The index of the panel to start at.
 *
 * @return A [RewindNavigationState] that manages the navigation state of a Rewind.
 *
 * @see RewindNavigationState
 */
@Composable
internal fun rememberRewindNavigationState(
    startingPanel: Int = 0,
): RewindNavigationState {
    return remember { RewindNavigationState(startingPanel) }
}

/**
 * A class that manages the navigation state of a Rewind.
 *
 * Consumers should set the total number of panels in the Rewind using [setPanelCount]. For
 * example:
 *
 * ```kotlin
 * @Composable
 * fun RewindLayout(totalPanels: Int) {
 *     val navigationState = rememberRewindNavigationState()
 *     LaunchedEffect(totalPanels) {
 *         navigationState.setTotalPanels(totalPanels)
 *     }
 *     // ...
 * }
 * ```
 *
 * @see rememberRewindNavigationState
 *
 * @param initialPanelCount The total number of panels in the Rewind. Defaults to 0.
 * @param startingPanel The index of the panel to start at. Defaults to 0.
 */
@Stable
class RewindNavigationState(
    initialPanelCount: Int = 0,
    startingPanel: Int = 0,
) {
    /**
     * Returns true if a Rewind can navigate back.
     */
    val canGoBack: Boolean
        get() = currentPanelIndex > 0

    /**
     * Returns true if a Rewind can navigate forward.
     */
    val canGoForward: Boolean
        get() = currentPanelIndex < totalPanels - 1

    /**
     * Returns true if the current panel is the first panel.
     */
    val isFirstPanel: Boolean
        get() = currentPanelIndex == 0

    /**
     * Returns true if the current panel is the last panel.
     */
    val isLastPanel: Boolean
        get() = currentPanelIndex == totalPanels - 1

    private var totalPanels by mutableIntStateOf(initialPanelCount)

    /**
     * The current panel index.
     */
    private var currentPanelIndex by mutableIntStateOf(startingPanel)

    /**
     * Sets the total number of panels in the Rewind.
     */
    fun setPanelCount(totalPanels: Int) {
        this.totalPanels = totalPanels
    }

    /**
     * Navigates to a specific panel.
     *
     * @param index The index of the panel to navigate to.
     * @throws IllegalArgumentException if the index is out of bounds.
     */
    fun navigateToPanel(index: Int) {
        if (index in 0 until totalPanels) {
            currentPanelIndex = index
        } else {
            throw IllegalArgumentException("Panel index out of bounds: $index")
        }
    }

    /**
     * Navigates forward in the Rewind.
     *
     * If the Rewind is already at the last panel, this method does nothing.
     */
    fun navigateForward() {
        if (canGoForward) {
            currentPanelIndex++
        }
    }

    /**
     * Navigates back in the Rewind.
     *
     * If the Rewind is already at the first panel, this method does nothing.
     */
    fun navigateBack() {
        if (canGoBack) {
            currentPanelIndex--
        }
    }

    /**
     * Resets the navigation state to the first panel.
     */
    fun reset() {
        currentPanelIndex = 0
    }
}