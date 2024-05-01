package app.logdate.core.di

import app.logdate.core.world.FusedWorldProvider
import app.logdate.core.world.ActivityLocationProvider
import app.logdate.core.world.LogdateLocationProvider
import app.logdate.core.world.PlacesProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * A module that exposes state of the real world including location and physical activity to the application.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WorldModule {
    @Binds
    abstract fun bindLocationProvider(provider: FusedWorldProvider): ActivityLocationProvider

    @Binds
    abstract fun bindActivityProvider(provider: FusedWorldProvider): LogdateLocationProvider

    @Binds
    abstract fun bindPlacesProvider(provider: FusedWorldProvider): PlacesProvider
}