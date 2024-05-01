package app.logdate.feature.onboarding.di

import android.content.Context
import app.logdate.feature.onboarding.editor.AudioEntryRecorder
import app.logdate.feature.onboarding.editor.DefaultAudioEntryRecorder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

// TODO: Install in activity component to prevent memory leaks
@InstallIn(SingletonComponent::class)
@Module
object EditorModule {
    @Provides
    fun provideAudioEntryRecorder(@ApplicationContext context: Context): AudioEntryRecorder {
        return DefaultAudioEntryRecorder(context)
    }
}