package app.logdate.feature.core.restore

import android.content.Context
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.shared.config.LogDateConfigRepository
import org.koin.core.Koin

internal suspend fun currentArchiveRecoveryScope(koin: Koin): ArchiveRecoveryScope =
    ArchiveRecoveryScope(
        koin.get<CanonicalOwnerProvider>().getCanonicalOwnerId(),
        koin.get<LogDateConfigRepository>().getCurrentBackendUrl().trimEnd('/'),
    )

internal fun Context.archiveRecoveryStore(): ArchiveRecoveryStore =
    ArchiveRecoveryStore(filesDir.resolve("archive_recovery"), cacheDir.resolve("archive_recovery_staging"))
