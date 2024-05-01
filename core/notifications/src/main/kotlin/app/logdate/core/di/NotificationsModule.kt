package app.logdate.core.di

import app.logdate.core.notifications.AndroidLogdateNotifier
import app.logdate.core.notifications.Notifier
import app.logdate.core.notifications.service.FirebaseNotificationProvider
import app.logdate.core.notifications.service.RegistrationTokenProvider
import app.logdate.core.notifications.service.RemoteNotificationProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotificationsModule {
    @Binds
    abstract fun bindNotifier(notifier: AndroidLogdateNotifier): Notifier

    @Binds
    abstract fun bindRegistrationTokenProvider(provider: FirebaseNotificationProvider): RegistrationTokenProvider

    @Binds
    abstract fun bindRemoteNotificationProvider(provider: FirebaseNotificationProvider): RemoteNotificationProvider
}
