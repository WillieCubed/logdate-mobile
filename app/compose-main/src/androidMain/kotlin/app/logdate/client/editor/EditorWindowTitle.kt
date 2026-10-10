package app.logdate.client.editor

private const val MAX_TITLE_LENGTH = 42

/**
 * The window title for an editor window: the first line of what the person wrote, shortened to fit
 * Recents, the taskbar, and desktop caption bars. Null until there is text, so the caller can use a
 * generic label.
 */
fun editorWindowTitle(firstText: String?): String? {
    val line =
        firstText
            ?.lineSequence()
            ?.map(String::trim)
            ?.firstOrNull(String::isNotEmpty)
            ?: return null
    if (line.length <= MAX_TITLE_LENGTH) return line
    val cut = line.take(MAX_TITLE_LENGTH).substringBeforeLast(' ').trimEnd(',', ';', ':', '.')
    return "$cut…"
}
