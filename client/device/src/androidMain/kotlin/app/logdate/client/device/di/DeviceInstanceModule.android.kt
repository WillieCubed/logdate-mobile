package app.logdate.client.device.di

import app.logdate.client.device.AndroidInstanceIdProvider
import app.logdate.client.device.InstanceIdProvider
import com.google.firebase.installations.FirebaseInstallations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

actual val deviceInstanceModule: Module = module {
    // Create a SupervisorJob that will be cancelled when the app is destroyed
    // TODO: Find a better way to handle this
    single { SupervisorJob() }

    // Create a CoroutineScope that uses the application dispatcher and supervisor job
    single {
        CoroutineScope(get<Job>() + Dispatchers.Default)
    }
    single { FirebaseInstallations.getInstance() }

    // Inject the InstanceIdProvider with the application-scoped coroutine scope
    single<InstanceIdProvider> {
        AndroidInstanceIdProvider(
            firebaseInstallations = get<FirebaseInstallations>(),
            externalScope = get<CoroutineScope>()
        )
    }
}
