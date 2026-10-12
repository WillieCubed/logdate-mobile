package app.logdate.di

import app.logdate.client.database.LogDateDatabase
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.v2.receipts.MomentReceiptBook
import app.logdate.client.sync.v2.receipts.PhoneMomentSupport
import app.logdate.client.sync.v2.receipts.WatchMomentIngestor
import org.koin.dsl.module

val phoneMomentSyncModule =
    module {
        single {
            val flags = get<FeatureFlagStore>()
            PhoneMomentSupport({ flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) }, get())
        }
        single {
            val support = get<PhoneMomentSupport>()
            val database = get<LogDateDatabase>()
            MomentReceiptBook(database.markedMomentDao(), database.peerDeliveryDao(), get(), get(), support::accepts)
        }
        single {
            val support = get<PhoneMomentSupport>()
            val database = get<LogDateDatabase>()
            WatchMomentIngestor(
                get(),
                database.markedMomentDao(),
                database.peerDeliveryDao(),
                get(),
                get(),
                get(),
                get(),
                support::accepts,
                { get<SyncManager>().sync() },
            )
        }
    }
