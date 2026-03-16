package app.logdate.wear.di

import app.logdate.client.data.journals.LocalFirstDraftRepository
import app.logdate.client.data.journals.OfflineFirstJournalRepository
import app.logdate.client.data.journals.RemoteJournalDataSource
import app.logdate.client.data.notes.EmptyNotePlaceResolver
import app.logdate.client.data.notes.NotePlaceResolver
import app.logdate.client.data.notes.OfflineFirstJournalNotesRepository
import app.logdate.client.database.databaseModule
import app.logdate.client.device.di.deviceInstanceModule
import app.logdate.client.di.datastoreModule
import app.logdate.client.repository.journals.DraftRepository
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.di.conflictResolverModule
import app.logdate.shared.config.configModule
import app.logdate.shared.model.Journal
import app.logdate.wear.health.HealthServicesWearHealthSensorManager
import app.logdate.wear.health.NoteHealthAnnotator
import app.logdate.wear.health.StubWearHealthSensorManager
import app.logdate.wear.health.WearHealthSensorManager
import app.logdate.wear.sync.GoogleWearDataLayerClient
import app.logdate.wear.sync.NoteDataMapper
import app.logdate.wear.sync.WearDataLayerClient
import app.logdate.wear.sync.WearDataLayerSyncManager
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * Koin module providing the full data stack for Wear OS.
 *
 * Includes Room database with SqlCipher encryption, DAOs, DataStore preferences,
 * and offline-first repository implementations. Stubs out account, networking,
 * and Firebase bindings that are not applicable on Wear OS.
 */
val wearDataModule = module {
    includes(databaseModule)
    includes(deviceInstanceModule)
    includes(datastoreModule)
    includes(configModule)
    includes(conflictResolverModule)

    // JSON serialization
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
            encodeDefaults = true
        }
    }

    // Data Layer sync: watch <-> phone via Wearable Data API
    single<WearDataLayerClient> { GoogleWearDataLayerClient(get()) }
    single { NoteDataMapper(get()) }
    single<SyncManager> {
        WearDataLayerSyncManager(
            dataLayerClient = get(),
            syncMetadataService = get(),
            notesRepository = get(),
            noteDataMapper = get(),
        )
    }

    // Health sensor manager: uses Health Services if available, stub otherwise
    single<WearHealthSensorManager> {
        try {
            HealthServicesWearHealthSensorManager(get())
        } catch (e: Exception) {
            Napier.w("Health Services not available, using stub", e)
            StubWearHealthSensorManager()
        }
    }
    single { NoteHealthAnnotator(get(), get()) }

    // Stub remote data source (no Firebase on Wear)
    factory<RemoteJournalDataSource> { NoOpRemoteJournalDataSource }

    // Draft storage
    single<DraftRepository> { LocalFirstDraftRepository(get(), get()) }

    // Journals
    single<JournalRepository> {
        OfflineFirstJournalRepository(
            journalDao = get(),
            remoteDataSource = get(),
            draftRepository = get(),
            syncManagerProvider = { get() },
            syncMetadataService = get(),
        )
    }

    // Notes
    single<NotePlaceResolver> { EmptyNotePlaceResolver }
    single<JournalNotesRepository> {
        OfflineFirstJournalNotesRepository(
            textNoteDao = get(),
            imageNoteDao = get(),
            audioNoteDao = get(),
            videoNoteDao = get(),
            journalContentDao = get(),
            journalRepository = get(),
            mediaCaptionDao = get(),
            notePlaceResolver = get(),
            syncManagerProvider = { get() },
            syncMetadataService = get(),
        )
    }
}

/**
 * No-op remote journal data source for Wear OS standalone mode.
 */
private object NoOpRemoteJournalDataSource : RemoteJournalDataSource {
    override suspend fun observeAllJournals(): List<Journal> = emptyList()

    override suspend fun addJournal(journal: Journal): String {
        Napier.d("Wear: Remote journal creation skipped (standalone mode)")
        return journal.id.toString()
    }

    override suspend fun editJournal(journal: Journal) {
        Napier.d("Wear: Remote journal edit skipped (standalone mode)")
    }

    override suspend fun deleteJournal(journalId: String) {
        Napier.d("Wear: Remote journal deletion skipped (standalone mode)")
    }
}
