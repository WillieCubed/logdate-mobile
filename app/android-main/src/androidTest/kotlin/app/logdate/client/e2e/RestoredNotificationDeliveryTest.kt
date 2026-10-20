package app.logdate.client.e2e

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.WorkerParameters
import app.logdate.client.ambient.AmbientPromptNotificationCoordinator
import app.logdate.client.ambient.AmbientPromptWorker
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.recommendation.AmbientPromptCandidate
import app.logdate.client.domain.recommendation.AmbientPromptFamily
import app.logdate.client.domain.recommendation.AmbientPromptHistoryRepository
import app.logdate.client.domain.recommendation.AmbientPromptPayload
import app.logdate.client.domain.recommendation.DefaultAmbientPromptHistoryRepository
import app.logdate.client.domain.recommendation.GenerateAmbientPromptCandidatesUseCase
import app.logdate.client.domain.reminders.PhoneReminderDelivery
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.notifications.LogDateNotificationRegistrar
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

/** The host selects each method after configuring the dedicated emulator installation's permission. */
@RunWith(AndroidJUnit4::class)
class RestoredNotificationDeliveryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun requireHostSequence() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("restorePermissionAcceptance") == "true")
    }

    @Test
    fun concurrent_recreated_workers_post_once_with_durable_history() =
        runBlocking {
            assumeTrue(InstrumentationRegistry.getArguments().getString("notificationDedupeAcceptance") == "true")
            require(android.os.Build.HARDWARE == "ranchu" || android.os.Build.HARDWARE == "goldfish")
            require(context.packageName.endsWith(".debug"))
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val permission = Manifest.permission.POST_NOTIFICATIONS
            val originalPermission = ContextCompat.checkSelfPermission(context, permission)
            val file = File(context.noBackupFilesDir, "reminder-acceptance-${Uuid.random()}.preferences_pb")
            val job = SupervisorJob()
            val reloadJob = SupervisorJob()
            val scope = CoroutineScope(job + Dispatchers.IO)
            val retained =
                DefaultAmbientPromptHistoryRepository(
                    DataStoreKeyValueStorage(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })),
                )
            val history =
                object : AmbientPromptHistoryRepository by retained {
                    override suspend fun canShow(candidate: AmbientPromptCandidate): Boolean {
                        val eligible = retained.canShow(candidate)
                        yield()
                        return eligible
                    }
                }
            val candidate = draft()
            val coordinator = spyk(AmbientPromptNotificationCoordinator(context))
            try {
                if (originalPermission !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    automation.grantRuntimePermission(context.packageName, permission)
                }
                LogDateNotificationRegistrar(context).registerChannel(LogDateNotificationChannelKey.DRAFT_RESCUE)
                val immediate = async { worker(candidate, history, coordinator).doWork() }
                val periodic = async { worker(candidate, history, coordinator).doWork() }
                immediate.await()
                periodic.await()
                verify(exactly = 1) { coordinator.post(candidate) }
                assertFalse(retained.canShow(candidate))
                job.cancelAndJoin()
                val reloaded =
                    DefaultAmbientPromptHistoryRepository(
                        DataStoreKeyValueStorage(
                            PreferenceDataStoreFactory.create(scope = CoroutineScope(reloadJob + Dispatchers.IO), produceFile = { file }),
                        ),
                    )
                // A second repository must observe the same durable suppression, not a worker-local cache.
                assertFalse(reloaded.canShow(candidate))
                assertEquals(1, manager.activeNotifications.count { it.id == LogDateNotificationChannelKey.DRAFT_RESCUE.notificationId })
            } finally {
                restoreFixtureState(
                    { LogDateNotificationChannelKey.DRAFT_RESCUE.notificationId?.let(manager::cancel) },
                    {
                        job.cancelAndJoin()
                        reloadJob.cancelAndJoin()
                        check(!file.exists() || file.delete())
                    },
                )
            }
        }

    @Test
    fun denied_permission_preserves_reminder_history() =
        runBlocking {
            assertEquals(
                PackageManager.PERMISSION_DENIED,
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS),
            )
            LogDateNotificationRegistrar(context).registerChannel(LogDateNotificationChannelKey.DRAFT_RESCUE)
            verifyDelivery(draft(), shown = false)
        }

    @Test
    fun granted_permission_records_delivery_once() =
        runBlocking {
            assertEquals(
                PackageManager.PERMISSION_GRANTED,
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS),
            )
            LogDateNotificationRegistrar(context).registerChannel(LogDateNotificationChannelKey.DRAFT_RESCUE)
            verifyDelivery(draft(), shown = true)
        }

    @Test
    fun blocked_channel_preserves_reminder_history() =
        runBlocking {
            assertEquals(
                PackageManager.PERMISSION_GRANTED,
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS),
            )
            val channelId = "restore-blocked-${Uuid.random()}"
            try {
                manager.createNotificationChannel(
                    NotificationChannel(channelId, "Synthetic blocked channel", NotificationManager.IMPORTANCE_NONE),
                )
                val allowed = LogDateNotificationRegistrar(context).canPostToChannel(channelId)
                assertFalse(allowed)
                val coordinator = mockk<AmbientPromptNotificationCoordinator>()
                every { coordinator.post(any()) } returns allowed
                verifyDelivery(draft(), shown = false, coordinator)
            } finally {
                manager.deleteNotificationChannel(channelId)
                assertEquals(null, manager.getNotificationChannel(channelId))
            }
        }

    private fun draft() =
        AmbientPromptCandidate(
            AmbientPromptFamily.DRAFT_RESCUE,
            1,
            "restore-permission-fixture",
            AmbientPromptPayload.DraftRescue(Uuid.random()),
        )

    private suspend fun verifyDelivery(
        candidate: AmbientPromptCandidate,
        shown: Boolean,
        coordinator: AmbientPromptNotificationCoordinator = AmbientPromptNotificationCoordinator(context),
    ) {
        val history = mockk<AmbientPromptHistoryRepository>(relaxed = true)
        coEvery { history.canShow(candidate) } returns true
        try {
            worker(candidate, history, coordinator).doWork()
            coVerify(exactly = if (shown) 1 else 0) { history.recordShown(candidate, any()) }
        } finally {
            LogDateNotificationChannelKey.DRAFT_RESCUE.notificationId?.let(manager::cancel)
        }
    }

    private fun worker(
        candidate: AmbientPromptCandidate,
        history: AmbientPromptHistoryRepository,
        coordinator: AmbientPromptNotificationCoordinator,
    ): AmbientPromptWorker {
        val flags =
            object : FeatureFlagStore {
                override fun observe(flag: FeatureFlag) = flowOf(false)

                override suspend fun setEnabled(
                    flag: FeatureFlag,
                    enabled: Boolean,
                ) = Unit
            }
        val generate = mockk<GenerateAmbientPromptCandidatesUseCase>()
        coEvery { generate(any()) } returns listOf(candidate)
        val parameters = mockk<WorkerParameters>(relaxed = true)
        every { parameters.inputData } returns Data.EMPTY
        return AmbientPromptWorker(
            context,
            parameters,
            flags,
            mockk<PhoneReminderDelivery>(relaxed = true),
            generate,
            history,
            coordinator,
        )
    }
}
