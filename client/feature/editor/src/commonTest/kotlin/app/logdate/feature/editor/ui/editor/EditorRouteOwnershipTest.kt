package app.logdate.feature.editor.ui.editor

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import app.logdate.feature.editor.navigation.EntryEditorRoute
import app.logdate.feature.editor.navigation.editorEntry
import app.logdate.ui.navigation.hasViewModelsPerVisit
import kotlin.test.Test
import kotlin.test.assertTrue

class EditorRouteOwnershipTest {
    @Test
    fun `each editor visit owns its view models so exit state and recorder collectors cannot leak into Add entry`() {
        val provider = entryProvider<NavKey> { editorEntry(onNavigateBack = {}, onEntrySaved = {}) }
        assertTrue(provider(EntryEditorRoute()).hasViewModelsPerVisit())
    }
}
