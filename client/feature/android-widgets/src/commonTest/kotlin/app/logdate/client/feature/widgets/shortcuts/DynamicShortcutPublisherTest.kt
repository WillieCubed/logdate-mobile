package app.logdate.client.feature.widgets.shortcuts

import app.logdate.client.domain.rewind.RewindQueryResult
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.Journal
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

/**
 * Tests for the [DynamicShortcutPublisher], which manages the creation and updating of
 * dynamic shortcuts and Direct Share targets on Android.
 *
 * This suite verifies the priority logic for different shortcut types (e.g., drafts,
 * rewinds, and current timeline), ensures that shortcuts are only shown when relevant
 * (e.g., within freshness windows), and confirms that shortcut IDs remain stable
 * to support pinned launcher icons.
 */
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
                    observeJournals = { flowOf(emptyList()) },
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

    // -- Sharing shortcuts (per-journal Direct Share targets) ------------------

    @Test
    fun `emits no ShareToJournal descriptors when journal list is empty`() =
        runTest {
            val descriptors = publisher(journals = emptyList()).computeShortcuts()

            assertTrue(descriptors.none { it is DynamicShortcutDescriptor.ShareToJournal })
        }

    @Test
    fun `emits one ShareToJournal descriptor per journal`() =
        runTest {
            val a = journal(title = "Travel 2026", lastUpdated = NOW)
            val b = journal(title = "Work", lastUpdated = NOW - 1.days)

            val descriptors = publisher(journals = listOf(a, b)).computeShortcuts()

            val shareDescriptors = descriptors.filterIsInstance<DynamicShortcutDescriptor.ShareToJournal>()
            assertEquals(2, shareDescriptors.size)
            assertEquals(setOf(a.id, b.id), shareDescriptors.map { it.journalId }.toSet())
        }

    @Test
    fun `sorts ShareToJournal descriptors by lastUpdated descending`() =
        runTest {
            val older = journal(title = "Old", lastUpdated = NOW - 7.days)
            val newest = journal(title = "Newest", lastUpdated = NOW)
            val middle = journal(title = "Middle", lastUpdated = NOW - 1.days)

            val descriptors = publisher(journals = listOf(older, newest, middle)).computeShortcuts()

            val shareDescriptors = descriptors.filterIsInstance<DynamicShortcutDescriptor.ShareToJournal>()
            assertEquals(
                listOf(newest.id, middle.id, older.id),
                shareDescriptors.map { it.journalId },
            )
        }

    @Test
    fun `sorts favorited journals ahead of more recently updated non-favorites`() =
        runTest {
            val favorite = journal(title = "Favorite", isFavorited = true, lastUpdated = NOW - 7.days)
            val recent = journal(title = "Recent", isFavorited = false, lastUpdated = NOW)

            val descriptors = publisher(journals = listOf(recent, favorite)).computeShortcuts()

            val shareDescriptors = descriptors.filterIsInstance<DynamicShortcutDescriptor.ShareToJournal>()
            assertEquals(listOf(favorite.id, recent.id), shareDescriptors.map { it.journalId })
        }

    @Test
    fun `excludes journals with blank titles`() =
        runTest {
            val good = journal(title = "Travel 2026")
            val blank = journal(title = "")
            val whitespace = journal(title = "   ")

            val descriptors = publisher(journals = listOf(good, blank, whitespace)).computeShortcuts()

            val shareDescriptors = descriptors.filterIsInstance<DynamicShortcutDescriptor.ShareToJournal>()
            assertEquals(1, shareDescriptors.size)
            assertEquals(good.id, shareDescriptors.single().journalId)
        }

    @Test
    fun `ShareToJournal id is stable per journal id across publishes`() =
        runTest {
            val journalId = Uuid.random()
            val firstSnapshot = journal(id = journalId, title = "Travel", lastUpdated = NOW - 5.days)
            val secondSnapshot = journal(id = journalId, title = "Travel", lastUpdated = NOW)

            val firstResult = publisher(journals = listOf(firstSnapshot)).computeShortcuts()
            val secondResult = publisher(journals = listOf(secondSnapshot)).computeShortcuts()

            val firstId = firstResult.filterIsInstance<DynamicShortcutDescriptor.ShareToJournal>().single().id
            val secondId = secondResult.filterIsInstance<DynamicShortcutDescriptor.ShareToJournal>().single().id
            assertEquals(firstId, secondId)
        }

    @Test
    fun `ShareToJournal id differs across distinct journals`() =
        runTest {
            val a = journal(id = Uuid.random(), title = "Travel")
            val b = journal(id = Uuid.random(), title = "Work")

            val descriptors = publisher(journals = listOf(a, b)).computeShortcuts()

            val ids = descriptors.filterIsInstance<DynamicShortcutDescriptor.ShareToJournal>().map { it.id }.toSet()
            assertEquals(2, ids.size)
        }

    @Test
    fun `ShareToJournal threads coverImageUri faithfully`() =
        runTest {
            val withCover = journal(title = "With cover", coverImageUri = "content://media/journal/42")
            val withoutCover = journal(title = "No cover", coverImageUri = null)

            val descriptors =
                publisher(journals = listOf(withCover, withoutCover)).computeShortcuts()

            val shareDescriptors =
                descriptors
                    .filterIsInstance<DynamicShortcutDescriptor.ShareToJournal>()
                    .associateBy { it.journalId }
            assertEquals("content://media/journal/42", shareDescriptors[withCover.id]?.coverImageUri)
            assertEquals(null, shareDescriptors[withoutCover.id]?.coverImageUri)
        }

    @Test
    fun `ShareToJournal descriptors come after all launcher descriptors`() =
        runTest {
            val draft = textDraft(updatedAt = NOW - 1.hours)
            val rewindResult = RewindQueryResult.Success(rewindWith(uid = Uuid.random()))
            val a = journal(title = "Travel 2026")
            val b = journal(title = "Work", lastUpdated = NOW - 2.days)

            val descriptors =
                publisher(
                    draft = draft,
                    rewind = rewindResult,
                    journals = listOf(a, b),
                ).computeShortcuts()

            // Find the first sharing index
            val firstShareIndex =
                descriptors.indexOfFirst { it is DynamicShortcutDescriptor.ShareToJournal }
            val lastLauncherIndex =
                descriptors.indexOfLast { it !is DynamicShortcutDescriptor.ShareToJournal }
            assertTrue(firstShareIndex > lastLauncherIndex)
        }

    @Test
    fun `does not cap the number of ShareToJournal descriptors`() =
        runTest {
            // Five journals must produce five descriptors — no arbitrary cap.
            val journals =
                (0 until 5).map { i ->
                    journal(title = "Journal $i", lastUpdated = NOW - i.days)
                }

            val descriptors = publisher(journals = journals).computeShortcuts()

            val shareCount = descriptors.count { it is DynamicShortcutDescriptor.ShareToJournal }
            assertEquals(5, shareCount)
        }

    // -- Helpers ----------------------------------------------------------------

    private fun publisher(
        draft: EntryDraft? = null,
        rewind: RewindQueryResult? = null,
        journals: List<Journal> = emptyList(),
        clockInstant: Instant = NOW,
        timeZone: TimeZone = TimeZone.UTC,
        maxShortcuts: Int = DynamicShortcutPublisher.DEFAULT_MAX_SHORTCUTS,
        draftFreshness: Duration = DynamicShortcutPublisher.DEFAULT_DRAFT_FRESHNESS,
    ): DynamicShortcutPublisher =
        DynamicShortcutPublisher(
            fetchMostRecentDraft = { flowOf(draft) },
            currentWeekRewind = { if (rewind != null) flowOf(rewind) else flowOf() },
            observeJournals = { flowOf(journals) },
            clock = fixedClock(clockInstant),
            timeZone = timeZone,
            draftFreshness = draftFreshness,
            maxShortcuts = maxShortcuts,
        )

    private fun journal(
        id: Uuid = Uuid.random(),
        title: String = "Travel 2026",
        isFavorited: Boolean = false,
        coverImageUri: String? = null,
        lastUpdated: Instant = NOW,
    ): Journal =
        Journal(
            id = id,
            title = title,
            description = "",
            isFavorited = isFavorited,
            created = lastUpdated - 1.days,
            lastUpdated = lastUpdated,
            syncVersion = 0,
            coverImageUri = coverImageUri,
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
