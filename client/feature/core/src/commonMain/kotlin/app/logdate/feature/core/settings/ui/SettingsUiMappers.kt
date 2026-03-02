package app.logdate.feature.core.settings.ui

import app.logdate.shared.model.CloudStorageQuota
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.user.UserData
import kotlin.time.Clock
import kotlin.uuid.Uuid

fun UserData?.orDefault(): UserData = this ?: UserData()

fun LogDateAccount?.orDefault(): LogDateAccount =
    this ?: LogDateAccount(
        id = Uuid.random(),
        username = "",
        displayName = "",
        bio = null,
        passkeyCredentialIds = emptyList(),
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
    )

fun CloudStorageQuota?.orDefault(): CloudStorageQuota =
    this ?: CloudStorageQuota(
        totalBytes = 100_000_000_000L,
        usedBytes = 0L,
        categories = emptyList(),
    )

fun LogDateAccount.toUserProfile(): UserProfile =
    UserProfile(
        name = displayName.ifEmpty { "No display name" },
        username = username.ifEmpty { "no_username" },
        isEditable = username.isNotEmpty(),
        isAuthenticated = username.isNotEmpty() && passkeyCredentialIds.isNotEmpty(),
    )

fun LogDateAccount.toPasskeyInfoList(): List<PasskeyInfo> =
    if (username.isNotEmpty()) {
        passkeyCredentialIds
            .mapIndexed { index, credentialId ->
                PasskeyInfo(
                    id = credentialId,
                    name = "Passkey #${index + 1}",
                    device = "This Device",
                    createdAt = createdAt.toString(),
                    lastUsed = updatedAt,
                )
            }.ifEmpty {
                listOf(
                    PasskeyInfo(
                        id = "current",
                        name = "Current Passkey",
                        device = "This Device",
                        createdAt = createdAt.toString(),
                        lastUsed = updatedAt,
                    ),
                )
            }
    } else {
        emptyList()
    }
