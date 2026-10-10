package app.logdate.client.ambient

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.text.oneLineExcerpt
import kotlin.time.Instant

/** The longest quick reply shown back in the saved confirmation before it is shortened. */
private const val CONFIRMATION_PREVIEW_LENGTH = 120

/**
 * The text entry a notification quick reply becomes, or null when the reply is blank and there is
 * nothing to save. Surrounding whitespace is dropped; line breaks inside the reply are kept.
 */
fun quickReplyNote(
    reply: CharSequence?,
    now: Instant,
    timeZoneId: String?,
): JournalNote.Text? {
    val content = reply?.toString()?.trim().orEmpty()
    if (content.isEmpty()) return null
    return JournalNote.Text(
        creationTimestamp = now,
        lastUpdated = now,
        content = content,
        timeZoneId = timeZoneId,
    )
}

/** The reply as the confirmation shows it: on one line, shortened with an ellipsis when long. */
fun quickReplyPreview(content: String): String = oneLineExcerpt(content, CONFIRMATION_PREVIEW_LENGTH)
