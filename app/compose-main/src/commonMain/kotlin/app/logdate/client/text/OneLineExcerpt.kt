package app.logdate.client.text

/**
 * [content] on one line, with each line trimmed and joined by a space, and shortened to [maxLength]
 * characters with an ellipsis when longer. For previews that must fit one line, such as a
 * notification or an assistant's reply.
 */
fun oneLineExcerpt(
    content: String,
    maxLength: Int,
): String {
    val oneLine = content.lines().joinToString(" ") { it.trim() }.trim()
    return if (oneLine.length <= maxLength) oneLine else oneLine.take(maxLength - 1).trimEnd() + "…"
}
