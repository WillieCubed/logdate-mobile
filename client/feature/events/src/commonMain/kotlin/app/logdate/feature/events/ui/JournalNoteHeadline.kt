package app.logdate.feature.events.ui

import app.logdate.client.repository.journals.JournalNote

/**
 * Short single-line label for a [JournalNote] used in the event editor's compact lists.
 *
 * Text notes show their first line truncated; media notes show a generic media-type label.
 * Used by both the linked-notes list and the attach-note picker so they stay consistent.
 */
internal fun JournalNote.headline(): String =
    when (this) {
        is JournalNote.Text ->
            content
                .lineSequence()
                .firstOrNull()
                ?.take(120)
                .orEmpty()
                .ifEmpty { "Text note" }

        is JournalNote.Image -> "Photo"
        is JournalNote.Video -> "Video"
        is JournalNote.Audio -> "Voice memo"
    }
