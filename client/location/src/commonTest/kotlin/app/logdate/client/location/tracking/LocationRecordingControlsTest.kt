package app.logdate.client.location.tracking

import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationRecordingControlsTest {
    @Test
    fun notificationPauseAndResumePersistOnlyTheRecordingSwitch() =
        runTest {
            val original =
                LocationTrackingSettings(
                    backgroundTrackingEnabled = true,
                    captureMode = LocationCaptureMode.ACTIVE,
                    minimumPersistIntervalMinutes = 12,
                    serverAssistEnabled = true,
                    autoTrackForJournalEntries = false,
                )
            val state = MutableStateFlow(original)
            val repository =
                object : LocationTrackingSettingsRepository {
                    override suspend fun getSettings() = state.value

                    override fun observeSettings() = state

                    override suspend fun updateSettings(settings: LocationTrackingSettings) {
                        state.value = settings
                    }

                    override suspend fun setBackgroundTrackingEnabled(enabled: Boolean) {
                        updateSettings(getSettings().copy(backgroundTrackingEnabled = enabled))
                    }
                }
            LocationRecordingControls(repository).pause()
            assertEquals(original.copy(backgroundTrackingEnabled = false), repository.getSettings())
            assertEquals(false, computeLocationTrackingExecutionDecision(repository.getSettings()).shouldStartActivityAwareTracking)
            LocationRecordingControls(repository).resume()
            assertEquals(original, repository.getSettings())
            assertEquals(true, computeLocationTrackingExecutionDecision(repository.getSettings()).shouldStartActivityAwareTracking)
        }
}
