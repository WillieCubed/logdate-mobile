package app.logdate.wear.sync.v2.removal

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.clearAllLogDateTables
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.wear.recording.WearRecorder
import app.logdate.wear.reminders.ActivityRegistration
import app.logdate.wear.reminders.EveningReminderScheduler
import app.logdate.wear.v2.playback.MomentPlayer
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Erases only this watch's retained copy. No cloud deletion or phone journal mutation is issued. */
class WatchDataWiper(
    private val context: Context,
    private val database: LogDateDatabase,
    private val preferences: DataStore<Preferences>,
    private val secure: SecureStorage,
    private val sessions: SessionStorage,
    private val lock: WatchRemovalLock,
    private val activity: ActivityRegistration,
    private val evening: EveningReminderScheduler,
    private val identity: IdentityKeyManager,
    private val owner: CanonicalOwnerProvider,
    private val mediaKeys: MediaPayloadKeyProvider,
    private val recorder: WearRecorder,
    private val player: MomentPlayer,
    private val caches: WatchRemovalCaches,
) {
    private val mutex = Mutex()

    suspend fun wipe() =
        withContext(NonCancellable + Dispatchers.IO) {
            mutex.withLock {
                if (lock.state()?.startsWith("removed") == true) return@withLock
                lock.erase {
                    val state = lock.state()
                    if (state?.startsWith("prepared:") == true || state?.startsWith("wiping:") == true) {
                        check(database.syncMetadataDao().getAllPending().isEmpty()) { "Watch captures are still waiting for sync" }
                    }
                    lock.mark()
                    check(lock.removed()) { "The watch removal marker was not retained" }
                    activity.unregister()
                    evening.cancel()
                    val work = WorkManager.getInstance(context)
                    work.enqueueUniqueWork(
                        "watch_secure_removal",
                        ExistingWorkPolicy.KEEP,
                        OneTimeWorkRequestBuilder<WatchRemovalWorker>().build(),
                    )
                    val unfinished = WorkQuery.fromStates(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED))
                    work.getWorkInfos(unfinished).get().filter { WatchRemovalWorker::class.java.name !in it.tags }.forEach {
                        work.cancelWorkById(it.id).result.get()
                    }
                    recorder.discard()
                    withContext(Dispatchers.Main.immediate) { player.stop() }
                    sessions.clearSession()
                    caches.clear()
                    database.clearAllLogDateTables()
                    clearWatchFiles(context.filesDir, context.cacheDir)
                    preferences.edit { values ->
                        val rolloutKey = booleanPreferencesKey(FeatureFlag.WATCH_EXPERIENCE_V2.key)
                        val rollout = values[rolloutKey]
                        values.clear()
                        if (rollout != null) values[rolloutKey] = rollout
                    }
                    identity.clearIdentityKey()
                    mediaKeys.clearCachedKey()
                    owner.resetForDeviceWipe()
                    // Keep the empty database readable; every account, identity, media and pair key is removed.
                    secure
                        .observeAll()
                        .first()
                        .keys
                        .filter {
                            it != "db_encryption_key" && it != WatchRemovalLock.KEY
                        }.forEach { secure.remove(it) }
                    clearDataItems()
                    lock.finish()
                }
            }
        }

    private suspend fun clearDataItems() {
        val data = Wearable.getDataClient(context)
        val own =
            Wearable
                .getNodeClient(context)
                .localNode
                .await()
                .id
        val items = data.dataItems.await()
        try {
            items.filter { it.uri.host == own }.forEach { data.deleteDataItems(it.uri).await() }
        } finally {
            items.release()
        }
    }
}
