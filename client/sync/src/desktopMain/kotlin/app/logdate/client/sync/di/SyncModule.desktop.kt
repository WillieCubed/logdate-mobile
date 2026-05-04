package app.logdate.client.sync.di

import app.logdate.client.database.LogDateDatabase
import app.logdate.client.device.di.deviceInstanceModule
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.ForegroundSyncManager
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.cloud.di.cloudAccountModule
import app.logdate.client.sync.conflict.KeyValueSyncConflictStore
import app.logdate.client.sync.conflict.SyncConflictStore
import app.logdate.client.sync.metadata.KeyValueMediaSyncRefStore
import app.logdate.client.sync.migration.di.migrationCoreModule
import app.logdate.client.sync.migration.di.migrationModule
import app.logdate.client.sync.metadata.KeyValueSyncDeadLetterStore
import app.logdate.client.sync.metadata.KeyValueSyncRetryScheduleStore
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * A module for all sync-related dependencies.
 */
actual val syncModule: Module =
    module {
        single<SyncConflictStore> { KeyValueSyncConflictStore(get()) }
        single<MediaSyncRefStore> { KeyValueMediaSyncRefStore(get()) }
        single<SyncDeadLetterStore> { KeyValueSyncDeadLetterStore(get()) }
        single<SyncRetryScheduleStore> { KeyValueSyncRetryScheduleStore(get()) }
        single<SyncTransactionManager> {
            RoomSyncTransactionManager(get<LogDateDatabase>())
        }
        single<DefaultSyncManager> {
            DefaultSyncManager(
                cloudContentDataSource = get(),
                cloudJournalDataSource = get(),
                cloudAssociationDataSource = get(),
                cloudMediaDataSource = get(),
                cloudDraftDataSource = get(),
                cloudAccountRepository = get(),
                sessionStorage = get(),
                mediaManager = get(),
                mediaSyncRefStore = get(),
                journalRepository = get(),
                journalNotesRepository = get(),
                journalContentRepository = get(),
                journalConflictResolver = get(named(SyncQualifiers.JOURNAL_CONFLICT_RESOLVER)),
                noteConflictResolver = get(named(SyncQualifiers.NOTE_CONFLICT_RESOLVER)),
                conflictStore = get(),
                deadLetterStore = get(),
                retryScheduleStore = get(),
                syncMetadataService = get(),
                transactionManager = get(),
                dataUsagePolicy = get(),
            )
        }
        single<SyncManager> { ForegroundSyncManager(get(), get(), get(), get(), get()) }
        includes(
            quotaModule,
            cloudAccountModule,
            cloudModule,
            deviceInstanceModule,
            conflictResolverModule,
            migrationCoreModule,
            migrationModule,
            app.logdate.client.media.di.mediaModule,
        )
    }
