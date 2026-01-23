package app.logdate.feature.core.settings.ui

import app.logdate.client.sync.SyncStatus
import app.logdate.shared.model.CloudStorageQuota
import app.logdate.shared.model.CloudStorageCategoryUsage
import app.logdate.shared.model.CloudObjectType
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.user.UserData
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Aggregated state for the settings UI.
 *
 * @property userData Local user profile and preferences.
 * @property quotaState Cloud storage usage details for the account.
 * @property currentAccount Current authenticated account details.
 * @property passkeyCreationState State for passkey creation workflow.
 * @property exportState State for export workflow and destination.
 * @property syncStatus Latest sync status from the sync manager.
 * @property isAuthenticated Whether the user is authenticated.
 * @property isBackgroundSyncEnabled Whether background sync is enabled.
 */
data class SettingsUiState(
    val userData: UserData,
    val quotaState: CloudStorageQuota,
    val currentAccount: LogDateAccount,
    val passkeyCreationState: PasskeyCreationState,
    val exportState: ExportState = ExportState.Idle,
    val syncStatus: SyncStatus? = null,
    val isAuthenticated: Boolean = false,
    val isBackgroundSyncEnabled: Boolean = true
)

// Extension functions to provide defaults when data is not available
fun UserData?.orDefault(): UserData = this ?: UserData()

fun LogDateAccount?.orDefault(): LogDateAccount = this ?: LogDateAccount(
    id = kotlin.uuid.Uuid.random(),
    username = "",
    displayName = "",
    bio = null,
    passkeyCredentialIds = emptyList(),
    createdAt = Clock.System.now(),
    updatedAt = Clock.System.now()
)

fun CloudStorageQuota?.orDefault(): CloudStorageQuota = this ?: CloudStorageQuota(
    totalBytes = 100_000_000_000L, // 100GB default
    usedBytes = 0L,
    categories = emptyList()
)


fun LogDateAccount.toUserProfile(): UserProfile = UserProfile(
    name = displayName.ifEmpty { "No display name" },
    username = username.ifEmpty { "no_username" },
    isEditable = username.isNotEmpty(),
    isAuthenticated = username.isNotEmpty() && passkeyCredentialIds.isNotEmpty()
)

fun LogDateAccount.toPasskeyInfoList(): List<PasskeyInfo> {
    return if (username.isNotEmpty()) {
        passkeyCredentialIds.mapIndexed { index, credentialId ->
            PasskeyInfo(
                id = credentialId,
                name = "Passkey #${index + 1}",
                device = "This Device", // TODO: Get actual device info
                createdAt = createdAt.toString(),
                lastUsed = updatedAt
            )
        }.ifEmpty {
            // Show at least one passkey if account exists but no credentials listed
            listOf(
                PasskeyInfo(
                    id = "current",
                    name = "Current Passkey",
                    device = "This Device",
                    createdAt = createdAt.toString(),
                    lastUsed = updatedAt
                )
            )
        }
    } else {
        emptyList()
    }
}

// PasskeyCreationState moved to its own file
