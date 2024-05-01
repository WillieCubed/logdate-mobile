package app.logdate.mobile.di

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import app.logdate.mobile.MainActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/**
 * A Dagger module that exposes activity components to the dependency graph.
 */
@InstallIn(SingletonComponent::class)
@Module
internal object LogdateAppUpdaterModule {
    @Provides
    fun provideAppUpdater(@ApplicationContext context: Context): ActivityResultLauncher<IntentSenderRequest> {
        return with(context as MainActivity) {
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                //result.getData() is always null
            }
        }
    }
}