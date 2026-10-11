package app.logdate.di

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.device.crypto.link.BouncyCastleLinkHandshake
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.link.PairKeyVault
import app.logdate.client.sync.peer.link.PhoneLinkExchange
import app.logdate.client.sync.peer.link.SecureStoragePairKeyVault
import app.logdate.client.sync.peer.state.PeerCapabilityPresence
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.v2.link.AndroidWatchLinkNotifier
import app.logdate.client.sync.v2.link.CapabilityLinkTargets
import app.logdate.client.sync.v2.link.LinkRequester
import app.logdate.client.sync.v2.link.LinkTargets
import app.logdate.client.sync.v2.link.MessageClientLinkRequester
import app.logdate.client.sync.v2.link.PhoneLinkInitiator
import app.logdate.client.sync.v2.link.PhoneWatchLinkStore
import app.logdate.client.sync.v2.link.WatchLinkNotifier
import app.logdate.client.sync.v2.localNodeIdOrNull
import com.google.android.gms.wearable.Wearable
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Koin module for the phone's side of the version 2 watch protocol. */
val phoneWatchSyncV2Module =
    module {
        single<PairKeyVault> { SecureStoragePairKeyVault(get(), prefix = "phone.watch_link.pair_key.") }
        single { PhoneWatchLinkStore(storage = get(), pairKeys = get()) }
        single { PhoneLinkExchange(BouncyCastleLinkHandshake()) }
        single<LinkRequester> { MessageClientLinkRequester(Wearable.getMessageClient(androidContext())) }
        single<LinkTargets> { CapabilityLinkTargets(Wearable.getCapabilityClient(androidContext())) }
        single<WatchLinkNotifier> { AndroidWatchLinkNotifier(androidContext()) }
        single {
            val nodeClient = Wearable.getNodeClient(androidContext())
            PhoneLinkInitiator(
                v2Enabled = get<FeatureFlagStore>().observe(FeatureFlag.WATCH_EXPERIENCE_V2),
                watchCapable = get<PeerCapabilityPresence>().observe(WearProtocol.WATCH_CAPABILITY_V2),
                watchDocuments = get<PeerStateStore>().documents(PeerRole.WATCH),
                targets = get(),
                requester = get(),
                exchange = get(),
                store = get(),
                notifier = get(),
                localNodeId = { nodeClient.localNodeIdOrNull() },
            )
        }
    }
