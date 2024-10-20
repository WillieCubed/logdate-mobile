package app.logdate.core.instanceid.di

import com.google.firebase.installations.FirebaseInstallations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module
internal object DeviceInstanceModule {
    @Provides
    fun provideFirebaseInstallations(): FirebaseInstallations {
        return FirebaseInstallations.getInstance()
    }
}