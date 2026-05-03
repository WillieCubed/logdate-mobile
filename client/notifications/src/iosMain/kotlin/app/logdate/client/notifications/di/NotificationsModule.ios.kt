package app.logdate.client.notifications.di

import app.logdate.client.notifications.IosLocalNotificationScheduler
import app.logdate.client.notifications.NotificationScheduler
import org.koin.dsl.module

val iosNotificationsModule =
    module {
        single<NotificationScheduler> { IosLocalNotificationScheduler() }
    }
