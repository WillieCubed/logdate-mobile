package app.logdate.core.di

import app.logdate.core.notifications.service.RemoteNotificationProvider
import app.logdate.core.sync.LogdateServiceSyncProvider
import dagger.Component
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncComponentDependencies {
    fun logdateServiceSyncProvider(): LogdateServiceSyncProvider
    fun remoteNotificationProvider(): RemoteNotificationProvider
}

@Component(dependencies = [SyncComponentDependencies::class])
interface SyncComponent {

    fun inject(syncProvider: LogdateServiceSyncProvider)
}