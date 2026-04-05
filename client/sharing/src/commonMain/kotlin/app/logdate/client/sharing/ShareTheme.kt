package app.logdate.client.sharing

sealed interface ShareTheme {
    data object Light : ShareTheme

    data object Dark : ShareTheme

    // TOOD: Support custom themes for sharing journals
    data class Custom(
        val background: Int,
        val foreground: Int,
    ) : ShareTheme
}

/**
 * A stable, ASCII-only key for use in cache file names.
 *
 * Using a fixed mapping instead of reflection-based name() keeps file names predictable
 * across refactors and locale-independent.
 */
internal val ShareTheme.cacheKey: String
    get() =
        when (this) {
            ShareTheme.Light -> "light"
            ShareTheme.Dark -> "dark"
            is ShareTheme.Custom -> "custom_${background}_$foreground"
        }
