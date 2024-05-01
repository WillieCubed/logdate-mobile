package app.logdate.feature.widgets.di

import android.content.ComponentName
import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/**
 * An entrypoint that exposes generic activity dependencies for widgets.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ActivityEntryPoint {
    fun getPhotoDetailActivity(): ComponentName
}

internal fun getPhotoDetailActivity(@ApplicationContext context: Context): ComponentName =
    EntryPointAccessors.fromApplication(context, ActivityEntryPoint::class.java)
        .getPhotoDetailActivity()