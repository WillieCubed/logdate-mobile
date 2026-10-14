package app.logdate.feature.core.settings.ui.watch.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchAudioDownloadPolicy
import app.logdate.client.domain.watch.WatchPreferencesRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WatchCompanionPreferences(
    val days: Int = 14,
    val onThisDay: Boolean = true,
    val rewind: Boolean = true,
    val places: Boolean = false,
    val moodAfterRecording: Boolean = true,
    val audioDownloads: WatchAudioDownloadPolicy = WatchAudioDownloadPolicy.WIFI_OR_CHARGING,
)

class WatchCompanionPreferencesViewModel(
    private val preferences: WatchPreferencesRepository,
) : ViewModel() {
    val state: StateFlow<WatchCompanionPreferences?> =
        combine(
            preferences.observe(SharedPreferenceKey.WindowDays),
            preferences.observe(SharedPreferenceKey.WindowOnThisDay),
            preferences.observe(SharedPreferenceKey.WindowRewind),
            preferences.observe(SharedPreferenceKey.CapturePlaces),
            preferences.observe(SharedPreferenceKey.MoodAfterRecording),
            { days, onThisDay, rewind, places, mood -> WatchCompanionPreferences(days, onThisDay, rewind, places, mood) },
        ).let { base ->
            combine(base, preferences.observe(SharedPreferenceKey.AudioDownloadPolicy)) { state, policy ->
                state.copy(audioDownloads = WatchAudioDownloadPolicy.fromStored(policy))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _saveFailed = MutableStateFlow(false)
    val saveFailed = _saveFailed.asStateFlow()

    fun <T> set(
        key: SharedPreferenceKey<T>,
        value: T,
    ) {
        if (_saving.value) return
        _saving.value = true
        _saveFailed.value = false
        viewModelScope.launch {
            try {
                _saveFailed.value = !key.isAllowed(value) || !preferences.setAndVerify(key, value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Napier.e("Could not save watch preference ${key.name}", error)
                _saveFailed.value = true
            } finally {
                _saving.value = false
            }
        }
    }
}
