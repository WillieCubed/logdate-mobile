package app.logdate.wear.v2.playback

import app.logdate.client.repository.journals.JournalNote

/** How long a line stays up after it ends, so short gaps between lines do not flicker. */
const val CAPTION_LINGER_MS = 1_500L

/**
 * The transcript line spoken at [positionMs], or null when the note has no timed transcript or
 * nothing is said there.
 */
fun captionAt(
    note: JournalNote.Audio,
    positionMs: Long,
): String? {
    val timed =
        note.transcript
            ?.segments
            .orEmpty()
            .filter { it.endMs > it.startMs && it.text.isNotBlank() }
    val line = timed.lastOrNull { it.startMs <= positionMs } ?: return null
    if (positionMs >= line.endMs + CAPTION_LINGER_MS) return null
    return line.text.trim()
}

/** The whole transcript of [note], or null when it has none. */
fun fullTranscript(note: JournalNote.Audio): String? =
    note.transcript?.plainText?.takeIf(String::isNotBlank) ?: note.transcription.trim().takeIf(String::isNotEmpty)
