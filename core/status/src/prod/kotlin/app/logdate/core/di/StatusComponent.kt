package app.logdate.core.di

import app.logdate.core.status.DefaultPresenceProvider
import app.logdate.core.status.PresenceProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * A module that exposes state of the real world including location and physical activity to the application.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class StatusComponent {
    @Binds
    abstract fun bindPresenceProvider(provider: DefaultPresenceProvider): PresenceProvider
}