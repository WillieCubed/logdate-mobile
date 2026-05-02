package app.logdate.client.shortcuts

import android.content.Intent
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

/**
 * Intent extra carrying the target journal id when a sharing shortcut is
 * launched from the launcher long-press menu (the long-press fallback path,
 * not the share-sheet path which uses the system-supplied
 * `Intent.EXTRA_SHORTCUT_ID`).
 *
 * The value is the journal's UUID encoded as a String, parseable via
 * `kotlin.uuid.Uuid.parse(...)`.
 */
const val EXTRA_SHORTCUT_TARGET_JOURNAL_ID: String = "logdate.shortcuts.extra.TARGET_JOURNAL_ID"

internal fun Intent.parseLauncherShortcutTargetJournalId(): Uuid? =
    parseLauncherShortcutTargetJournalId(getStringExtra(EXTRA_SHORTCUT_TARGET_JOURNAL_ID))

internal fun parseLauncherShortcutTargetJournalId(rawJournalId: String?): Uuid? {
    rawJournalId ?: return null
    return runCatching { Uuid.parse(rawJournalId) }
        .onFailure { Napier.w("Could not parse launcher shortcut journal id: $rawJournalId", it) }
        .getOrNull()
}
