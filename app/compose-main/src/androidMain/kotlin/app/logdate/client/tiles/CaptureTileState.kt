package app.logdate.client.tiles

/** What the Record voice tile's subtitle says. */
enum class VoiceTileStatus { Idle, Recording, Paused }

/** How the Record voice tile looks: active (highlighted) and its status line. */
data class VoiceTileAppearance(
    val active: Boolean,
    val status: VoiceTileStatus,
)

/** What tapping the Record voice tile does. */
enum class VoiceTileAction {
    /** Opens the editor in voice mode, which starts recording. */
    StartRecording,

    /** Brings back the entry that is already recording instead of starting a second one. */
    ShowRecording,
}

fun voiceTileAppearance(
    isRecording: Boolean,
    isPaused: Boolean,
): VoiceTileAppearance =
    when {
        !isRecording -> VoiceTileAppearance(active = false, status = VoiceTileStatus.Idle)
        isPaused -> VoiceTileAppearance(active = true, status = VoiceTileStatus.Paused)
        else -> VoiceTileAppearance(active = true, status = VoiceTileStatus.Recording)
    }

fun voiceTileAction(isRecording: Boolean): VoiceTileAction =
    if (isRecording) VoiceTileAction.ShowRecording else VoiceTileAction.StartRecording
