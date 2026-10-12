package app.logdate.reminders.e2e

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import app.logdate.feature.onboarding.ui.OnboardingNotificationsScreen
import app.logdate.ui.theme.LogDateTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ReminderOnboardingRuntimeTest {
    @get:Rule val compose = createComposeRule()
    private val koin get() = KoinJavaComponent.getKoin()
    private val flags get() = koin.get<FeatureFlagStore>()
    private val shared get() = koin.get<WatchPreferencesRepository>()
    private var oldFlag = false
    private var oldShared: String? = null
    private var oldHandled: Boolean? = null
    private var advanced = 0

    @Before fun prepare() =
        runBlocking {
            oldFlag = flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)
            val storage = koin.get<KeyValueStorage>()
            oldShared = storage.getString(StampedPreferenceStore.STORAGE_KEY)
            oldHandled = if (storage.contains(HANDLED_KEY)) storage.getBoolean(HANDLED_KEY) else null
            storage.remove(HANDLED_KEY)
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
            oldHandled?.let { storage.putBoolean(HANDLED_KEY, it) } ?: storage.remove(HANDLED_KEY)
        }

    private fun <T> storage(block: suspend () -> T): T {
        val result = CoroutineScope(Dispatchers.IO).async { withTimeout(10_000) { block() } }
        compose.waitUntil(15_000) { result.isCompleted }
        return runBlocking { result.await() }
    }

    private fun open() {
        compose.setContent { LogDateTheme { OnboardingNotificationsScreen(onBack = {}, onNext = { advanced++ }) } }
        compose.waitUntil(10_000) {
            compose
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasText("Reminders"),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test(timeout = 60_000)
    fun aExplicitOptInPrecedesPermissionHandling() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permissionAlreadyGranted =
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        open()
        compose.onNodeWithText("Turn on reminders").performClick()
        storage { shared.observe(SharedPreferenceKey.RemindersEnabled).first { it } }
        compose.waitForIdle()
        if (!permissionAlreadyGranted) {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val allow =
                device.wait(Until.findObject(By.res("com.android.permissioncontroller", "permission_allow_button")), 10_000)
                    ?: error("Expected the system notification permission prompt")
            assertTrue(storage { shared.get(SharedPreferenceKey.RemindersEnabled) })
            assertFalse(storage { koin.get<KeyValueStorage>().getBoolean(HANDLED_KEY) })
            assertEquals(0, advanced)
            allow.click()
            compose.onNodeWithText("Continue").assertIsDisplayed().performClick()
        }
        compose.waitUntil(10_000) { advanced == 1 }
        assertTrue(storage { koin.get<KeyValueStorage>().getBoolean(HANDLED_KEY) })
        assertTrue(storage { shared.get(SharedPreferenceKey.RemindersEnabled) })
        assertFalse(storage { shared.get(SharedPreferenceKey.CapturePlaces) })
    }

    @Test(timeout = 60_000)
    fun bGrantedPermissionDoesNotImplicitlyOptIn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        open()
        compose.onNodeWithText("Turn on reminders").assertIsDisplayed()
        assertFalse(storage { shared.get(SharedPreferenceKey.RemindersEnabled) })
        assertEquals(0, advanced)
        compose.onNodeWithText("Turn on reminders").performClick()
        compose.waitUntil(10_000) { advanced == 1 }
        assertTrue(storage { shared.get(SharedPreferenceKey.RemindersEnabled) })
    }

    @Test(timeout = 60_000)
    fun cNotNowSavesOffDespiteGrantedPermissionAndExistingWatchChoice() {
        storage { shared.set(SharedPreferenceKey.RemindersEnabled, true) }
        open()
        compose.onNodeWithText("Not now").performClick()
        compose.waitUntil(10_000) { advanced == 1 }
        assertFalse(storage { shared.get(SharedPreferenceKey.RemindersEnabled) })
        assertTrue(storage { koin.get<KeyValueStorage>().getBoolean(HANDLED_KEY) })
    }

    @Test(timeout = 60_000)
    fun dFlagOffKeepsTheLegacyRoute() {
        storage { flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, false) }
        compose.setContent { LogDateTheme { OnboardingNotificationsScreen(onBack = {}, onNext = {}) } }
        compose.onNodeWithText("Journal notifications").assertIsDisplayed()
        compose.onNodeWithText("Turn on reminders").assertDoesNotExist()
        assertFalse(storage { shared.get(SharedPreferenceKey.RemindersEnabled) })
    }

    private companion object {
        const val HANDLED_KEY = "onboarding_notifications_handled"
    }
}
