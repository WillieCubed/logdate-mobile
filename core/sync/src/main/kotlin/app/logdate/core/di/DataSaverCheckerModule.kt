package app.logdate.core.di

import android.content.Context
import android.net.ConnectivityManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module
internal object DataSaverCheckerModule {
    @Provides
    fun provideConnectivityManager(context: Context): ConnectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
}