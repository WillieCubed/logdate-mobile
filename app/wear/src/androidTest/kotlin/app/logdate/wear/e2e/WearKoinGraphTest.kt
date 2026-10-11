package app.logdate.wear.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.metadata.CloudEligibility
import app.logdate.client.sync.peer.link.LinkInstaller
import app.logdate.wear.sync.v2.link.WatchLinkCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Resolves the watch's version 2 sync graph the way the installed app builds it, so a missing binding
 * fails here rather than on a watch.
 */
@RunWith(AndroidJUnit4::class)
class WearKoinGraphTest {
    private val koin = KoinJavaComponent.getKoin()

    @Test
    fun cloudEngineResolvesAndWaitsForAccountLink() {
        val engine = koin.get<DefaultSyncManager>(named("watchCloud"))

        assertEquals(SyncPausedReason.NEEDS_DEVICE_LINK, runBlocking { engine.getSyncStatus() }.pausedReason)
    }

    @Test
    fun linkBindingsResolveWithoutMintingAnIdentity() {
        koin.get<WatchLinkCoordinator>()
        koin.get<LinkInstaller>()
        koin.get<CloudEligibility>(named("watchCloud"))

        assertFalse(runBlocking { koin.get<IdentityKeyManager>().hasIdentityKey() })
    }
}
