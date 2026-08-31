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
        username = username.ifEmpty { "" },
        isEditable = username.isNotEmpty(),
        isAuthenticated = username.isNotEmpty() && passkeyCredentialIds.isNotEmpty(),
        email = email,
        emailVerified = emailVerified,
        emailVerifiedAt = emailVerifiedAt,
    )

/**
 * Lists the account's passkeys.
 *
 * Only the credential ID is populated. The account payload carries nothing else about a passkey,
 * and the fields this used to fill -- a device of "This Device", the account's own created and
 * updated timestamps presented as the passkey's -- were not describing the credential at all. The
 * server does record a nickname, device type, and real timestamps; exposing them needs an endpoint
 * that does not exist yet.
 */
fun LogDateAccount.toPasskeyInfoList(): List<PasskeyInfo> =
    if (username.isNotEmpty()) {
        passkeyCredentialIds.map { credentialId -> PasskeyInfo(id = credentialId) }
    } else {
        emptyList()
    }
