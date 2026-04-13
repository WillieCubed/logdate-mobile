package app.logdate.client.feature.widgets.shortcuts

import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/**
 * One launcher shortcut the dynamic publisher wants to surface.
 *
 * Platform-agnostic data: an Android-only applier maps each variant to a
 * `ShortcutInfoCompat` with the right label, icon, and Intent extras for the
 * main Activity to route on click. The static "new entry" shortcut declared
 * in the launcher manifest is independent of these descriptors and is always
 * present.
 *
 * Shortcut ids are stable strings rather than per-instance values so that
 * pinned shortcuts continue to resolve when the underlying data changes
 * (the system republishes pinned shortcuts under the same id).
 */
sealed interface DynamicShortcutDescriptor {
    /**
     * Stable id required by `ShortcutManagerCompat`. Survives republishes
     * so pinned shortcuts continue to work after the underlying data changes.
     */
    val id: String

    /**
     * Resume editing the most recently updated in-progress draft.
     */
    data class ContinueDraft(
        val draftId: Uuid,
    ) : DynamicShortcutDescriptor {
        override val id: String = ID

        companion object {
            const val ID: String = "logdate.shortcut.continue_draft"
        }
    }

    /**
     * Open the timeline view for the user's current local day.
     */
    data class TodayTimeline(
        val date: LocalDate,
    ) : DynamicShortcutDescriptor {
        override val id: String = ID

        companion object {
            const val ID: String = "logdate.shortcut.today_timeline"
        }
    }

    /**
     * Open the most recently generated weekly rewind.
     */
    data class WeekRewind(
        val rewindId: Uuid,
    ) : DynamicShortcutDescriptor {
        override val id: String = ID

        companion object {
            const val ID: String = "logdate.shortcut.week_rewind"
        }
    }

    /**
     * Direct Share target for a specific journal.
     *
     * Each journal gets its own conversation shortcut so the system share sheet
     * can offer named per-journal targets ("Share to Travel 2026") when the user
     * shares text or media from another app. The Android applier resolves
     * [coverImageUri] to a real bitmap when present, or falls back to the same
     * deterministic color the in-app `JournalCover` uses.
     *
     * Unlike the launcher variants above, the id is per-instance (uuid-suffixed)
     * so each journal has its own stable shortcut id — pinned share targets keep
     * working when the journal list changes shape.
     */
    data class ShareToJournal(
        val journalId: Uuid,
        val journalTitle: String,
        val coverImageUri: String?,
    ) : DynamicShortcutDescriptor {
        override val id: String = "$ID_PREFIX:$journalId"

        companion object {
            const val ID_PREFIX: String = "logdate.shortcut.share_to_journal"
        }
    }
}
