package app.logdate.feature.journals.navigation

import androidx.navigation3.runtime.NavKey
import app.logdate.client.repository.journals.JournalMergeOperation
import app.logdate.client.repository.journals.JournalMergeScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class JournalMergeNavigationTest {
    @Test
    fun `completion preserves unrelated stack removes obsolete source routes and opens destination`() {
        val source = Uuid.random()
        val destination = Uuid.random()
        val unrelated = Uuid.random()
        val operation =
            JournalMergeOperation(
                Uuid.random(),
                source,
                destination,
                emptySet(),
                JournalMergeScope("owner", "server"),
                "Travel",
                "Archive",
            )
        val stack =
            mutableListOf<NavKey>(
                JournalsOverviewRoute,
                JournalDetailsRoute(unrelated),
                JournalDetailsRoute(source),
                JournalSettingsRoute(source),
                ShareJournalRoute(source),
                JournalContentPickerRoute(source),
                JournalMergeRoute(source.toString()),
            )
        stack.completeJournalMerge(operation)
        assertEquals(
            listOf(
                JournalsOverviewRoute,
                JournalDetailsRoute(unrelated),
                JournalDetailsRoute(destination.toString(), mergedSourceTitle = "Travel"),
            ),
            stack,
        )
    }
}
