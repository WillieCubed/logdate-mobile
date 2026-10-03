package app.logdate.ui.platform

/**
 * Every named haptic event LogDate can fire, on the phone and on the watch. The mapping from event
 * to primitive lives in [HapticEventToTokens] (the single tuning file). Feature code never
 * references primitives directly — it calls [LogDateHaptics] methods, which use this enum internally.
 *
 * Membership in [criticalEvents] decides whether an event survives system reduce-motion, "haptics
 * off", and the watch's Essential haptics setting. An event is critical when it reports something
 * the user cannot see or must not miss: a recording starting, locking, pausing, resuming, nearing
 * its limit or finishing, a destructive confirmation, a mark made without looking at the screen,
 * or a request that was refused. Everything else is suppressible.
 */
enum class HapticEvent {
    SaveSucceeded,
    RecordingStarted,
    RecordingFinished,
    TranscriptionReady,
    ConfirmDestruction,

    /** A recording started by holding Record locked, so lifting the finger no longer stops it. */
    RecordingLocked,

    /** A recording paused. */
    Paused,

    /** A paused recording resumed. */
    Resumed,

    /** A moment was marked, often from a complication with the screen off. */
    MomentMarked,

    /** A recording is close to its length limit and stops on its own soon. */
    LimitApproaching,

    /** Something the user asked for did not happen, such as a recording that could not start. */
    ActionRejected,

    /** The crown or a scrubber passed one detent. */
    DetentTick,
    ;

    companion object {
        /**
         * Events that always fire, even when the OS reports reduce-motion or haptics-off, or the
         * watch is set to Essential haptics. They are signals, never delight; suppressing them would
         * let users miss something important.
         */
        val criticalEvents: Set<HapticEvent> =
            setOf(
                RecordingStarted,
                RecordingFinished,
                ConfirmDestruction,
                RecordingLocked,
                Paused,
                Resumed,
                MomentMarked,
                LimitApproaching,
                ActionRejected,
            )
    }
}
