package app.logdate.client.sync.continuity

import kotlinx.serialization.Serializable

@Serializable
enum class HandoffFailure {
    AUDIO_NOT_ON_PHONE,
    AUDIO_NOT_ON_WATCH,
    PHONE_NEEDS_SETUP,
    PLAYBACK_FAILED,
    NOT_ALLOWED,
    WATCH_STARTING,
    WATCH_DID_NOT_RESPOND,
    WATCH_DID_NOT_OPEN,
    PHONE_PLAYBACK_CHANGED,
}

@Serializable
data class PlaybackHandoffResult(
    val schema: Int = 1,
    val accepted: Boolean,
    val failure: HandoffFailure? = null,
)
