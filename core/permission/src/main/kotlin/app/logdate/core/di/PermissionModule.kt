package app.logdate.core.di

import app.logdate.core.permission.AndroidPermissionProvider
import app.logdate.core.permission.PermissionProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PermissionModule {

    @Binds
    @Singleton
    abstract fun bindPermissionProvider(provider: AndroidPermissionProvider): PermissionProvider
}
