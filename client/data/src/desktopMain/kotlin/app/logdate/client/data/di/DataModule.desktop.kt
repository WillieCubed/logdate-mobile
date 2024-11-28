package app.logdate.client.data.di

import app.logdate.client.data.journals.OfflineFirstJournalRepository
import app.logdate.client.data.journals.OfflineFirstJournalUserDataRepository
import app.logdate.client.data.journals.RemoteJournalDataSource
import app.logdate.client.data.journals.StubJournalDataSource
import app.logdate.client.data.rewind.OfflineFirstRewindRepository
import app.logdate.client.data.timeline.OfflineFirstActivityTimelineRepository
import app.logdate.client.data.user.StubUserDeviceRepository
import app.logdate.client.data.user.StubUserStateRepository
import app.logdate.client.device.di.deviceInstanceModule
import app.logdate.client.di.datastoreModule
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.client.repository.timeline.ActivityTimelineRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.client.repository.user.devices.UserDeviceRepository
import app.logdate.client.data.journals.JournalUserDataRepository
import org.koin.core.module.Module
import org.koin.dsl.module

actual val dataModule: Module = module {
    includes(deviceInstanceModule)
    includes(datastoreModule)

    // Journals
    factory<RemoteJournalDataSource> { StubJournalDataSource }
    single<JournalUserDataRepository> { OfflineFirstJournalUserDataRepository(get()) }
    single<JournalRepository> { OfflineFirstJournalRepository(get(), get(), get(), get()) }

    // Rewind
    single<RewindRepository> { OfflineFirstRewindRepository(get(), get()) }

    // Timeline
    single<ActivityTimelineRepository> { OfflineFirstActivityTimelineRepository() }

    // User
    single<UserDeviceRepository> { StubUserDeviceRepository }
//    single<UserStateRepository> { OfflineFirstUserStateRepository(get()) }
    single<UserStateRepository> { StubUserStateRepository }
}