package app.logdate.client.sync

import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.sync.metadata.IdentityRecoveryNeededStore
import io.github.aakira.napier.Napier

/** How [DefaultSyncManager] behaves on one kind of device. */
sealed interface SyncProfile {
    /** Whether this device may create an identity key; one that may not waits until it is linked. */
    val mintsIdentityKey: Boolean

    /** Whether the first sync with a server queues everything already on the device. */
    val sweepsOnFirstSync: Boolean

    /** Whether media downloads with the records it belongs to. */
    val downloadsMedia: Boolean

    /** Whether edits upload from this device; a linked watch leaves them for its phone to relay. */
    val uploadsUpdates: Boolean

    /** Whether creates upload as create-if-absent, so a record the phone already uploaded is never overwritten. */
    val createsConditionally: Boolean

    val syncsDrafts: Boolean

    val syncsLocationHistory: Boolean

    val remoteApplyFilter: RemoteApplyFilter

    /** A phone, tablet or computer someone signed in on. */
    data object Standard : SyncProfile {
        override val mintsIdentityKey = true
        override val sweepsOnFirstSync = true
        override val downloadsMedia = true
        override val uploadsUpdates = true
        override val createsConditionally = false
        override val syncsDrafts = true
        override val syncsLocationHistory = true
        override val remoteApplyFilter = RemoteApplyFilter.ALL
    }

    /**
     * A watch its phone linked to the account. It never creates an identity key, starts from nothing
     * instead of queueing what is already on it, leaves media for later, uploads only creates (if absent) and
     * deletions, keeps only the remote notes [remoteApplyFilter] allows, and has no drafts or location
     * history.
     */
    data class LinkedWatch(
        override val remoteApplyFilter: RemoteApplyFilter,
    ) : SyncProfile {
        override val mintsIdentityKey = false
        override val sweepsOnFirstSync = false
        override val downloadsMedia = false
        override val uploadsUpdates = false
        override val createsConditionally = true
        override val syncsDrafts = false
        override val syncsLocationHistory = false
    }
}

/**
 * A linked device only ever uses the key its phone installed. Without one, sync waits for the link;
 * with one that belongs to another account, sync stops until the device is linked again.
 */
internal suspend fun bindLinkedIdentityKey(
    manager: IdentityKeyManager,
    accountId: String?,
    recoveryNeeded: IdentityRecoveryNeededStore,
) {
    if (!manager.hasIdentityKey()) {
        Napier.w("This device has no identity key yet; sync waits for its phone to link it")
        return
    }
    if (accountId != null && !manager.bindToAccount(accountId)) {
        Napier.e("This device's identity key belongs to another account; sync waits until it is linked again")
        recoveryNeeded.setNeeded(true)
        return
    }
    if (recoveryNeeded.isNeeded()) recoveryNeeded.setNeeded(false)
}
