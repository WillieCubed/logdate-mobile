package app.logdate.client.feature.widgets.shortcuts

import app.logdate.client.domain.rewind.RewindQueryResult
import app.logdate.client.repository.journals.EntryDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Computes the dynamic launcher shortcuts LogDate should currently expose.
 *
 * Pure logic: it asks the existing draft and weekly-rewind use cases for the
 * current state, then decides which optional shortcuts to surface in priority
 * order. The Android-only applier converts each descriptor into a
 * `ShortcutInfoCompat` + Intent and hands the resulting list to
 * `ShortcutManagerCompat`.
 *
 * Priority order (highest first):
 * 1. **Continue draft** — only when the most recently updated draft has been
 *    touched within [draftFreshness] and contains at least one note.
 * 2. **Today's timeline** — always present.
 * 3. **Last week's rewind** — only when [currentWeekRewind] currently emits
 *    [RewindQueryResult.Success]. Other states (NotReady, Generating,
 *    NoneAvailable) are skipped silently.
 *
 * The result is capped at [maxShortcuts] entries to leave room for the static
 * "new entry" launcher shortcut.
 *
 * @param fetchMostRecentDraft Factory returning a cold flow of the most
 *   recently updated draft (or null when there are none). Uses
 *   [app.logdate.client.domain.notes.drafts.FetchMostRecentDraftUseCase] in
 *   production.
 * @param currentWeekRewind Factory returning a cold flow of the most recent
 *   weekly rewind result. Uses
 *   [app.logdate.client.domain.rewind.GetWeekRewindUseCase] in production.
 *   Function references rather than the concrete use cases keep the publisher
 *   trivially fakeable in commonTest without depending on use-case internals.
 */
class DynamicShortcutPublisher(
    private val fetchMostRecentDraft: () -> Flow<EntryDraft?>,
    private val currentWeekRewind: () -> Flow<RewindQueryResult>,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val draftFreshness: Duration = DEFAULT_DRAFT_FRESHNESS,
    private val maxShortcuts: Int = DEFAULT_MAX_SHORTCUTS,
) {
    /**
     * Snapshots the current state of drafts and rewinds and returns the
     * descriptors that should be published right now, in launcher priority order.
     */
    suspend fun computeShortcuts(): List<DynamicShortcutDescriptor> {
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        val cutoff = now - draftFreshness
        val draft = fetchMostRecentDraft().firstOrNull()
        val freshDraft = draft?.takeIf { it.notes.isNotEmpty() && it.updatedAt >= cutoff }
        val rewindResult = currentWeekRewind().firstOrNull()

        val descriptors =
            buildList {
                if (freshDraft != null) {
                    add(DynamicShortcutDescriptor.ContinueDraft(draftId = freshDraft.id))
                }
                add(DynamicShortcutDescriptor.TodayTimeline(date = today))
                if (rewindResult is RewindQueryResult.Success) {
                    add(DynamicShortcutDescriptor.WeekRewind(rewindId = rewindResult.rewind.uid))
                }
            }
        return descriptors.take(maxShortcuts)
    }

    companion object {
        /** Drafts older than this are considered abandoned and not surfaced as shortcuts. */
        val DEFAULT_DRAFT_FRESHNESS: Duration = 7.days

        /** Three slots leaves room for the static "new entry" shortcut as the fourth launcher entry. */
        const val DEFAULT_MAX_SHORTCUTS: Int = 3
    }
}
