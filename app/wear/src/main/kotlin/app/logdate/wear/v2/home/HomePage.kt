package app.logdate.wear.v2.home

/** Home's pages, in pager order. */
enum class HomePage {
    TODAY,
    FLOW,
}

/**
 * The page a turn of the crown moves Home to, or null when the crown scrolls the page it is on.
 * Turning up at the top of the moment list returns to Today; turning down at the end of Today
 * opens the list. Nothing pages while [locked].
 */
internal fun pageForCrown(
    current: HomePage,
    scrollPixels: Float,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    locked: Boolean,
): HomePage? {
    if (locked) return null
    if (current == HomePage.FLOW && scrollPixels < 0f && !canScrollBackward) return HomePage.TODAY
    if (current == HomePage.TODAY && scrollPixels > 0f && !canScrollForward) return HomePage.FLOW
    return null
}
