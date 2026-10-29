package app.logdate.feature.editor.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class EntryCreationRouteIdentityTest {
    @Test
    fun unsourcedEditorVisitsDoNotClaimAHomeSurface() {
        assertNull(EntryEditorRoute().creationTransitionId)
        assertNull(EntryEditorRoute(draftId = "draft").creationTransitionId)
        assertNull(EntryEditorRoute(entryId = "entry").creationTransitionId)
        assertNull(EntryEditorRoute(launchMode = EditorLaunchMode.Voice).creationTransitionId)
    }

    @Test
    fun separateHomeVisitsRemainDistinctWithoutChangingTheirCaptureIntent() {
        val first = EntryEditorRoute(creationTransitionId = "first")
        val second = EntryEditorRoute(creationTransitionId = "second")
        assertNotEquals(first, second)
        assertEquals(EditorLaunchMode.Default, first.launchMode)
        assertEquals("first", first.copy(journalIds = listOf("journal")).creationTransitionId)
    }
}
