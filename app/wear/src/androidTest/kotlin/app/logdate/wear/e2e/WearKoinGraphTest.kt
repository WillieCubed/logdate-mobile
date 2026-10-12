package app.logdate.wear.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.metadata.CloudEligibility
import app.logdate.client.sync.peer.link.LinkInstaller
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.wear.sync.WatchSyncScheduler
import app.logdate.wear.sync.WearDataLayerSyncManager
import app.logdate.wear.sync.v2.delivery.ReleasedToCloud
import app.logdate.wear.sync.v2.delivery.WatchCaptureProtocol
import app.logdate.wear.sync.v2.delivery.WatchNoteDelivery
import app.logdate.wear.sync.v2.delivery.WatchNotesV2
import app.logdate.wear.sync.v2.link.WatchLinkCoordinator
import app.logdate.wear.sync.v2.media.GoogleMediaRangeSource
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

/**
 * Resolves the watch's version 2 sync graph the way the installed app builds it, so a missing binding
 * fails here rather than on a watch.
 */
@RunWith(AndroidJUnit4::class)
class WearKoinGraphTest {
    private val koin = KoinJavaComponent.getKoin()

    @Test
    fun mediaSourceResolvesForAnExactCapturedExchange() {
        val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
        kotlin.test.assertNotNull(koin.get<GoogleMediaRangeSource> { parametersOf(peer) })
    }

    @Test
    fun playbackUsesTheVerifiedPhoneResolverAndServiceSource() {
        kotlin.test.assertTrue(
            koin.get<app.logdate.wear.playback.WearSyncedAudioResolver>() is app.logdate.wear.playback.WindowPhoneAudioResolver,
        )
        kotlin.test.assertTrue(
            koin.get<app.logdate.client.media.audio.AudioPlaybackSourceFactory>() is app.logdate.wear.sync.v2.media.WatchAudioDataSources,
        )
        koin.get<app.logdate.wear.sync.v2.media.WatchPhoneMedia>()
    }

    @Test
    fun cloudEngineResolvesAndWaitsForAccountLink() {
        val engine = koin.get<DefaultSyncManager>(named("watchCloud"))

        assertEquals(SyncPausedReason.NEEDS_DEVICE_LINK, runBlocking { engine.getSyncStatus() }.pausedReason)
    }

    @Test
    fun captureDeliveryAndSchedulingResolveThroughTheInstalledGraph() {
        assertSame(koin.get<WatchNoteDelivery>(), koin.get<WatchNotesV2>())
        koin.get<WearDataLayerSyncManager>()
        koin.get<WatchSyncScheduler>()
        assertFalse(runBlocking { koin.get<WatchCaptureProtocol>().active() })
    }

    @Test
    fun libraryWindowResolvesAndRemainsInactiveBeforeRollout() =
        runBlocking {
            koin.get<app.logdate.wear.sync.v2.window.WindowMembershipStore>()
            koin.get<app.logdate.wear.sync.v2.window.WindowApplier>()
            koin.get<app.logdate.wear.sync.v2.window.WindowItemSource>()
            assertFalse(koin.get<app.logdate.wear.sync.v2.window.WindowProtocol>().active())
            kotlin.test.assertNull(koin.get<app.logdate.wear.sync.v2.window.WindowReconciler>().resync())
        }

    @Test
    fun linkBindingsResolveWithoutMintingAnIdentity() {
        koin.get<WatchLinkCoordinator>()
        koin.get<LinkInstaller>()
        assertSame(koin.get<ReleasedToCloud>(), koin.get<CloudEligibility>(named("watchCloud")))

        assertFalse(runBlocking { koin.get<IdentityKeyManager>().hasIdentityKey() })
    }
}
