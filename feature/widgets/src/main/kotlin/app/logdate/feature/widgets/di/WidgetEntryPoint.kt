package app.logdate.feature.widgets.di

import android.content.Context
import app.logdate.core.data.notes.JournalNotesRepository
import app.logdate.core.data.user.UserStateRepository
import app.logdate.core.media.MediaManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun journalNotesRepository(): JournalNotesRepository
    fun userStateRepository(): UserStateRepository
    fun mediaManager(): MediaManager
}

internal fun getJournalNotesRepository(@ApplicationContext context: Context): JournalNotesRepository =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        .journalNotesRepository()

internal fun getUserStateRepository(@ApplicationContext context: Context): UserStateRepository =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        .userStateRepository()

internal fun getMediaManager(@ApplicationContext context: Context): MediaManager =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        .mediaManager()