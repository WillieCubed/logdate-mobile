package app.logdate.wear.v2.playback

import kotlin.uuid.Uuid

/** What the watch is playing, as every screen of the new app sees it. */
sealed interface PlaybackState {
    data object Idle : PlaybackState

    /** The recording is being found or fetched. */
    data class Preparing(
        val noteId: Uuid,
    ) : PlaybackState

    data class Active(
        val noteId: Uuid,
        val positionMs: Long,
        val durationMs: Long,
        val isPaused: Boolean,
    ) : PlaybackState {
        /** How far through the recording playback is, from 0 to 1; 0 while the length is unknown. */
        val fraction: Float
            get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    data class Failed(
        val noteId: Uuid,
        val reason: PlaybackFailure,
    ) : PlaybackState
}

/** The recording this state is about, or null while nothing plays. */
val PlaybackState.noteId: Uuid?
    get() =
        when (this) {
            PlaybackState.Idle -> null
            is PlaybackState.Preparing -> noteId
            is PlaybackState.Active -> noteId
            is PlaybackState.Failed -> noteId
        }

/** Why a recording did not play. */
enum class PlaybackFailure {
    /** No speaker or headphones, or the system refused the speaker. */
    NO_OUTPUT,

    /** The audio is on the phone and no phone is in reach. */
    NOT_ON_WATCH,

    /** The watch is too short of space to fetch the audio. */
    NO_ROOM,

    /** The file could not be read or decoded. */
    UNPLAYABLE,
}

/** The speeds playback offers, in the order a tap steps through them. */
enum class PlaybackSpeed(
    val factor: Float,
    val storedName: String,
) {
    NORMAL(1f, "1"),
    FASTER(1.5f, "1.5"),
    FASTEST(2f, "2"),
    ;

    fun next(): PlaybackSpeed = entries[(ordinal + 1) % entries.size]

    companion object {
        /** The speed saved as [storedName], or [NORMAL] when nothing valid was saved. */
        fun fromStored(storedName: String?): PlaybackSpeed = entries.firstOrNull { it.storedName == storedName } ?: NORMAL
    }
}
