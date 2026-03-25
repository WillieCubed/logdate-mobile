package app.logdate.dynamic

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over Play Feature Delivery's [SplitInstallManager][com.google.android.play.core.splitinstall.SplitInstallManager]
 * for checking, installing, and loading code from dynamic feature modules.
 *
 * Injected via Koin so that tests can substitute a fake implementation.
 */
interface DynamicFeatureLoader {
    /**
     * Returns true when [moduleName] has already been downloaded and is available on-device.
     */
    fun isInstalled(moduleName: String): Boolean

    /**
     * Requests installation of [moduleName].
     *
     * Returns [DynamicInstallResult.Success] once the module is ready,
     * [DynamicInstallResult.AlreadyInstalled] if it was already present,
     * or [DynamicInstallResult.Failed] on error.
     */
    suspend fun requestInstall(moduleName: String): DynamicInstallResult

    /**
     * Emits the current install state for [moduleName] so UI can show progress.
     */
    fun observeInstallState(moduleName: String): Flow<DynamicInstallState>

    /**
     * Loads a provider object from an installed dynamic module by its fully-qualified class name.
     *
     * Returns null when the class cannot be found (module not installed or class name wrong).
     */
    fun <T> loadProvider(providerClassName: String): T?
}

/**
 * Coarse install state exposed to UI layers.
 */
sealed class DynamicInstallState {
    data object NotInstalled : DynamicInstallState()

    data class Downloading(
        val progress: Float,
    ) : DynamicInstallState()

    data object Installed : DynamicInstallState()

    data class Failed(
        val error: Exception,
    ) : DynamicInstallState()
}

/**
 * One-shot result of a [DynamicFeatureLoader.requestInstall] call.
 */
sealed class DynamicInstallResult {
    data object Success : DynamicInstallResult()

    data object AlreadyInstalled : DynamicInstallResult()

    data class Failed(
        val error: Exception,
    ) : DynamicInstallResult()
}
