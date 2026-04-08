package app.logdate.client.feature.widgets.shortcuts

import app.logdate.client.domain.rewind.RewindQueryResult
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.Rewind
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

class DynamicShortcutPublisherTest {
    @Test
    fun `returns only TodayTimeline when no draft and no rewind`() =
        runTest {
            val publisher = publisher(draft = null, rewind = null)

            val descriptors = publisher.computeShortcuts()

            assertEquals(1, descriptors.size)
            val today = assertIs<DynamicShortcutDescriptor.TodayTimeline>(descriptors[0])
            assertEquals(LocalDate(2026, 4, 7), today.date)
        }

    @Test
    fun `today's date is derived from injected clock and timezone`() =
        runTest {
            val publisher =
                publisher(
                    draft = null,
                    rewind = null,
                    clockInstant = Instant.parse("2026-04-07T23:30:00Z"),
                    timeZone = TimeZone.of("America/Los_Angeles"),
                )

            val descriptors = publisher.computeShortcuts()

            // 23:30 UTC on Apr 7 is 16:30 PDT on Apr 7 — same calendar day in LA.
            val today = assertIs<DynamicShortcutDescriptor.TodayTimeline>(descriptors.single())
            assertEquals(LocalDate(2026, 4, 7), today.date)
        }

    @Test
    fun `includes ContinueDraft when the most recent draft is fresh`() =
        runTest {
            val draftId = Uuid.random()
            val draft = textDraft(id = draftId, updatedAt = NOW - 2.hours)
            val publisher = publisher(draft = draft, rewind = null)

            val descriptors = publisher.computeShortcuts()

            assertEquals(2, descriptors.size)
            val continueDraft = assertIs<DynamicShortcutDescriptor.ContinueDraft>(descriptors[0])
            assertEquals(draftId, continueDraft.draftId)
            assertIs<DynamicShortcutDescriptor.TodayTimeline>(descriptors[1])
        }

    @Test
    fun `excludes drafts older than the freshness window`() =
        runTest {
            val staleDraft = textDraft(updatedAt = NOW - 8.days)
            val publisher = publisher(draft = staleDraft, rewind = null)

            val descriptors = publisher.computeShortcuts()

            assertEquals(1, descriptors.size)
            assertIs<DynamicShortcutDescriptor.TodayTimeline>(descriptors[0])
        }

    @Test
    fun `excludes drafts with no notes`() =
        runTest {
            val emptyDraft = textDraft(notes = emptyList(), updatedAt = NOW - 1.hours)
            val publisher = publisher(draft = emptyDraft, rewind = null)

            val descriptors = publisher.computeShortcuts()

            assertEquals(1, descriptors.size)
            assertIs<DynamicShortcutDescriptor.TodayTimeline>(descriptors[0])
        }

    @Test
    fun `includes WeekRewind when rewind result is Success`() =
        runTest {
            val rewindId = Uuid.random()
            val publisher =
                publisher(
                    draft = null,
                    rewind = RewindQueryResult.Success(rewindWith(uid = rewindId)),
                )

            val descriptors = publisher.computeShortcuts()

            assertEquals(2, descriptors.size)
            assertIs<DynamicShortcutDescriptor.TodayTimeline>(descriptors[0])
            val rewind = assertIs<DynamicShortcutDescriptor.WeekRewind>(descriptors[1])
            assertEquals(rewindId, rewind.rewindId)
        }

    @Test
    fun `skips WeekRewind when rewind state is NotReady`() =
        runTest {
            val publisher = publisher(draft = null, rewind = RewindQueryResult.NotReady)

            val descriptors = publisher.computeShortcuts()

            assertEquals(1, descriptors.size)
            assertTrue(descriptors.none { it is DynamicShortcutDescriptor.WeekRewind })
        }

    @Test
    fun `skips WeekRewind when rewind state is Generating`() =
        runTest {
            val publisher = publisher(draft = null, rewind = RewindQueryResult.Generating)

            val descriptors = publisher.computeShortcuts()

            assertEquals(1, descriptors.size)
            assertTrue(descriptors.none { it is DynamicShortcutDescriptor.WeekRewind })
        }

    @Test
    fun `skips WeekRewind when rewind state is NoneAvailable`() =
        runTest {
            val publisher = publisher(draft = null, rewind = RewindQueryResult.NoneAvailable)

            val descriptors = publisher.computeShortcuts()

            assertEquals(1, descriptors.size)
            assertTrue(descriptors.none { it is DynamicShortcutDescriptor.WeekRewind })
        }

