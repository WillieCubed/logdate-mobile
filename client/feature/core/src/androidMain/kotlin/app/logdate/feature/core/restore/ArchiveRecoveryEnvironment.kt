package app.logdate.feature.core.restore

import android.content.Context
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.shared.config.LogDateConfigRepository
import kotlinx.coroutines.flow.combine
import org.koin.core.Koin

internal suspend fun currentArchiveRecoveryScope(koin: Koin): ArchiveRecoveryScope =
    ArchiveRecoveryScope(
        koin.get<CanonicalOwnerProvider>().getCanonicalOwnerId(),
        koin.get<LogDateConfigRepository>().getCurrentBackendUrl().trimEnd('/'),
    )

internal fun Context.archiveRecoveryStore(): ArchiveRecoveryStore =
    ArchiveRecoveryStore(filesDir.resolve("archive_recovery"), cacheDir.resolve("archive_recovery_staging"))

internal suspend fun observeCurrentArchiveRecovery(
    context: Context,
    koin: Koin,
) {
    val config = koin.get<LogDateConfigRepository>()
    val configuration =
        combine(
            config.backendUrl,
            config.apiVersion,
            config.serverDescriptor,
        ) { origin, version, descriptor -> Triple(origin, version, descriptor) }
    observeArchiveRecoveryReadiness(
        configuration,
        koin.get<SessionStorage>().getSessionFlow(),
        inventory = koin.get<MediaSyncRefStore>().changes,
        identityChanges = koin.get<IdentityKeyManager>().observeIdentityChanges(),
    ) {
        ArchiveRecoveryScheduler.enqueueAll(context, koin)
    }
}
