package app.logdate.client.data.restore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class RetainedJournalMembershipPolicyTest {
    @Test
    fun `legacy relation cannot override canonical deleted relation`() {
        val journal = Uuid.parse("10000000-0000-0000-0000-000000000001")
        val note = Uuid.parse("20000000-0000-0000-0000-000000000001")

        assertEquals(
            emptySet(),
            retainedJournalMemberships(
                canonical = listOf(RetainedJournalMembership(journal, note, deleted = true)),
                legacy = listOf(RetainedJournalMembership(journal, note, deleted = false)),
            ),
        )
    }

    @Test
    fun `legacy relations fill only pairs absent from canonical table`() {
        val journal = Uuid.parse("10000000-0000-0000-0000-000000000001")
        val deleted = Uuid.parse("20000000-0000-0000-0000-000000000001")
        val absent = Uuid.parse("20000000-0000-0000-0000-000000000002")

        assertEquals(
            setOf(journal to absent),
            retainedJournalMemberships(
                canonical = listOf(RetainedJournalMembership(journal, deleted, deleted = true)),
                legacy =
                    listOf(
                        RetainedJournalMembership(journal, deleted, deleted = false),
                        RetainedJournalMembership(journal, absent, deleted = false),
                    ),
            ),
        )
    }
}
