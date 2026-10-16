package app.logdate.wear.v2.settings

import app.cash.turbine.test
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.PermissionType
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.datalayer.v2.WearProtocolGate
import app.logdate.wear.reminders.EveningReminderScheduler
import app.logdate.wear.reminders.FakeActivityRegistration
import app.logdate.wear.reminders.FakeEveningWork
import app.logdate.wear.reminders.FakeReminderPermissions
import app.logdate.wear.reminders.FakeReminderPreferences
import app.logdate.wear.reminders.WatchReminders
import app.logdate.wear.sync.ReachablePhone
import app.logdate.wear.v2.haptics.HapticsPreference
import app.logdate.wear.v2.haptics.HapticsPreferenceStore
import app.logdate.wear.v2.phone.HandoffResult
import app.logdate.wear.v2.phone.PhoneHandoff
import app.logdate.wear.v2.phone.PhoneLink
import app.logdate.wear.v2.phone.PhoneLinkRepository
import app.logdate.wear.v2.today.HandoffState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WatchSettingsViewModelTest {
    private val syncStatus =
        MutableStateFlow(SyncStatus(isEnabled = true, lastSyncTime = null, pendingUploads = 0, isSyncing = false, hasErrors = false))
    private val syncManager = mockk<SyncManager>(relaxed = true) { every { syncStatusFlow } returns syncStatus }
    private val phone = MutableStateFlow<ReachablePhone?>(ReachablePhone("Pixel"))
    private val haptics = FakeHapticsStore()
    private val locationSettings = MutableStateFlow(LocationTrackingSettings())
    private val locationRepository = mockk<LocationTrackingSettingsRepository>(relaxed = true)
    private val permissionManager = mockk<PermissionManager>(relaxed = true)
    private val phoneLink = MutableStateFlow<PhoneLink>(PhoneLink.Connected(nodeId = "phone-node", phoneName = "Pixel"))
    private val phoneLinks = mockk<PhoneLinkRepository> { every { observe() } returns phoneLink }
    private val phoneFeatures = MutableStateFlow(emptySet<String>())
    private val phoneHandoff = mockk<PhoneHandoff>()
    private val reminderPreferences = FakeReminderPreferences(enabled = false)
    private val activityRegistration = FakeActivityRegistration()
    private val reminderPermissions = FakeReminderPermissions()
    private val watchReminders =
        WatchReminders(reminderPreferences, activityRegistration, reminderPermissions, EveningReminderScheduler(FakeEveningWork()))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { locationRepository.observeSettings() } returns locationSettings
        coEvery { locationRepository.getSettings() } answers { locationSettings.value }
        coEvery { locationRepository.updateSettings(any()) } answers { locationSettings.value = firstArg() }
        every { permissionManager.isPermissionGranted(PermissionType.LOCATION) } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `healthy sync shows no status`() =
        runTest {
            viewModel().uiState.test { assertEquals(WatchStatus.Healthy, expectMostRecentItem().status) }
        }

    @Test
    fun `waiting moments with the phone away say so as it happens`() =
        runTest {
            viewModel().uiState.test {
                phone.value = null
                syncStatus.value = syncStatus.value.copy(pendingUploads = 3)

                assertEquals(WatchStatus.WaitingForPhone(3), expectMostRecentItem().status)
            }
        }

    @Test
    fun `a failing send can be retried once at a time`() =
        runTest {
            val finish = CompletableDeferred<SyncResult>()
            coEvery { syncManager.fullSync() } coAnswers { finish.await() }
            syncStatus.value = syncStatus.value.copy(pendingUploads = 2, hasErrors = true)
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(WatchStatus.SendFailing(2), expectMostRecentItem().status)
                viewModel.retrySync()
                viewModel.retrySync()
                assertTrue(expectMostRecentItem().retrying)
                finish.complete(SyncResult(success = true, uploadedItems = 2))
                assertEquals(false, expectMostRecentItem().retrying)
            }
            coVerify(exactly = 1) { syncManager.fullSync() }
        }

    @Test
    fun `a watch whose account link was lost asks to be set up from the phone`() =
        runTest {
            viewModel().uiState.test {
                syncStatus.value = syncStatus.value.copy(pausedReason = SyncPausedReason.NEEDS_DEVICE_LINK)

                assertEquals(WatchStatus.NeedsDeviceLink, expectMostRecentItem().status)
            }
        }

    @Test
    fun `a failing backup can be retried`() =
        runTest {
            coEvery { syncManager.fullSync() } returns SyncResult(success = true, uploadedItems = 1)
            syncStatus.value =
                syncStatus.value.copy(
                    pendingUploads = 1,
                    hasErrors = true,
                    lastError = SyncError(SyncErrorType.SERVER_ERROR, "The server rejected the capture"),
                )
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(WatchStatus.BackupFailing(1), expectMostRecentItem().status)
                viewModel.retrySync()
                expectMostRecentItem()
            }
            coVerify(exactly = 1) { syncManager.fullSync() }
        }

    @Test
    fun `haptics follow the store and a choice is saved`() =
        runTest {
            val viewModel = viewModel()

            viewModel.uiState.test {
                viewModel.setHaptics(HapticsPreference.ESSENTIAL)

                assertEquals(HapticsPreference.ESSENTIAL, expectMostRecentItem().haptics)
            }
            assertEquals(HapticsPreference.ESSENTIAL, haptics.preference.value)
        }

    @Test
    fun `places are on only with the setting and location access`() =
        runTest {
            every { permissionManager.isPermissionGranted(PermissionType.LOCATION) } returns false

            viewModel().uiState.test { assertEquals(false, expectMostRecentItem().places) }
        }

    @Test
    fun `turning places on waits for location access when it is missing`() =
        runTest {
            locationSettings.value = LocationTrackingSettings(autoTrackForJournalEntries = false)
            every { permissionManager.isPermissionGranted(PermissionType.LOCATION) } returns false
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertTrue(viewModel.placesNeedPermission())
                viewModel.onLocationPermissionResult(granted = true)

                assertEquals(true, expectMostRecentItem().places)
            }
        }

    @Test
    fun `turning places off saves it`() =
        runTest {
            viewModel().setPlaces(false)

            assertEquals(false, locationSettings.value.autoTrackForJournalEntries)
        }

    @Test
    fun `more on your phone needs the phone's settings link and a connected phone`() =
        runTest {
            viewModel().uiState.test {
                assertNull(expectMostRecentItem().phoneSettingsNodeId)
                phoneFeatures.value = setOf(WearFeatures.PHONE_SETTINGS_LINK)
                assertEquals("phone-node", expectMostRecentItem().phoneSettingsNodeId)
                phoneLink.value = PhoneLink.NotConnected
                assertNull(expectMostRecentItem().phoneSettingsNodeId)
            }
        }

    @Test
    fun `opening the phone's watch page reports when it was sent`() =
        runTest {
            phoneFeatures.value = setOf(WearFeatures.PHONE_SETTINGS_LINK)
            coEvery { phoneHandoff.open(WatchSettingsViewModel.PHONE_WATCH_SETTINGS_LINK, "phone-node") } returns HandoffResult.SENT
            val viewModel = viewModel()

            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.openPhoneSettings()

                assertEquals(HandoffState.SENT, expectMostRecentItem().handoff)
            }
        }

    @Test
    fun `reminders show their switch and what they cover`() =
        runTest {
            reminderPreferences.setEnabled(true)
            watchReminders.restore()

            viewModel().uiState.test {
                assertEquals(RemindersSetting(enabled = true, afterWorkouts = true), expectMostRecentItem().reminders)
            }
        }

    @Test
    fun `turning reminders on asks for permissions first when they are missing`() =
        runTest {
            reminderPermissions.notifications = false
            reminderPermissions.activityRecognition = false
            val viewModel = viewModel()

            assertTrue(viewModel.remindersNeedPermissions())
            reminderPermissions.notifications = true
            reminderPermissions.activityRecognition = true
            viewModel.onReminderPermissionsResult()

            assertEquals(true, reminderPreferences.enabled.value)
            assertEquals(1, activityRegistration.registrations)
        }

    @Test
    fun `refused permissions leave reminders off`() =
        runTest {
            reminderPermissions.notifications = false
            val viewModel = viewModel()

            viewModel.onReminderPermissionsResult()

            assertEquals(false, reminderPreferences.enabled.value)
        }

    @Test
    fun `turning reminders off saves it and stops listening`() =
        runTest {
            reminderPreferences.setEnabled(true)

            viewModel().setReminders(false)

            assertEquals(false, reminderPreferences.enabled.value)
            assertEquals(1, activityRegistration.unregistrations)
        }

    @Test
    fun `unsupported workout monitoring is not advertised as active`() =
        runTest {
            activityRegistration.supported = false
            reminderPreferences.setEnabled(true)
            watchReminders.restore()
            viewModel().uiState.test {
                assertEquals(RemindersSetting(enabled = true, afterWorkouts = false), expectMostRecentItem().reminders)
            }
        }

    @Test
    fun `revoked notifications are reflected when settings resume`() =
        runTest {
            reminderPreferences.setEnabled(true)
            watchReminders.restore()
            val model = viewModel()
            model.uiState.test {
                assertTrue(expectMostRecentItem().reminders.enabled)
                reminderPermissions.notifications = false
                model.onResume()
                assertEquals(false, expectMostRecentItem().reminders.enabled)
                assertTrue(reminderPreferences.enabled.value)
            }
        }

    @Test
    fun `without physical activity reminders still come in the evening`() =
        runTest {
            reminderPermissions.activityRecognition = false
            val viewModel = viewModel()

            viewModel.uiState.test {
                viewModel.onReminderPermissionsResult()

                assertEquals(RemindersSetting(enabled = true, afterWorkouts = false), expectMostRecentItem().reminders)
            }
            assertEquals(0, activityRegistration.registrations)
        }

    private fun viewModel() =
        WatchSettingsViewModel(
            syncManager = syncManager,
            phoneReachability = { phone },
            haptics = haptics,
            locationSettings = locationRepository,
            permissionManager = permissionManager,
            phoneLinks = phoneLinks,
            protocolGate = WearProtocolGate(flowOf(true), flowOf(true), phoneFeatures),
            phoneHandoff = phoneHandoff,
            reminders = watchReminders,
            reminderPermissions = reminderPermissions,
        )

    private class FakeHapticsStore : HapticsPreferenceStore {
        override val preference = MutableStateFlow(HapticsPreference.ALL)

        override fun set(preference: HapticsPreference) {
            this.preference.value = preference
        }
    }
}
