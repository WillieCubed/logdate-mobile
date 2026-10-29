package app.logdate.ui.creation

import kotlin.test.Test
import kotlin.test.assertEquals

class EntryCreationShapeRegistryTest {
    @Test
    fun lateReleaseCannotRemoveTheReplacementSourceOwner() {
        val launcher = EntryCreationLauncher()
        val header = Any()
        val rail = Any()
        launcher.registerSourceShape("same-visit", header, EntryCreationShape.Round)
        assertEquals(EntryCreationShape.Round, launcher.sourceShape("same-visit", EntryCreationShape.Large))
        launcher.registerSourceShape("same-visit", rail, EntryCreationShape.Large)
        launcher.unregisterSourceShape("same-visit", header)
        assertEquals(EntryCreationShape.Large, launcher.sourceShape("same-visit", EntryCreationShape.Round))

        launcher.registerSourceShape("same-visit", rail, EntryCreationShape.Round)
        assertEquals(EntryCreationShape.Round, launcher.sourceShape("same-visit", EntryCreationShape.Large))
        launcher.unregisterSourceShape("same-visit", rail)
        assertEquals(EntryCreationShape.Large, launcher.sourceShape("same-visit", EntryCreationShape.Large))
    }

    @Test
    fun missingSourcesUseTheRouteFallbackAndWindowsAreIsolated() {
        val firstWindow = EntryCreationLauncher()
        val secondWindow = EntryCreationLauncher()
        val owner = Any()
        assertEquals(EntryCreationShape.Round, firstWindow.sourceShape("absent", EntryCreationShape.Round))
        assertEquals(EntryCreationShape.Large, firstWindow.sourceShape("absent", EntryCreationShape.Large))
        firstWindow.registerSourceShape("visit", owner, EntryCreationShape.Round)
        assertEquals(EntryCreationShape.Round, firstWindow.sourceShape("visit", EntryCreationShape.Large))
        assertEquals(EntryCreationShape.Large, secondWindow.sourceShape("visit", EntryCreationShape.Large))
        secondWindow.unregisterSourceShape("visit", owner)
        assertEquals(EntryCreationShape.Round, firstWindow.sourceShape("visit", EntryCreationShape.Large))
        assertEquals(EntryCreationShape.Large, firstWindow.sourceShape("another-visit", EntryCreationShape.Large))
    }
}
