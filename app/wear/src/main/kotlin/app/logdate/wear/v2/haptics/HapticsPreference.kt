package app.logdate.wear.v2.haptics

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How much the watch vibrates. */
enum class HapticsPreference(
    val storedName: String,
) {
    /** Every haptic LogDate plays, including crown ticks and save confirmations. */
    ALL("all"),

    /** Only changes to a recording, marks and refused actions. */
    ESSENTIAL("essential"),
    ;

    companion object {
        /** The preference saved as [storedName], or [ALL] when nothing valid was saved. */
        fun fromStored(storedName: String?): HapticsPreference = entries.firstOrNull { it.storedName == storedName } ?: ALL
    }
}

/** The watch's haptics setting. */
interface HapticsPreferenceStore {
    val preference: StateFlow<HapticsPreference>

    fun set(preference: HapticsPreference)
}

/** [HapticsPreferenceStore] in the watch's own SharedPreferences file, [PREFERENCES_NAME]. */
class SharedPreferencesHapticsPreferenceStore(
    private val preferences: SharedPreferences,
) : HapticsPreferenceStore {
    private val current = MutableStateFlow(HapticsPreference.fromStored(preferences.getString(KEY_HAPTICS, null)))

    override val preference: StateFlow<HapticsPreference> = current.asStateFlow()

    override fun set(preference: HapticsPreference) {
        preferences.edit { putString(KEY_HAPTICS, preference.storedName) }
        current.value = preference
    }

    companion object {
        const val PREFERENCES_NAME = "wear_haptics"
        private const val KEY_HAPTICS = "haptics"
    }
}
