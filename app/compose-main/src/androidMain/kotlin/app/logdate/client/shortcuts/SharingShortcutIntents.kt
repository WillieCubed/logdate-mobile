package app.logdate.client.shortcuts

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
