package app.logdate.client.domain.today

import app.logdate.client.repository.journals.JournalNote

/**
 * Reads the tag a mood check-in starts with, such as `#mood:great Feeling great`.
 *
 * The watch saves a check-in as a text note whose first word is the tag; only the tag carries the
 * feeling. A check-in describes the day rather than being a moment of it, so summaries count it
 * apart from the moments.
 */
object MoodNoteTag {
    const val PREFIX = "#mood:"
    private val TAG = Regex("^#mood:(\\w+)")

    /** The tag [content] starts with, or null when it is not a check-in. */
    fun tagOf(content: String): String? = TAG.find(content)?.groupValues?.get(1)

    fun isFeeling(note: JournalNote): Boolean = note is JournalNote.Text && tagOf(note.content) != null

    /**
     * The tag of the newest check-in among [notes] that [isKnown] accepts, or null. A newer
     * check-in with a tag this version does not know, written by a later build, is skipped.
     */
    fun latestTag(
        notes: List<JournalNote>,
        isKnown: (String) -> Boolean = { true },
    ): String? =
        notes
            .filterIsInstance<JournalNote.Text>()
            .sortedByDescending(JournalNote::creationTimestamp)
            .firstNotNullOfOrNull { note -> tagOf(note.content)?.takeIf(isKnown) }
}
