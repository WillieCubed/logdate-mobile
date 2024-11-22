package app.logdate.feature.rewind.di

import app.logdate.core.data.rewind.DefaultRewindGenerator
import app.logdate.core.data.rewind.RewindGenerator
import app.logdate.feature.rewind.data.RewindMessageGenerator
import app.logdate.feature.rewind.data.WittyRewindMessageGenerator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RewindGeneratorModule {
    @Binds
    abstract fun bindRewindMessageGenerator(rewindMessageGenerator: WittyRewindMessageGenerator): RewindMessageGenerator

    @Binds
    abstract fun bindRewindGenerator(rewindMessageGenerator: DefaultRewindGenerator): RewindGenerator
}