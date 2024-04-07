package app.logdate.core.sharing

sealed interface ShareTheme {
    data object Light : ShareTheme
    data object Dark : ShareTheme

    // TOOD: Support custom themes for sharing journals
    data class Custom(val background: Int, val foreground: Int) : ShareTheme
}