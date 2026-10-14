package app.logdate.client.di

import android.content.pm.PackageManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Android-specific datastore module implementation
 */
actual val datastoreModule: Module =
    module {
        // Include common datastore module components
        includes(commonDatastoreModule)
        single(named("watchExperienceDefault")) {
            val context = androidContext()
            runCatching {
                context.packageManager
                    .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                    .metaData
                    ?.getBoolean("app.logdate.watchExperienceDefault", false) ?: false
            }.getOrDefault(false)
        }
    }
