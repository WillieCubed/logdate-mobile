package app.logdate.di

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.device.crypto.link.BouncyCastleLinkHandshake
import app.logdate.client.domain.watch.StampedPreferenceStore
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.peer.link.PairKeyVault
import app.logdate.client.sync.peer.link.PhoneLinkExchange
import app.logdate.client.sync.peer.link.SecureStoragePairKeyVault
import app.logdate.client.sync.peer.state.PeerCapabilityPresence
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.v2.PhoneStateParts
import app.logdate.client.sync.v2.PhoneStatePublisher
import app.logdate.client.sync.v2.link.AccountKeySource
import app.logdate.client.sync.v2.link.AndroidWatchLinkNotifier
import app.logdate.client.sync.v2.link.CapabilityLinkTargets
import app.logdate.client.sync.v2.link.CloudLinkBundleSource
import app.logdate.client.sync.v2.link.DeviceAccountKeySource
import app.logdate.client.sync.v2.link.LinkBundleSource
import app.logdate.client.sync.v2.link.LinkCodeMinter
import app.logdate.client.sync.v2.link.LinkRequester
import app.logdate.client.sync.v2.link.LinkTargets
import app.logdate.client.sync.v2.link.MessageClientLinkRequester
import app.logdate.client.sync.v2.link.PhoneLinkClientFactory
import app.logdate.client.sync.v2.link.PhoneLinkInitiator
import app.logdate.client.sync.v2.link.PhoneWatchLinkStore
import app.logdate.client.sync.v2.link.WatchLinkNotifier
import app.logdate.client.sync.v2.localNodeIdOrNull
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Koin module for the phone's side of the version 2 watch protocol. */
val phoneWatchSyncV2Module =
    module {
        single {
            val clients = PhoneLinkClientFactory(get(), get())
            LinkCodeMinter(sessions = get(), clientsFor = clients::capture)
        }
        single<AccountKeySource> { DeviceAccountKeySource(get(), get(), get()) }
        single<LinkBundleSource> {
            val flags = get<FeatureFlagStore>()
            CloudLinkBundleSource(get(), get(), get(), get(), v2Enabled = { flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) })
        }
        single {
            val sessions = get<SessionStorage>()
            val watchPreferences = get<StampedPreferenceStore>()
            PhoneStatePublisher(
                v2Enabled = get<FeatureFlagStore>().observe(FeatureFlag.WATCH_EXPERIENCE_V2),
                channel = get(),
                capabilities = get(),
                peerStates = get(),
                sharedPreferences = watchPreferences.observe(),
                adoptWatchPreferences = { shared -> watchPreferences.adopt(shared) },
                features = setOf(WearFeatures.PREFS, WearFeatures.LINK_CLOUD),
                parts = sessions.getSessionFlow().map { PhoneStateParts(cloud = cloudPresence(it)) },
            )
        }
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
                bundles = get(),
                targets = get(),
                requester = get(),
                exchange = get(),
                store = get(),
                notifier = get(),
                localNodeId = { nodeClient.localNodeIdOrNull() },
            )
        }
    }

internal fun cloudPresence(session: UserSession?): CloudPresence =
    if (session == null) CloudPresence.NO_ACCOUNT else CloudPresence.SIGNED_IN
