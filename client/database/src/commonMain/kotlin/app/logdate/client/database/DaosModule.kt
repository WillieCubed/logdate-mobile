package app.logdate.client.database

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module for providing data access objects for the client.
 */
val daosModule: Module = module {
    single { get<LogDateDatabase>().textNoteDao() }
    single { get<LogDateDatabase>().imageNoteDao() }
    single { get<LogDateDatabase>().journalDao() }
    single { get<LogDateDatabase>().journalNotesDao() }
    single { get<LogDateDatabase>().rewindDao() }
    single { get<LogDateDatabase>().locationHistoryDao() }
    single { get<LogDateDatabase>().userDevicesDao() }
    single { get<LogDateDatabase>().userMediaDao() }
}