package app.logdate.feature.rewind.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RewindModule {
//    @Binds
//    abstract fun bindRewindRepository(impl: OfflineFirstRewindRepository): RewindRepository
}