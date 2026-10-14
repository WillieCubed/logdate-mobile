package app.logdate.wear.reminders

import android.content.Context
import android.content.SharedPreferences
import app.logdate.client.domain.reminders.WakingHours
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.PhoneState
import app.logdate.client.sync.peer.state.ReminderHours
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.wear.sync.v2.MemoryKeyValueStorage
import app.logdate.wear.sync.v2.removal.WatchRemovalCaches
import app.logdate.wear.v2.continuity.WatchRecordingContinuity
import app.logdate.wear.v2.haptics.HapticsPreference
import app.logdate.wear.v2.haptics.SharedPreferencesHapticsPreferenceStore
import app.logdate.wear.v2.playback.PlaybackSpeed
import app.logdate.wear.v2.playback.SharedPreferencesPlaybackPreferencesStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class WatchRemovalCachesTest {
    @Test
    fun `removal clears retained and live private state and cannot reschedule reminders`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            try {
                val values =
                    mutableMapOf<String, Any>(
                        "enabled" to true,
                        "last_reminder_at" to 1000L,
                        "speed" to "2",
                        "skip_silence" to true,
                        "haptics" to "essential",
                    )
                val editor = mockk<SharedPreferences.Editor>(relaxed = true)
                val preferences =
                    mockk<SharedPreferences> {
                        every { edit() } returns editor
                        every { getBoolean(any(), any()) } answers { values[firstArg<String>()] as? Boolean ?: secondArg() }
                        every { getLong(any(), any()) } answers { values[firstArg<String>()] as? Long ?: secondArg() }
                        every { getString(any(), any()) } answers { values[firstArg<String>()] as? String ?: secondArg() }
                    }
                every { editor.clear() } answers {
                    values.clear()
                    editor
                }
                every { editor.commit() } returns true
                val context = mockk<Context> { every { getSharedPreferences(any(), any()) } returns preferences }
                val savedHours = MemoryKeyValueStorage()
                val hours = WatchReminderHours(savedHours) { true }
                val document =
                    StateDocument(
                        role = PeerRole.PHONE,
                        writtenAt = 1,
                        phone =
                            PhoneState(
                                reminderHours =
                                    ReminderHours(
                                        8 * 3600,
                                        23 * 3600,
                                    ),
                            ),
                    )
                hours.adopt("phone", document)
                val peers = PeerStateStore().apply { accept("phone", Decoded.Supported(document), PeerRole.PHONE) }
                val local = SharedPreferencesWatchReminderPreferences(preferences)
                val activity = FakeActivityRegistration()
                val work = FakeEveningWork()
                val controller =
                    WatchReminders(
                        local,
                        activity,
                        FakeReminderPermissions(),
                        EveningReminderScheduler(work, FixedClock(berlin(10, 18)), { REMINDER_ZONE }),
                    )
                controller.restore()
                val playback = SharedPreferencesPlaybackPreferencesStore(preferences)
                val haptics = SharedPreferencesHapticsPreferenceStore(preferences)
                val caches =
                    WatchRemovalCaches(
                        context,
                        hours,
                        peers,
                        controller,
                        mockk<WatchRecordingContinuity>(relaxed = true),
                        playback,
                        haptics,
                    )
                caches.clear()
                controller.restore()
                assertFalse(local.enabled.value)
                assertNull(local.lastReminderAt())
                assertNull(local.observeLastReminderAt().first())
                assertFalse(controller.workoutsActive.value)
                assertEquals(1, activity.registrations)
                assertEquals(1, work.ensures)
                assertEquals(WakingHours.Fallback, hours.current)
                assertNull(savedHours.getString(WatchReminderHours.STORAGE_KEY))
                assertNull(peers.latest(PeerRole.PHONE).first())
                assertEquals(PlaybackSpeed.NORMAL, playback.speed.value)
                assertFalse(playback.skipSilence.value)
                assertEquals(HapticsPreference.ALL, haptics.preference.value)
            } finally {
                Dispatchers.resetMain()
            }
        }
}
