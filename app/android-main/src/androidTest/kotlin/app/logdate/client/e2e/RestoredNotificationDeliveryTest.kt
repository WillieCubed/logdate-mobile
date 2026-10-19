package app.logdate.client.e2e

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.WorkerParameters
import app.logdate.client.ambient.AmbientPromptNotificationCoordinator
import app.logdate.client.ambient.AmbientPromptWorker
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.recommendation.AmbientPromptCandidate
import app.logdate.client.domain.recommendation.AmbientPromptFamily
import app.logdate.client.domain.recommendation.AmbientPromptHistoryRepository
import app.logdate.client.domain.recommendation.AmbientPromptPayload
import app.logdate.client.domain.recommendation.GenerateAmbientPromptCandidatesUseCase
import app.logdate.client.domain.reminders.PhoneReminderDelivery
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.notifications.LogDateNotificationRegistrar
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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
        val flags =
            object : FeatureFlagStore {
                override fun observe(flag: FeatureFlag) = flowOf(false)

                override suspend fun setEnabled(
                    flag: FeatureFlag,
                    enabled: Boolean,
                ) = Unit
            }
        val history = mockk<AmbientPromptHistoryRepository>(relaxed = true)
        coEvery { history.canShow(candidate) } returns true
        val generate = mockk<GenerateAmbientPromptCandidatesUseCase>()
        coEvery { generate(any()) } returns listOf(candidate)
        val parameters = mockk<WorkerParameters>(relaxed = true)
        every { parameters.inputData } returns Data.EMPTY
        try {
            AmbientPromptWorker(
                context,
                parameters,
                flags,
                mockk<PhoneReminderDelivery>(relaxed = true),
                generate,
                history,
                coordinator,
            ).doWork()
            coVerify(exactly = if (shown) 1 else 0) { history.recordShown(candidate, any()) }
        } finally {
            LogDateNotificationChannelKey.DRAFT_RESCUE.notificationId?.let(manager::cancel)
        }
    }
}