    @Test
    fun `skips WeekRewind when rewind flow is empty`() =
        runTest {
            val publisher =
                DynamicShortcutPublisher(
                    fetchMostRecentDraft = { flowOf(null) },
                    currentWeekRewind = { flowOf() },
                    clock = fixedClock(NOW),
                    timeZone = TimeZone.UTC,
                )

            val descriptors = publisher.computeShortcuts()

            assertEquals(1, descriptors.size)
            assertIs<DynamicShortcutDescriptor.TodayTimeline>(descriptors[0])
        }

    @Test
    fun `priority order is ContinueDraft, TodayTimeline, WeekRewind`() =
        runTest {
            val draft = textDraft(updatedAt = NOW - 30.minutes)
            val rewind = RewindQueryResult.Success(rewindWith(uid = Uuid.random()))
            val publisher = publisher(draft = draft, rewind = rewind)

            val descriptors = publisher.computeShortcuts()

            assertEquals(3, descriptors.size)
            assertIs<DynamicShortcutDescriptor.ContinueDraft>(descriptors[0])
            assertIs<DynamicShortcutDescriptor.TodayTimeline>(descriptors[1])
            assertIs<DynamicShortcutDescriptor.WeekRewind>(descriptors[2])
        }

    @Test
    fun `caps at maxShortcuts`() =
        runTest {
            val draft = textDraft(updatedAt = NOW - 1.hours)
            val rewind = RewindQueryResult.Success(rewindWith(uid = Uuid.random()))
            val publisher =
                publisher(
                    draft = draft,
                    rewind = rewind,
                    maxShortcuts = 2,
                )

            val descriptors = publisher.computeShortcuts()

            assertEquals(2, descriptors.size)
            assertIs<DynamicShortcutDescriptor.ContinueDraft>(descriptors[0])
            assertIs<DynamicShortcutDescriptor.TodayTimeline>(descriptors[1])
        }

    @Test
    fun `descriptor ids are stable across publishes`() =
        runTest {
            // Even when the underlying draft id changes, the shortcut id remains
            // the same so pinned launcher shortcuts continue to resolve.
            val draftA = textDraft(id = Uuid.random(), updatedAt = NOW - 2.hours)
            val draftB = textDraft(id = Uuid.random(), updatedAt = NOW - 1.hours)
            val publisherA = publisher(draft = draftA, rewind = null)
            val publisherB = publisher(draft = draftB, rewind = null)

            val firstResult = publisherA.computeShortcuts()
            val secondResult = publisherB.computeShortcuts()

            assertEquals(
                firstResult.first { it is DynamicShortcutDescriptor.ContinueDraft }.id,
                secondResult.first { it is DynamicShortcutDescriptor.ContinueDraft }.id,
            )
        }

    // -- Helpers ----------------------------------------------------------------

    private fun publisher(
        draft: EntryDraft?,
        rewind: RewindQueryResult?,
        clockInstant: Instant = NOW,
        timeZone: TimeZone = TimeZone.UTC,
        maxShortcuts: Int = DynamicShortcutPublisher.DEFAULT_MAX_SHORTCUTS,
        draftFreshness: Duration = DynamicShortcutPublisher.DEFAULT_DRAFT_FRESHNESS,
    ): DynamicShortcutPublisher =
        DynamicShortcutPublisher(
            fetchMostRecentDraft = { flowOf(draft) },
            currentWeekRewind = { if (rewind != null) flowOf(rewind) else flowOf() },
            clock = fixedClock(clockInstant),
            timeZone = timeZone,
            draftFreshness = draftFreshness,
            maxShortcuts = maxShortcuts,
        )

    private fun textDraft(
        id: Uuid = Uuid.random(),
        notes: List<JournalNote> =
            listOf(
                JournalNote.Text(
                    creationTimestamp = NOW - 1.days,
                    lastUpdated = NOW,
                    content = "Draft body",
                ),
            ),
        updatedAt: Instant = NOW,
    ): EntryDraft =
        EntryDraft(
            id = id,
            notes = notes,
            createdAt = updatedAt - 1.hours,
            updatedAt = updatedAt,
        )

    private fun rewindWith(uid: Uuid): Rewind =
        Rewind(
            uid = uid,
            startDate = NOW - 7.days,
            endDate = NOW,
            generationDate = NOW,
            label = "2026#14",
            title = "Last week",
        )

    private fun fixedClock(instant: Instant): Clock =
        object : Clock {
            override fun now(): Instant = instant
        }

    private companion object {
        // Tuesday April 7, 2026, 12:00 UTC — fixed reference instant for all tests.
        val NOW: Instant = Instant.parse("2026-04-07T12:00:00Z")
    }
}
