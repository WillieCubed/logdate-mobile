package app.logdate.core.di

import dagger.Component
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupComponentDependencies {
}

@Component(dependencies = [BackupComponentDependencies::class])
interface BackupRestoreComponent {
    @Component.Factory
    interface Factory {
        fun create(dependencies: BackupComponentDependencies): BackupRestoreComponent
    }
}