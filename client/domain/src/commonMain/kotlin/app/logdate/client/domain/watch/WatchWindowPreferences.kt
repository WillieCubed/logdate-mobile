package app.logdate.client.domain.watch

import app.logdate.client.sync.peer.state.Stamped

/** One selection used for both notes and marks during a library publication. */
data class WatchWindowPreferences(
    val days: Int = SharedPreferenceKey.WindowDays.default,
    val onThisDay: Boolean = SharedPreferenceKey.WindowOnThisDay.default,
    val rewind: Boolean = SharedPreferenceKey.WindowRewind.default,
) {
    companion object {
        fun from(shared: Map<String, Stamped>): WatchWindowPreferences =
            WatchWindowPreferences(
                days = SharedPreferenceKey.WindowDays.read(shared[SharedPreferenceKey.WindowDays.name]),
                onThisDay = SharedPreferenceKey.WindowOnThisDay.read(shared[SharedPreferenceKey.WindowOnThisDay.name]),
                rewind = SharedPreferenceKey.WindowRewind.read(shared[SharedPreferenceKey.WindowRewind.name]),
            )
    }
}
