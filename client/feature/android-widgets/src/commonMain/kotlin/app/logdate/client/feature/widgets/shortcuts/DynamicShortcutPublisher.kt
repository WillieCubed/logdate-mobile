package app.logdate.client.feature.widgets.shortcuts

import app.logdate.client.domain.journals.GetCurrentUserJournalsUseCase
import app.logdate.client.domain.notes.drafts.FetchMostRecentDraftUseCase
import app.logdate.client.domain.rewind.GetWeekRewindUseCase
import app.logdate.client.domain.rewind.RewindQueryResult
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Computes the dynamic launcher shortcuts LogDate should currently expose,
 * including per-journal Direct Share targets.
 *
 * Pure logic: it asks the existing draft, weekly-rewind, and journal use cases
 * for the current state, then decides which descriptors to surface. The
 * Android-only applier converts each descriptor into a `ShortcutInfoCompat` +
 * Intent and hands the resulting list to `ShortcutManagerCompat`.
 *
 * The output is two logical groups in a single ordered list:
 *
 * **Launcher group** (capped at [maxShortcuts] to leave room for the static
 * "new entry" launcher fallback):
 * 1. **Continue draft** — only when the most recently updated draft has been
 *    touched within [draftFreshness] and contains at least one note.
 * 2. **Today's timeline** — always present.
 * 3. **Last week's rewind** — only when [currentWeekRewind] currently emits
 *    [RewindQueryResult.Success]. Other states (NotReady, Generating,
 *    NoneAvailable) are skipped silently.
 *
 * **Sharing group** (no cap — see [observeJournals]):
 * - One [DynamicShortcutDescriptor.ShareToJournal] per journal with a
 *   non-blank title, sorted by `lastUpdated` descending so the most active
 *   journals come first. The Android applier trims this group at apply time
 *   if the system's per-activity shortcut limit would be exceeded.
 *
 * @param fetchMostRecentDraft Factory returning a cold flow of the most
 *   recently updated draft (or null when there are none). Uses
 *   [FetchMostRecentDraftUseCase] in
 *   production.
 * @param currentWeekRewind Factory returning a cold flow of the most recent
 *   weekly rewind result. Uses
 *   [GetWeekRewindUseCase] in production.
 * @param observeJournals Factory returning a cold flow of all of the user's
 *   journals. Uses
 *   [GetCurrentUserJournalsUseCase] in
 *   production.
 *   Function references rather than the concrete use cases keep the publisher
 *   trivially fakeable in commonTest without depending on use-case internals.
 */
class DynamicShortcutPublisher(
    private val fetchMostRecentDraft: () -> Flow<EntryDraft?>,
    private val currentWeekRewind: () -> Flow<RewindQueryResult>,
    private val observeJournals: () -> Flow<List<Journal>>,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val draftFreshness: Duration = DEFAULT_DRAFT_FRESHNESS,
    private val maxShortcuts: Int = DEFAULT_MAX_SHORTCUTS,
) {
    /**
     * Snapshots the current state of drafts, rewinds, and journals and returns
     * the descriptors that should be published right now. Launcher descriptors
     * come first (capped at [maxShortcuts]), followed by every eligible
     * sharing descriptor in `lastUpdated` order — no artificial cap on
     * sharing entries; the Android applier handles system-limit trimming.
     */
    suspend fun computeShortcuts(): List<DynamicShortcutDescriptor> {
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        val cutoff = now - draftFreshness
        val draft = fetchMostRecentDraft().firstOrNull()
        val freshDraft = draft?.takeIf { it.notes.isNotEmpty() && it.updatedAt >= cutoff }
        val rewindResult = currentWeekRewind().firstOrNull()
        val journals = observeJournals().firstOrNull().orEmpty()

        val launcherDescriptors =
            buildList {
                if (freshDraft != null) {
                    add(DynamicShortcutDescriptor.ContinueDraft(draftId = freshDraft.id))
                }
                add(DynamicShortcutDescriptor.TodayTimeline(date = today))
                if (rewindResult is RewindQueryResult.Success) {
                    add(DynamicShortcutDescriptor.WeekRewind(rewindId = rewindResult.rewind.uid))
                }
            }.take(maxShortcuts)

        val sharingDescriptors =
            journals
                .filter { it.title.isNotBlank() }
                .sortedWith(
                    compareByDescending<Journal> { it.isFavorited }
                        .thenByDescending { it.lastUpdated },
                )
                .map { journal ->
                    DynamicShortcutDescriptor.ShareToJournal(
                        journalId = journal.id,
                        journalTitle = journal.title,
                        coverImageUri = journal.coverImageUri,
                    )
                }

        return launcherDescriptors + sharingDescriptors
    }

    companion object {
        /** Drafts older than this are considered abandoned and not surfaced as shortcuts. */
        val DEFAULT_DRAFT_FRESHNESS: Duration = 7.days

        /** Three slots leaves room for the static "new entry" shortcut as the fourth launcher entry. */
        const val DEFAULT_MAX_SHORTCUTS: Int = 3
    }
}
