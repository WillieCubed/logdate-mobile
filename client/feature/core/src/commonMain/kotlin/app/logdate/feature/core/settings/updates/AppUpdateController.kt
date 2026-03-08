package app.logdate.feature.core.settings.updates

import app.logdate.client.device.AppInfoProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Distinguishes background update checks from user-initiated ones.
 *
 * Automatic checks are intentionally quieter, while manual checks can surface
 * errors and bypass flexible-update deferral.
 */
enum class AppUpdateCheckTrigger {
    /** Silent or low-friction check started by normal app lifecycle events. */
    Automatic,

    /** Explicit check started by the user from a visible settings action. */
    Manual,
}

/**
 * The Google Play update flow type that should be launched for an eligible release.
 */
enum class AppUpdateFlowType {
    /** Background-download flow that lets the user keep using the app. */
    Flexible,

    /** Blocking flow used when the update should interrupt normal app usage. */
    Immediate,
}

/**
 * UI-friendly summary of the current in-app update state.
 *
 * This keeps shared settings UI decoupled from the Android Play Core API surface.
 */
enum class AppUpdateStatus {
    /** No user-visible update result is currently being surfaced. */
    Idle,

    /** The app is currently querying Play for update metadata. */
    Checking,

    /** Play reported that no eligible update should be shown right now. */
    UpToDate,

    /** An eligible update exists and the flow is about to launch or resume. */
    Available,

    /** A flexible update is downloading, or final installation has started. */
    Downloading,

    /** A flexible update has finished downloading and is waiting for restart. */
    Downloaded,

    /** The current platform or install source cannot use Play in-app updates. */
    Unsupported,

    /** The most recent manual check or flow launch failed. */
    Error,
}

/**
 * Shared, presentation-ready view of app update state.
 *
 * The Android implementation keeps this state in sync with Play Core, while
 * non-Android platforms expose an unsupported state.
 */
data class AppUpdateUiState(
    /** Human-readable version name of the currently installed app build. */
    val currentVersionName: String = "unknown",
    /** Version code of the currently installed app build, when available. */
    val currentVersionCode: Int? = null,
    /** High-level update state that shared UI can render directly. */
    val status: AppUpdateStatus = AppUpdateStatus.Idle,
    /** Version code reported by Play for the available update, when known. */
    val availableVersionCode: Int? = null,
    /** Flow Play should use for the available update, when one has been chosen. */
    val flowType: AppUpdateFlowType? = null,
    /** Optional user-facing status or error message for settings surfaces. */
    val message: String? = null,
    /** Timestamp of the last completed Play check surfaced through this state. */
    val lastCheckedAt: Instant? = null,
)

/**
 * Platform abstraction used by shared settings UI to observe and trigger
 * application updates.
 */
interface AppUpdateController {
    /**
     * Current app update state for UI surfaces such as Advanced Settings.
     */
    val uiState: StateFlow<AppUpdateUiState>

    /**
     * Checks whether an update is available and launches the appropriate flow when needed.
     */
    suspend fun checkForUpdates(trigger: AppUpdateCheckTrigger)

    /**
     * Completes a previously downloaded flexible update.
     */
    suspend fun completeUpdate()
}

/**
 * Non-Android fallback used on platforms where Google Play in-app updates do not exist.
 */
class UnsupportedAppUpdateController(
    appInfoProvider: AppInfoProvider,
) : AppUpdateController {
    private val appInfo = appInfoProvider.getAppInfo()
    private val _uiState =
        MutableStateFlow(
            AppUpdateUiState(
                currentVersionName = appInfo.versionName,
                currentVersionCode = appInfo.versionCode,
                status = AppUpdateStatus.Unsupported,
                message = "In-app updates are only available on Android builds installed from Google Play.",
                lastCheckedAt = Clock.System.now(),
            ),
        )

    override val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    /** No-op because non-Android platforms do not support Play-managed updates. */
    override suspend fun checkForUpdates(trigger: AppUpdateCheckTrigger) = Unit

    /** No-op because non-Android platforms do not support Play-managed updates. */
    override suspend fun completeUpdate() = Unit
}
