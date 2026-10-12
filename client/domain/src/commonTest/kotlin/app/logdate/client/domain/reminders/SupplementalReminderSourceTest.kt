package app.logdate.client.domain.reminders

import app.logdate.client.domain.recommendation.AmbientPromptFamily
import app.logdate.client.domain.recommendation.AmbientPromptPayload
import app.logdate.client.domain.recommendation.DefaultAmbientPromptHistoryRepository
import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import app.logdate.client.domain.recommendation.MemoryRecallData
import app.logdate.client.repository.journals.EntryDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SupplementalReminderSourceTest {
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private var draft: EntryDraft? = null
    private var saved = false
    private var memory: MemoryRecallData? = null
    private var recallReads = 0
    private val history = DefaultAmbientPromptHistoryRepository(FakeKeyValueStorage()) { now }

    private fun source() =
        SupplementalReminderSource({ draft }, { saved }, {
            recallReads++
            memory
        }, history)

    private fun oldDraft() = EntryDraft(Uuid.random(), emptyList(), now - 7.hours, now - 6.hours)

    @Test fun oldDraftKeepsItsTargetAndTakesPriorityOverRecall() =
        runTest {
            draft = oldDraft()
            memory = MemoryRecallData(LocalDate(2025, 10, 12), "A day to revisit")
            val candidate = source().read(now)!!
            assertEquals(AmbientPromptFamily.DRAFT_RESCUE, candidate.family)
            assertEquals(draft!!.id, assertIs<AmbientPromptPayload.DraftRescue>(candidate.payload).draftId)
            assertEquals(0, recallReads)
        }

    @Test fun recentlyEditedDraftDoesNotInterrupt() =
        runTest {
            draft = oldDraft().copy(updatedAt = now - 1.hours)
            assertNull(source().read(now))
        }

    @Test fun futureDraftDoesNotInterrupt() =
        runTest {
            draft = oldDraft().copy(updatedAt = now + 1.hours)
            assertNull(source().read(now))
        }

    @Test fun aSavedEntrySinceTheDraftSuppressesRescue() =
        runTest {
            draft = oldDraft()
            saved = true
            assertNull(source().read(now))
        }

    @Test fun recallKeepsItsOriginalDateAndSummary() =
        runTest {
            memory = MemoryRecallData(LocalDate(2025, 10, 12), "A day to revisit")
            val candidate = source().read(now)!!
            assertEquals(AmbientPromptFamily.MEMORY_RECALL, candidate.family)
            assertEquals(AmbientPromptPayload.MemoryRecall(memory!!.date, memory!!.summary), candidate.payload)
        }

    @Test fun previouslyShownDraftIsNotRepeated() =
        runTest {
            draft = oldDraft()
            val source = source()
            history.recordShown(source.read(now)!!, now - 48.hours)
            assertNull(source.read(now))
        }

    @Test fun previouslyShownMemoryIsNotRepeated() =
        runTest {
            memory = MemoryRecallData(LocalDate(2025, 10, 12), "A day to revisit")
            val source = source()
            history.recordShown(source.read(now)!!, now - 48.hours)
            assertNull(source.read(now))
        }

    @Test fun cancellationPropagates() =
        runTest {
            val source = SupplementalReminderSource({ throw CancellationException("Cancelled") }, { false }, { null }, history)
            assertFailsWith<CancellationException> { source.read(now) }
        }
}
