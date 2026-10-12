package app.logdate.client.sync.v2.window

interface WindowRollWork {
    fun runAfter(delayMs: Long)

    fun cancel()

    object None : WindowRollWork {
        override fun runAfter(delayMs: Long) = Unit

        override fun cancel() = Unit
    }
}
