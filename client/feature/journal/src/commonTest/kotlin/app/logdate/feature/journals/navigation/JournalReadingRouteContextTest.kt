package app.logdate.feature.journals.navigation

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import app.logdate.ui.navigation.ActivityPresentation
import app.logdate.ui.navigation.activityPresentation
import app.logdate.ui.navigation.hasViewModelsPerVisit
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class JournalReadingRouteContextTest {
    private val provider =
        entryProvider<NavKey> {
            journalDetailsEntry(onBack = {}, onJournalDeleted = {}, onOpenNote = { _, _ -> })
            noteDetailEntry(onBack = {})
        }

    @Test
    fun selectedNoteSharesTheSpecificJournalContext() {
        val journal = Uuid.random().toString()
        val source = provider(JournalDetailsRoute(journal)).activityPresentation() as ActivityPresentation.Detail
        val note = provider(NoteDetailRoute(Uuid.random().toString(), journal)).activityPresentation() as ActivityPresentation.Detail
        assertEquals("journal:$journal", source.childContext)
        assertEquals(source.childContext, note.context)
    }

    @Test
    fun anotherJournalsNoteDoesNotInheritVisibleJournalContext() {
        val source = provider(JournalDetailsRoute(Uuid.random())).activityPresentation() as ActivityPresentation.Detail
        val note =
            provider(
                NoteDetailRoute(Uuid.random().toString(), Uuid.random().toString()),
            ).activityPresentation() as ActivityPresentation.Detail
        assertNotEquals(source.childContext, note.context)
    }

    @Test
    fun externalNoteDeepLinkIsACompleteReplacementActivity() {
        assertEquals(ActivityPresentation.Replacement, provider(NoteDetailRoute(Uuid.random())).activityPresentation())
    }

    @Test
    fun noteViewModelsBelongToTheirVisitedNoteRatherThanTheWholeApplication() {
        assertTrue(provider(NoteDetailRoute(Uuid.random())).hasViewModelsPerVisit())
    }

    @Test
    fun previouslySavedNoteRoutesRestoreWithoutInventingJournalContext() {
        val route = Json.decodeFromString<NoteDetailRoute>("{\"noteId\":\"00000000-0000-0000-0000-000000000001\"}")
        assertEquals(null, route.journalId)
    }
}
