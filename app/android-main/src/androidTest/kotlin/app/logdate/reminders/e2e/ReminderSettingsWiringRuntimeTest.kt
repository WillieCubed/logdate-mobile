package app.logdate.reminders.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.StampedPreferenceStore
import app.logdate.client.domain.watch.WatchPreferencesRepository
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.feature.core.settings.ui.RecommendationSettingsScreen
import app.logdate.ui.theme.LogDateTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ReminderSettingsWiringRuntimeTest {
    @get:Rule val compose = createComposeRule()
    private val koin get() = KoinJavaComponent.getKoin()
    private val flags get() = koin.get<FeatureFlagStore>()
    private val shared get() = koin.get<WatchPreferencesRepository>()
    private var oldFlag = false
    private var oldShared: String? = null

    @Before fun prepare() =
        runBlocking {
            oldFlag = flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)
            oldShared = koin.get<KeyValueStorage>().getString(StampedPreferenceStore.STORAGE_KEY)
            shared.set(SharedPreferenceKey.RemindersEnabled, false)
            shared.set(SharedPreferenceKey.CapturePlaces, false)
            flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, true)
        }

    @After fun restore() =
        runBlocking {
            flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, oldFlag)
            val storage = koin.get<KeyValueStorage>()
            oldShared?.let { storage.putString(StampedPreferenceStore.STORAGE_KEY, it) }
                ?: storage.remove(StampedPreferenceStore.STORAGE_KEY)
        }

    private fun <T> storage(block: suspend () -> T): T {
        val result = CoroutineScope(Dispatchers.IO).async { withTimeout(10_000) { block() } }
        compose.waitUntil(15_000) { result.isCompleted }
        return runBlocking { result.await() }
    }

    @Test fun realSettingsRouteWritesThePreferenceAndReceivesWatchChanges() {
        compose.setContent { LogDateTheme { RecommendationSettingsScreen(onBack = {}) } }
        compose.onNodeWithText("At most one reminder a day, on your phone or watch.").assertIsDisplayed()
        compose.onNode(isToggleable()).assertIsOff().performClick()
        storage { shared.observe(SharedPreferenceKey.RemindersEnabled).first { it } }
        assertTrue(storage { shared.get(SharedPreferenceKey.RemindersEnabled) })
        storage { shared.set(SharedPreferenceKey.RemindersEnabled, false) }
        compose.waitForIdle()
        compose.onNode(isToggleable()).assertIsOff()
    }

    @Test fun placesConsentIsSeparateAndDoesNotStartLocationHistory() {
        storage { shared.set(SharedPreferenceKey.RemindersEnabled, true) }
        val locations = koin.get<LocationTrackingSettingsRepository>()
        val historyBefore = storage { locations.getSettings().backgroundTrackingEnabled }
        compose.setContent { LogDateTheme { RecommendationSettingsScreen(onBack = {}) } }
        compose.onNodeWithText("New places").performScrollTo().performClick()
        compose.onNodeWithText("Add places to your moments?").assertIsDisplayed()
        assertFalse(storage { shared.get(SharedPreferenceKey.CapturePlaces) })
        compose.onNodeWithText("Cancel").performClick()
        assertFalse(storage { shared.get(SharedPreferenceKey.CapturePlaces) })
        compose.onNodeWithText("New places").performScrollTo().performClick()
        compose.onNodeWithText("Enable Places").performClick()
        storage { shared.observe(SharedPreferenceKey.CapturePlaces).first { it } }
        assertTrue(storage { shared.get(SharedPreferenceKey.RemindersEnabled) })
        kotlin.test.assertEquals(historyBefore, storage { locations.getSettings().backgroundTrackingEnabled })
    }

    @Test fun notificationPermissionUsesTheSystemPromptAndRefreshesAfterGrant() {
        storage { shared.set(SharedPreferenceKey.RemindersEnabled, true) }
        compose.setContent { LogDateTheme { RecommendationSettingsScreen(onBack = {}) } }
        compose.onNodeWithText("Notification access is off").assertIsDisplayed()
        compose.onNodeWithText("Notifications").performScrollTo().performClick()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val allow =
            device.wait(Until.findObject(By.res("com.android.permissioncontroller", "permission_allow_button")), 10_000)
                ?: error("Expected the actual notification permission prompt")
        allow.click()
        compose.waitForIdle()
        compose.onNodeWithText("Notifications allowed").assertIsDisplayed()
    }

    @Test fun flagOffRetainsTheEstablishedRecommendationSettings() {
        storage { flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, false) }
        compose.setContent { LogDateTheme { RecommendationSettingsScreen(onBack = {}) } }
        compose.onNodeWithText("At most one reminder a day, on your phone or watch.").assertDoesNotExist()
    }
}
