package app.logdate.client.notifications

/**
 * Whether ongoing work may be promoted to a Live Update: a chip in the status bar plus a place at the
 * top of the shade and on the lock screen (Android 16 and later).
 *
 * Modules that build notifications read this instead of the feature flag store, so they do not
 * depend on it. The app binds an implementation backed by the LIVE_UPDATES flag.
 */
interface LiveUpdatesSwitch {
    val enabled: Boolean

    /** Live Updates off, used where the app has not provided a switch. */
    object Off : LiveUpdatesSwitch {
        override val enabled: Boolean = false
    }
}

/** Short status-bar chip text for states the elapsed-time chronometer cannot express. */
enum class LiveUpdateChip { Paused, }

/**
 * How a notification should present itself as a Live Update.
 *
 * @property promoted Whether to request promotion to a Live Update.
 * @property chipText Text for the status-bar chip, or null to let the chip show the chronometer.
 */
data class LiveUpdate(
    val promoted: Boolean,
    val chipText: LiveUpdateChip?,
) {
    companion object {
        val None = LiveUpdate(promoted = false, chipText = null)
    }
}

/**
 * The Live Update for a voice recording. While recording, the chip shows the elapsed time from the
 * notification's chronometer; while paused, it says so.
 */
fun recordingLiveUpdate(
    enabled: Boolean,
    isRecording: Boolean,
): LiveUpdate =
    when {
        !enabled -> LiveUpdate.None
        isRecording -> LiveUpdate(promoted = true, chipText = null)
        else -> LiveUpdate(promoted = true, chipText = LiveUpdateChip.Paused)
    }

/**
 * Whether a running recording may float as a bubble. The app binds an implementation backed by the
 * RECORDING_BUBBLE flag; anywhere none is bound, the recording notification offers no Float action.
 */
interface RecordingBubbleSwitch {
    val enabled: Boolean

    object Off : RecordingBubbleSwitch {
        override val enabled: Boolean = false
    }
}

/** The broadcast the recording notification's Float action sends to the app. */
const val ACTION_FLOAT_RECORDER = "app.logdate.action.FLOAT_RECORDER"

/** The app's receiver for [ACTION_FLOAT_RECORDER], named so notification code needs no app dependency. */
const val FLOAT_RECORDER_RECEIVER_CLASS = "app.logdate.client.bubble.FloatRecorderReceiver"

/**
 * How long-running data work (export, restore, sync) presents itself as a Live Update.
 *
 * @property promoted Whether to request promotion to a Live Update.
 * @property chipPercent The percent for the status-bar chip, or null while progress is unknown.
 */
data class TransferLiveUpdate(
    val promoted: Boolean,
    val chipPercent: Int?,
)

/**
 * The Live Update for data work at [percent] complete, or with unknown progress when [percent] is
 * null. The chip shows the percent once it is known; promotion itself does not wait for it, because
 * the work is already running and the person is waiting on it.
 */
fun transferLiveUpdate(
    enabled: Boolean,
    percent: Int?,
): TransferLiveUpdate =
    if (!enabled) {
        TransferLiveUpdate(promoted = false, chipPercent = null)
    } else {
        TransferLiveUpdate(promoted = true, chipPercent = percent?.coerceIn(0, 100))
    }

/**
 * Splits a 0-[max] progress bar into one segment per phase, so the bar shows which phase the work is
 * in. [phaseStarts] are the progress values where each phase begins; duplicates and values outside
 * the bar are ignored. Returns each segment's length, which together always add up to [max].
 */
fun progressSegmentLengths(
    phaseStarts: List<Int>,
    max: Int = 100,
): List<Int> {
    val starts = (phaseStarts.filter { it in 0 until max } + 0).distinct().sorted()
    return starts.mapIndexed { index, start -> (starts.getOrNull(index + 1) ?: max) - start }
}
