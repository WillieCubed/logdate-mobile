package app.logdate.client.watch

import android.app.Activity
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val LOGDATE_WATCH_ASSOCIATION_NAME = "LogDate Watch"

interface WatchCompanionAssociationManager {
    /** Emits the latest app-level watch association state observed on the phone. */
    fun observeAssociationState(): StateFlow<WatchAssociationSnapshot>

    /** Starts the system companion association flow when supported. */
    suspend fun beginAssociation()

    /** Attaches the launcher used to show the system-owned association chooser UI. */
    fun attachLauncher(launcher: ActivityResultLauncher<IntentSenderRequest>)

    /** Refreshes state after the chooser UI completes or is dismissed. */
    fun onAssociationFlowResult(resultCode: Int)
}

/** Android implementation backed by [CompanionDeviceManager]. */
class DefaultWatchCompanionAssociationManager(
    private val companionDeviceClient: CompanionDeviceClient,
    private val applicationScope: CoroutineScope,
    private val associationRequestFactory: WatchAssociationRequestFactory = DefaultWatchAssociationRequestFactory(),
) : WatchCompanionAssociationManager {
    private val associationState = MutableStateFlow<WatchAssociationSnapshot>(WatchAssociationSnapshot.Unsupported)
    private var launcher: ActivityResultLauncher<IntentSenderRequest>? = null

    init {
        refreshAssociationState()
    }

    override fun observeAssociationState(): StateFlow<WatchAssociationSnapshot> = associationState.asStateFlow()

    override suspend fun beginAssociation() {
        if (!companionDeviceClient.isSupported()) {
            associationState.value = WatchAssociationSnapshot.Unsupported
            return
        }

        val activityLauncher = launcher
        if (activityLauncher == null) {
            Napier.w("Cannot start watch association without an attached ActivityResult launcher")
            refreshAssociationState()
            return
        }

        associationState.value = WatchAssociationSnapshot.Pending
        companionDeviceClient.associate(
            request = associationRequestFactory.createRequest(),
            callback = associationCallback(activityLauncher),
        )
    }

    override fun attachLauncher(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        this.launcher = launcher
        refreshAssociationState()
    }

    override fun onAssociationFlowResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_CANCELED &&
            associationState.value == WatchAssociationSnapshot.Pending
        ) {
            associationState.value = WatchAssociationSnapshot.Unassociated
        }
        refreshAssociationState()
    }

    private fun refreshAssociationState() {
        applicationScope.launch {
            associationState.value = companionDeviceClient.currentAssociation()
        }
    }

    private fun associationCallback(activityLauncher: ActivityResultLauncher<IntentSenderRequest>): CompanionDeviceManager.Callback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            modernAssociationCallback(activityLauncher)
        } else {
            legacyAssociationCallback(activityLauncher)
        }

    private fun modernAssociationCallback(activityLauncher: ActivityResultLauncher<IntentSenderRequest>): CompanionDeviceManager.Callback =
        object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                launchAssociationIntent(activityLauncher, intentSender)
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                this@DefaultWatchCompanionAssociationManager.onAssociationCreated(associationInfo)
            }

            override fun onFailure(error: CharSequence?) {
                onAssociationFailed(error = error)
            }

            override fun onFailure(
                errorCode: Int,
                error: CharSequence?,
            ) {
                onAssociationFailed(errorCode = errorCode, error = error)
            }
        }

    @Suppress("DEPRECATION")
    private fun legacyAssociationCallback(activityLauncher: ActivityResultLauncher<IntentSenderRequest>): CompanionDeviceManager.Callback =
        object : CompanionDeviceManager.Callback() {
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onDeviceFound(intentSender: IntentSender) {
                launchAssociationIntent(activityLauncher, intentSender)
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                this@DefaultWatchCompanionAssociationManager.onAssociationCreated(associationInfo)
            }

            override fun onFailure(error: CharSequence?) {
                onAssociationFailed(error = error)
            }
        }

    private fun launchAssociationIntent(
        activityLauncher: ActivityResultLauncher<IntentSenderRequest>,
        intentSender: IntentSender,
    ) {
        activityLauncher.launch(
            IntentSenderRequest
                .Builder(intentSender)
                .build(),
        )
    }

    private fun onAssociationCreated(associationInfo: AssociationInfo) {
        associationState.value =
            WatchAssociationSnapshot.Associated(
                displayName = associationInfo.displayName?.toString(),
                macAddress = associationInfo.deviceMacAddress?.toString(),
            )
    }

    private fun onAssociationFailed(
        errorCode: Int? = null,
        error: CharSequence?,
    ) {
        if (errorCode == null) {
            Napier.w("Watch association failed: ${error?.toString().orEmpty()}")
        } else {
            Napier.w("Watch association failed with code $errorCode: ${error?.toString().orEmpty()}")
        }
        refreshAssociationState()
    }
}

interface CompanionDeviceClient {
    /** Whether the current phone exposes the companion-device setup feature. */
    fun isSupported(): Boolean

    /** Returns the current LogDate watch association snapshot, if any. */
    suspend fun currentAssociation(): WatchAssociationSnapshot

    /** Starts the framework association request. */
    fun associate(
        request: AssociationRequest,
        callback: CompanionDeviceManager.Callback,
    )
}

/** Factory that builds the framework association request used for watch pairing. */
interface WatchAssociationRequestFactory {
    fun createRequest(): AssociationRequest
}

/** Default watch association request for LogDate's phone-to-watch relationship. */
class DefaultWatchAssociationRequestFactory : WatchAssociationRequestFactory {
    override fun createRequest(): AssociationRequest =
        AssociationRequest
            .Builder()
            .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
            .setDisplayName(LOGDATE_WATCH_ASSOCIATION_NAME)
            .setSingleDevice(false)
            .build()
}

/** Thin Android wrapper around [CompanionDeviceManager] to keep the manager testable. */
class AndroidCompanionDeviceClient(
    private val context: Context,
) : CompanionDeviceClient {
    private val companionDeviceManager =
        context.getSystemService(CompanionDeviceManager::class.java)

    override fun isSupported(): Boolean =
        companionDeviceManager != null &&
            context.packageManager.hasSystemFeature("android.software.companion_device_setup")

    override suspend fun currentAssociation(): WatchAssociationSnapshot {
        val manager = companionDeviceManager ?: return WatchAssociationSnapshot.Unsupported
        if (!isSupported()) {
            return WatchAssociationSnapshot.Unsupported
        }

        val association =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager
                    .myAssociations
                    .firstOrNull { it.deviceProfile == AssociationRequest.DEVICE_PROFILE_WATCH }
                    ?.toSnapshot()
            } else {
                manager.legacyWatchAssociation()
            }

        return association ?: WatchAssociationSnapshot.Unassociated
    }

    override fun associate(
        request: AssociationRequest,
        callback: CompanionDeviceManager.Callback,
    ) {
        val manager = companionDeviceManager ?: return
        manager.associate(request, context.mainExecutor, callback)
    }
}

private fun AssociationInfo.toSnapshot(): WatchAssociationSnapshot.Associated =
    WatchAssociationSnapshot.Associated(
        displayName = displayName?.toString(),
        macAddress = deviceMacAddress?.toString(),
    )

@Suppress("DEPRECATION")
private fun CompanionDeviceManager.legacyWatchAssociation(): WatchAssociationSnapshot.Associated? =
    associations
        .firstOrNull()
        ?.let { macAddress ->
            WatchAssociationSnapshot.Associated(macAddress = macAddress)
        }
