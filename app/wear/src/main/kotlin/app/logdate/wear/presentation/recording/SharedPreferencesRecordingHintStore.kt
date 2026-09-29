package app.logdate.wear.presentation.recording

import android.content.Context
import androidx.core.content.edit

class SharedPreferencesRecordingHintStore(
    context: Context,
) : RecordingHintStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun hasSeenHint(): Boolean = preferences.getBoolean(KEY_HINT_SEEN, false)

    override fun markHintSeen() {
        preferences.edit { putBoolean(KEY_HINT_SEEN, true) }
    }

    private companion object {
        const val PREFERENCES_NAME = "wear_recorder"
        const val KEY_HINT_SEEN = "gesture_hint_seen"
    }
}
