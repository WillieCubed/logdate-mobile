package app.logdate.wear.di

import app.logdate.client.database.LogDateDatabase
import app.logdate.wear.recording.WearRecorder
import app.logdate.wear.sync.v2.delivery.WatchCaptureDelivery
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import app.logdate.wear.sync.v2.removal.WatchDataWiper
import app.logdate.wear.sync.v2.removal.WatchRemovalEndpoint
import app.logdate.wear.sync.v2.removal.WatchRemovalLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.dsl.module

val wearRemovalModule =
    module {
        single { WatchRemovalLock(get()) }
        single {
            app.logdate.wear.sync.v2.removal
                .WatchRemovalCaches(get(), get(), get(), get(), get(), get(), get())
        }
        single {
            app.logdate.wear.sync.v2.removal
                .WatchRemovalEvents(get(), get())
        }
        single {
            WatchDataWiper(
                get(),
                get(),
                get(
                    org.koin.core.qualifier
                        .named("mainDataStore"),
                ),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        single {
            val database = get<LogDateDatabase>()
            app.logdate.wear.sync.v2.removal
                .WatchRemovalRecovery(
                    get(),
                    get(),
                    get(),
                    get(),
                    pending = { database.syncMetadataDao().getAllPending().size },
                )
        }
        single {
            val peers = get<WatchDeliveryPeer>()
            val delivery = get<WatchCaptureDelivery>()
            val database = get<LogDateDatabase>()
            val recorder = get<WearRecorder>()
            val wiper = get<WatchDataWiper>()
            WatchRemovalEndpoint(
                current = peers::forRemoval,
                lock = get(),
                recording = { recorder.isRecording.value },
                drain = {
                    withTimeoutOrNull(8_000) {
                        do {
                            delivery.deliverIfActive()
                            if (database.syncMetadataDao().getAllPending().isEmpty()) break
                            delay(250)
                        } while (true)
                    }
                },
                pending = { database.syncMetadataDao().getAllPending().size },
                wipe = wiper::wipe,
                forcedWipe = wiper::wipe,
            )
        }
    }
