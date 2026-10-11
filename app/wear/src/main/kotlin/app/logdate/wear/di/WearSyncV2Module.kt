package app.logdate.wear.di

import android.content.Context
import app.logdate.client.datastore.LogDateConfigDataSource
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.device.crypto.link.BouncyCastleLinkHandshake
import app.logdate.client.sync.datalayer.v2.LinkNonceLedger
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.peer.link.LinkInstaller
import app.logdate.client.sync.peer.link.PairKeyVault
import app.logdate.client.sync.peer.link.SecureStoragePairKeyVault
import app.logdate.client.sync.peer.link.WatchLinkExchange
import app.logdate.wear.sync.v2.WatchOwnState
import app.logdate.wear.sync.v2.delivery.LinkedDataLayerTransportV2
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import app.logdate.wear.sync.v2.link.WatchLinkCoordinator
import app.logdate.wear.sync.v2.link.WatchLinkStore
import app.logdate.wear.sync.v2.localNodeIdOrNull
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

/** Koin module for the watch's version 2 sync: linking, delivery and the library window. */
val wearSyncV2Module =
    module {
        single<PairKeyVault> { SecureStoragePairKeyVault(get(), prefix = "watch.link.pair_key.") }
        single { WatchLinkStore(storage = get(), pairKeys = get()) }
        single { WatchLinkExchange(handshake = BouncyCastleLinkHandshake(), nonces = LinkNonceLedger()) }
        single {
            val nodeClient = Wearable.getNodeClient(get<Context>())
            val flags = get<FeatureFlagStore>()
            val config = get<LogDateConfigDataSource>()
            WatchLinkCoordinator(
                exchange = get(),
                store = get(),
                v2Enabled = { flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) },
                localNodeId = { nodeClient.localNodeIdOrNull() },
                installer = get<LinkInstaller>(),
                installScope = get<CoroutineScope>(),
                beforeInstall = { config.awaitConfigurationLoaded() },
            )
        }
        single {
            val nodeClient = Wearable.getNodeClient(get<Context>())
            val flags = get<FeatureFlagStore>()
            WatchDeliveryPeer(
                links = get(),
                sessions = get(),
                config = get(),
                localNodeId = { nodeClient.localNodeIdOrNull() },
                active = { flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) },
            )
        }
        factory { parameters ->
            val context = get<Context>()
            LinkedDataLayerTransportV2(
                peer = parameters.get<PeerDeliveryContext>(),
                peers = get(),
                dataLayer = get(),
                capabilities = Wearable.getCapabilityClient(context),
                channels = Wearable.getChannelClient(context),
                messages = Wearable.getMessageClient(context),
            )
        }
        single { WatchOwnState(links = get<WatchLinkStore>().observe(), features = setOf(WearFeatures.LINK_CLOUD)) }
    }
