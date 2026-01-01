package app.logdate.client.database

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module for providing data access objects for the client.
 */
val daosModule: Module = module {
    single { get<LogDateDatabase>().textNoteDao() }
    single { get<LogDateDatabase>().imageNoteDao() }
    single { get<LogDateDatabase>().videoNoteDao() }
    single { get<LogDateDatabase>().voiceNoteDao() }
    single { get<LogDateDatabase>().journalDao() }
    single { get<LogDateDatabase>().journalNotesDao() }
    single { get<LogDateDatabase>().journalContentDao() }
    single { get<LogDateDatabase>().rewindDao() }
    single { get<LogDateDatabase>().rewindGenerationRequestDao() }
    single { get<LogDateDatabase>().locationHistoryDao() }
    single { get<LogDateDatabase>().storageMetadataDao() }
    single { get<LogDateDatabase>().userDevicesDao() }
    single { get<LogDateDatabase>().userMediaDao() }
    single { get<LogDateDatabase>().indexedMediaDao() }
    single { get<LogDateDatabase>().transcriptionDao() }
    single { get<LogDateDatabase>().searchDao() }
    single { get<LogDateDatabase>().syncMetadataDao() }
}