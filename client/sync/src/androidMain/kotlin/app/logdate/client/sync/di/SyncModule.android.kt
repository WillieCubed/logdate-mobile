package app.logdate.client.sync.di

import app.logdate.client.device.di.deviceInstanceModule
import app.logdate.client.sync.AndroidSyncManager
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.cloud.di.cloudAccountModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * A module for all sync-related dependencies.
 */
actual val syncModule: Module = module {
    single<DefaultSyncManager> {
        DefaultSyncManager(
            cloudContentDataSource = get(),
            cloudJournalDataSource = get(),
            cloudAssociationDataSource = get(),
            cloudMediaDataSource = get(),
            cloudAccountRepository = get(),
            sessionStorage = get(),
            journalRepository = get(),
            journalNotesRepository = get(),
            journalContentRepository = get(),
            journalConflictResolver = get(named(SyncQualifiers.JOURNAL_CONFLICT_RESOLVER)),
            noteConflictResolver = get(named(SyncQualifiers.NOTE_CONFLICT_RESOLVER)),
            syncMetadataService = get()
        )
    }
    single<SyncManager> { AndroidSyncManager(androidContext(), get<DefaultSyncManager>(), get()) }
    includes(quotaModule, cloudAccountModule, cloudModule, deviceInstanceModule, conflictResolverModule)
}