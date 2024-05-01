package app.logdate.mobile.di

import android.content.ComponentName
import android.content.Context
import app.logdate.mobile.MainActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/**
 * A Dagger module that exposes activity components to the dependency graph.
 */
@InstallIn(SingletonComponent::class)
@Module
internal object ActivityComponentModule {
    @Provides
    fun provideMainActivity(@ApplicationContext context: Context): ComponentName =
        EntryPointAccessors.fromApplication(context, MainActivity::class.java).componentName
}