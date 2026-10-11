package app.logdate.feature.core.settings.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsActivityNavigationTest {
    @Test
    fun categoryChangeReplacesNestedSectionAndPreservesOrigin() {
        val origin = Origin
        val root = SettingsRoute()
        val stack = NavBackStack<NavKey>(origin, root, AccountSettingsRoute, HostingRoute, MoveServerRoute)

        stack.navigateToSettingsCategory(PrivacySettingsRoute)

        assertEquals(listOf(origin, root, PrivacySettingsRoute), stack.toList())
    }

    @Test
    fun repeatedCategoryDoesNotDuplicateEntryOrDiscardNestedHistory() {
        val stack = NavBackStack<NavKey>(SettingsRoute(), AccountSettingsRoute, HostingRoute)
        val before = stack.toList()

        stack.navigateToSettingsCategory(AccountSettingsRoute)

        assertEquals(before, stack.toList())
    }

    @Test
    fun directLinkedSectionChangesWithoutSyntheticSettingsRoot() {
        val stack = NavBackStack<NavKey>(Origin, RecoveryPhraseRoute)

        stack.navigateToSettingsCategory(TimelineSettingsRoute)

        assertEquals(listOf(Origin, TimelineSettingsRoute), stack.toList())
    }

    @Test
    fun compactBackReturnsToCategoriesThenOrigin() {
        val root = SettingsRoute()
        val stack = NavBackStack<NavKey>(Origin, root)
        stack.navigateToSettingsCategory(LocationSettingsRoute)

        stack.removeAt(stack.lastIndex)
        assertEquals(listOf(Origin, root), stack.toList())
        stack.removeAt(stack.lastIndex)
        assertEquals(listOf(Origin), stack.toList())
    }

    @Test
    fun labsRetainsAboutContextWhenChangingCategories() {
        assertEquals(SettingsCategory.About, LabsSettingsRoute.settingsCategory())
        val root = SettingsRoute()
        val stack = NavBackStack<NavKey>(Origin, root, AdvancedSettingsRoute, LabsSettingsRoute)
        stack.navigateToSettingsCategory(PrivacySettingsRoute)
        assertEquals(listOf(Origin, root, PrivacySettingsRoute), stack.toList())
    }

    private data object Origin : NavKey
}
