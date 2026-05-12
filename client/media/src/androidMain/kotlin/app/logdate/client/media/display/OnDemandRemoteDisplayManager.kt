package app.logdate.client.media.display

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await

/**
 * [RemoteDisplayManager] that loads the real implementation on-demand from a dynamic feature
 * module via Google Play Feature Delivery.
 *
 * When the module is already installed, delegates immediately. When not installed, triggers
 * installation and falls back to no-op until the module is available.
 */
class OnDemandRemoteDisplayManager(
    private val context: Context,
) : RemoteDisplayManager {
    companion object {
        private const val MODULE_NAME = "remote_display"
        private const val PROVIDER_CLASS = "app.logdate.feature.remotedisplay.RemoteDisplayProvider"
    }

    private val splitInstallManager = SplitInstallManagerFactory.create(context)
    private val delegate = MutableStateFlow<RemoteDisplayManager?>(null)
    private val unavailableManager = UnavailableRemoteDisplayManager()

    init {
        if (isModuleInstalled()) {
            loadDelegate()
        }
    }

    /**
     * Requests installation of the remote display module.
     *
     * Call this when the user first taps the "Present" button and the module isn't installed yet.
     * Returns true if installation was started successfully, false if already installed or failed.
     */
    suspend fun requestInstall(): Boolean {
        if (isModuleInstalled()) {
            loadDelegate()
            return true
        }

        return try {
            val request =
                SplitInstallRequest
                    .newBuilder()
                    .addModule(MODULE_NAME)
                    .build()
            splitInstallManager.startInstall(request).await()
            // After successful install, load the delegate
            loadDelegate()
            true
        } catch (e: Exception) {
            Napier.e("Failed to install remote display module", e)
            false
        }
    }

    override fun observeExternalDisplays(): Flow<List<ExternalDisplay>> =
        delegate.value?.observeExternalDisplays() ?: unavailableManager.observeExternalDisplays()

    override fun present(
        displayId: Int,
        mediaUri: String,
        mimeType: String,
    ) {
        delegate.value?.present(displayId, mediaUri, mimeType)
    }

    override fun updatePresentation(
        mediaUri: String,
        mimeType: String,
    ) {
        delegate.value?.updatePresentation(mediaUri, mimeType)
    }

    override fun dismiss() {
        delegate.value?.dismiss()
    }

    override fun observeIsPresenting(): Flow<Boolean> = delegate.value?.observeIsPresenting() ?: unavailableManager.observeIsPresenting()

    private fun isModuleInstalled(): Boolean = splitInstallManager.installedModules.contains(MODULE_NAME)

    private fun loadDelegate() {
        try {
            val providerClass = Class.forName(PROVIDER_CLASS)
            val createMethod = providerClass.getMethod("create", Context::class.java)
            val instance = providerClass.getDeclaredField("INSTANCE").get(null)
            val manager = createMethod.invoke(instance, context) as RemoteDisplayManager
            delegate.value = manager
            Napier.i("Remote display module loaded successfully")
        } catch (e: Exception) {
            Napier.e("Failed to load remote display module", e)
        }
    }
}
