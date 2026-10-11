package app.logdate.wear.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.LogDateConfigDataSource
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.IdentityMintingNotAllowedException
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.peer.link.LinkInstaller
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.link.LinkedDeviceInstaller
import app.logdate.wear.sync.v2.WatchOwnState
import app.logdate.wear.sync.v2.delivery.LinkedDataLayerTransportV2
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import app.logdate.wear.sync.v2.delivery.WatchReceiptApplier
import app.logdate.wear.sync.v2.link.WatchLinkCoordinator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent.getKoin
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Checks account linking against the real watch application and platform crypto bindings. */
@RunWith(AndroidJUnit4::class)
class WearIdentityRuntimeTest {
    @Test
    fun accountInstallerAndCoordinatorResolveFromTheInstalledApp() {
        assertTrue(getKoin().get<LinkInstaller>() is LinkedDeviceInstaller)
        getKoin().get<WatchLinkCoordinator>()
    }

    @Test
    fun deliveryAudienceResolvesFromTheInstalledApp() {
        getKoin().get<WatchDeliveryPeer>()
        getKoin().get<WatchReceiptApplier>()
        val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32)), "phone", "watch", LinkMode.PAIR)
        getKoin().get<LinkedDataLayerTransportV2> { parametersOf(peer) }
    }

    @Test
    fun theWatchCannotMintAnIdentity() =
        runBlocking {
            val identity = getKoin().get<IdentityKeyManager>()
            assertFalse(identity.hasIdentityKey())
            assertFailsWith<IdentityMintingNotAllowedException> { identity.ensureIdentityKey() }
            assertFalse(identity.hasIdentityKey())
        }

    @Test
    fun loadedConfigurationAndOwnedStateAdvertiseAccountLinking() =
        runBlocking {
            withTimeout(10_000) {
                getKoin().get<LogDateConfigDataSource>().awaitConfigurationLoaded()
                assertTrue(
                    WearFeatures.LINK_CLOUD in
                        getKoin()
                            .get<WatchOwnState>()
                            .observe()
                            .first()
                            .features,
                )
            }
        }
}
