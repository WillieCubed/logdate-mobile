package app.logdate.wear.v2.playback

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The speed and skip silence the next recording plays with. */
interface PlaybackPreferencesStore {
    val speed: StateFlow<PlaybackSpeed>
    val skipSilence: StateFlow<Boolean>

    fun setSpeed(speed: PlaybackSpeed)

    fun setSkipSilence(enabled: Boolean)

    fun clearForRemoval() {
        setSpeed(PlaybackSpeed.fromStored(null))
        setSkipSilence(false)
    }
}

/** [PlaybackPreferencesStore] in the watch's own SharedPreferences file, [PREFERENCES_NAME]. */
class SharedPreferencesPlaybackPreferencesStore(
    private val preferences: SharedPreferences,
) : PlaybackPreferencesStore {
    private val currentSpeed = MutableStateFlow(PlaybackSpeed.fromStored(preferences.getString(KEY_SPEED, null)))
    private val currentSkipSilence = MutableStateFlow(preferences.getBoolean(KEY_SKIP_SILENCE, false))

    override val speed: StateFlow<PlaybackSpeed> = currentSpeed.asStateFlow()
    override val skipSilence: StateFlow<Boolean> = currentSkipSilence.asStateFlow()

    override fun setSpeed(speed: PlaybackSpeed) {
        preferences.edit { putString(KEY_SPEED, speed.storedName) }
        currentSpeed.value = speed
    }

    override fun setSkipSilence(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_SKIP_SILENCE, enabled) }
        currentSkipSilence.value = enabled
    }

    override fun clearForRemoval() {
        check(preferences.edit().clear().commit()) { "The removed watch's playback preferences could not be cleared" }
        currentSpeed.value = PlaybackSpeed.fromStored(null)
        currentSkipSilence.value = false
    }

    companion object {
        const val PREFERENCES_NAME = "wear_playback"
        private const val KEY_SPEED = "speed"
        private const val KEY_SKIP_SILENCE = "skip_silence"
    }
}
