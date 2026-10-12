package app.logdate.client.sync.v2.window

import app.logdate.client.domain.watch.WindowExtraIds

/** Notes other features pick for the watch's window. Rewind and starred notes supply them in D11; until then there are none. */
fun interface WindowExtras {
    suspend fun ids(): WindowExtraIds

    companion object {
        val None = WindowExtras { WindowExtraIds() }
    }
}
