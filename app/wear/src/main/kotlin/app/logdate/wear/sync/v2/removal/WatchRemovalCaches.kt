package app.logdate.wear.sync.v2.removal

import android.content.Context
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.wear.presentation.camera.WearRemoteCameraDeviceStore
import app.logdate.wear.presentation.recording.SharedPreferencesRecordingHintStore
import app.logdate.wear.reminders.SharedPreferencesActivityTransitionStore
import app.logdate.wear.reminders.WatchReminderHours
import app.logdate.wear.reminders.WatchReminders
import app.logdate.wear.v2.continuity.WatchRecordingContinuity
import app.logdate.wear.v2.haptics.HapticsPreferenceStore
import app.logdate.wear.v2.permissions.SharedPreferencesPermissionPromptStore
import app.logdate.wear.v2.playback.PlaybackPreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Clears both persistent per-watch state and the live objects that could otherwise reuse it. */
class WatchRemovalCaches(
    private val context: Context,
    private val hours: WatchReminderHours,
    private val peers: PeerStateStore,
    private val reminders: WatchReminders,
    private val continuity: WatchRecordingContinuity,
    private val playback: PlaybackPreferencesStore,
    private val haptics: HapticsPreferenceStore,
) {
    suspend fun clear() {
        reminders.clearForRemoval()
        hours.clearForRemoval()
        peers.clear()
        withContext(Dispatchers.Main.immediate) {
            continuity.clearForRemoval()
            playback.clearForRemoval()
            haptics.clearForRemoval()
            WearRemoteCameraDeviceStore.clearForRemoval()
        }
        listOf(
            SharedPreferencesActivityTransitionStore.PREFERENCES_NAME,
            SharedPreferencesPermissionPromptStore.PREFERENCES_NAME,
            SharedPreferencesRecordingHintStore.PREFERENCES_NAME,
            "wear_onboarding",
            "wear_settings",
        ).forEach { name ->
            check(
                context
                    .getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit(),
            ) {
                "The removed watch's local preferences could not be cleared"
            }
        }
    }
}
