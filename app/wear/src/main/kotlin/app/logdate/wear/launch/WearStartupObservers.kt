package app.logdate.wear.launch

/** A retry must not duplicate observers whose startup already succeeded. */
internal class WearStartupObservers {
    private val started = mutableSetOf<String>()

    @Synchronized
    fun start(
        name: String,
        action: () -> Unit,
    ) {
        if (name in started) return
        action()
        started += name
    }
}
