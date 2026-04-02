package app.logdate.client.watch

import android.app.Activity
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WatchCompanionAssociationManagerTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val request = mockk<AssociationRequest>(relaxed = true)

    @Test
    fun `begin association delegates to companion client and enters pending state`() =
        scope.runTest {
            val companionClient = FakeCompanionDeviceClient()
            val manager =
                DefaultWatchCompanionAssociationManager(
                    companionDeviceClient = companionClient,
                    applicationScope = scope,
                    associationRequestFactory = FakeWatchAssociationRequestFactory(request),
                )
            val launcher = RecordingIntentSenderLauncher()
            manager.attachLauncher(launcher)
            advanceUntilIdle()

            manager.beginAssociation()

            assertEquals(1, companionClient.requests.size)
            assertEquals(WatchAssociationSnapshot.Pending, manager.observeAssociationState().value)
            assertEquals(request, companionClient.requests.single())
        }

    @Test
    fun `association callback updates state and launches system UI`() =
        scope.runTest {
            val companionClient = FakeCompanionDeviceClient()
            val manager =
                DefaultWatchCompanionAssociationManager(
                    companionDeviceClient = companionClient,
                    applicationScope = scope,
                    associationRequestFactory = FakeWatchAssociationRequestFactory(request),
                )
            val launcher = RecordingIntentSenderLauncher()
            manager.attachLauncher(launcher)
            advanceUntilIdle()

            manager.beginAssociation()
            companionClient.callback?.onAssociationPending(mockk<IntentSender>(relaxed = true))
            companionClient.callback?.onAssociationCreated(
                mockAssociationInfo(
                    displayName = "Pixel Watch",
                ),
            )

            assertEquals(1, launcher.launchCount)
            assertEquals(
                WatchAssociationSnapshot.Associated(
                    displayName = "Pixel Watch",
                ),
                manager.observeAssociationState().value,
            )
        }

    @Test
    fun `canceled association returns to unassociated state`() =
        scope.runTest {
            val companionClient = FakeCompanionDeviceClient(
                currentAssociation = WatchAssociationSnapshot.Unassociated,
            )
            val manager =
                DefaultWatchCompanionAssociationManager(
                    companionDeviceClient = companionClient,
                    applicationScope = scope,
                    associationRequestFactory = FakeWatchAssociationRequestFactory(request),
                )
            manager.attachLauncher(RecordingIntentSenderLauncher())
            advanceUntilIdle()

            manager.beginAssociation()
            manager.onAssociationFlowResult(Activity.RESULT_CANCELED)
            advanceUntilIdle()

            assertEquals(WatchAssociationSnapshot.Unassociated, manager.observeAssociationState().value)
        }

    @Test
    fun `unsupported devices stay unsupported`() =
        scope.runTest {
            val companionClient = FakeCompanionDeviceClient(isSupported = false)
            val manager =
                DefaultWatchCompanionAssociationManager(
                    companionDeviceClient = companionClient,
                    applicationScope = scope,
                    associationRequestFactory = FakeWatchAssociationRequestFactory(request),
                )

            advanceUntilIdle()

            manager.beginAssociation()

            assertEquals(WatchAssociationSnapshot.Unsupported, manager.observeAssociationState().value)
            assertTrue(companionClient.requests.isEmpty())
        }

    private class FakeCompanionDeviceClient(
        private val isSupported: Boolean = true,
        private val currentAssociation: WatchAssociationSnapshot =
            if (isSupported) {
                WatchAssociationSnapshot.Unassociated
            } else {
                WatchAssociationSnapshot.Unsupported
            },
    ) : CompanionDeviceClient {
        val requests = mutableListOf<AssociationRequest>()
        var callback: CompanionDeviceManager.Callback? = null

        override fun isSupported(): Boolean = isSupported

        override suspend fun currentAssociation(): WatchAssociationSnapshot = currentAssociation

        override fun associate(
            request: AssociationRequest,
            callback: CompanionDeviceManager.Callback,
        ) {
            requests += request
            this.callback = callback
        }
    }

    private class FakeWatchAssociationRequestFactory(
        private val request: AssociationRequest,
    ) : WatchAssociationRequestFactory {
        override fun createRequest(): AssociationRequest = request
    }

    private fun mockAssociationInfo(
        displayName: String,
    ): AssociationInfo =
        mockk {
            every { this@mockk.displayName } returns displayName
            every { this@mockk.deviceMacAddress } returns null
        }

    private class RecordingIntentSenderLauncher : ActivityResultLauncher<IntentSenderRequest>() {
        var launchCount: Int = 0

        override fun launch(
            input: IntentSenderRequest,
            options: ActivityOptionsCompat?,
        ) {
            launchCount++
        }

        override fun unregister() {
        }

        override fun getContract(): ActivityResultContract<IntentSenderRequest, *> =
            throw UnsupportedOperationException("Not needed for this test")
    }
}
