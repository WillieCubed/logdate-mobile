package app.logdate.client.data.account

import app.logdate.client.device.identity.sameOwnerId
import kotlin.uuid.Uuid

/** Background authentication cannot claim an offline journal or replace its existing owner. */
internal class NativeRestoreAccountPolicy(
    private val ownerId: String?,
    private val bound: String?,
    private val hasLocalData: Boolean,
) {
    val canAttempt: Boolean =
        (ownerId == null || runCatching { Uuid.parse(requireNotNull(ownerId)) }.isSuccess) &&
            (bound == null || bound == "true") &&
            if (bound == "true") ownerId != null else !hasLocalData

    fun accepts(accountId: String): Boolean =
        canAttempt && runCatching { Uuid.parse(accountId) }.isSuccess && (bound != "true" || sameOwnerId(ownerId, accountId))
}
