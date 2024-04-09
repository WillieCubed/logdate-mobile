package app.logdate.core.di

import app.logdate.core.notifications.AndroidLogdateNotifier
import app.logdate.core.notifications.Notifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotificationsModule {
    @Binds
    abstract fun bindNotifier(notifier: AndroidLogdateNotifier): Notifier
}