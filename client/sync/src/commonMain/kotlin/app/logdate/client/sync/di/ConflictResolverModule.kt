package app.logdate.client.sync.di

import app.logdate.client.database.dao.sync.SyncMetadataDao
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.conflict.LastWriteWinsResolver
import app.logdate.client.sync.metadata.DatabaseSyncMetadataService
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.shared.model.Journal
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Named qualifier constants for conflict resolvers.
 * Using constants prevents typos and makes refactoring easier.
 */
object SyncQualifiers {
    const val JOURNAL_CONFLICT_RESOLVER = "journalConflictResolver"
    const val NOTE_CONFLICT_RESOLVER = "noteConflictResolver"
}

/**
 * Common module providing conflict resolution strategies and sync metadata service.
 * This module is included by all platform-specific sync modules.
 */
val conflictResolverModule: Module = module {
    // Conflict resolvers with named qualifiers
    single<ConflictResolver<Journal>>(named(SyncQualifiers.JOURNAL_CONFLICT_RESOLVER)) {
        LastWriteWinsResolver()
    }
    single<ConflictResolver<JournalNote>>(named(SyncQualifiers.NOTE_CONFLICT_RESOLVER)) {
        LastWriteWinsResolver()
    }

    // Sync metadata service backed by Room database
    single<SyncMetadataService> {
        DatabaseSyncMetadataService(get<SyncMetadataDao>())
    }
}
