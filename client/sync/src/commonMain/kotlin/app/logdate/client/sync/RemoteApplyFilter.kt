package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNote
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Decides whether a note that only the server has is stored on this device. A note already here is
 * always updated, and the download moves past a skipped note so it is not fetched again.
 */
fun interface RemoteApplyFilter {
    fun appliesNote(note: JournalNote): Boolean

    companion object {
        val ALL = RemoteApplyFilter { true }
    }
}

/** Keeps notes captured within [window] of now, the simplest library window a watch can hold. */
class RecentCaptureFilter(
    private val window: Duration,
    private val clock: Clock = Clock.System,
) : RemoteApplyFilter {
    override fun appliesNote(note: JournalNote): Boolean = note.creationTimestamp >= clock.now() - window
}
