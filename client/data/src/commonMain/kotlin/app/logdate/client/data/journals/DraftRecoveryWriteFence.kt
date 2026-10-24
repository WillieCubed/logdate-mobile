package app.logdate.client.data.journals

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.device.identity.sameOwnerId
import app.logdate.client.repository.journals.DraftRecoveryScope

internal class DraftRecoveryWriteFence(
    private val storage: KeyValueStorage,
) {
    suspend fun commit(
        key: String,
        expectedValue: String?,
        value: String,
        scope: DraftRecoveryScope,
    ): Boolean {
        val expected = storage.getStringsOrThrow(setOf(OWNER, BOUND, ORIGIN, key))
        check(sameOwnerId(expected[OWNER], scope.outboxOwnerId)) { "Draft recovery owner changed" }
        check(expected[ORIGIN]?.trimEnd('/')?.let { it == scope.outboxServerOrigin?.trimEnd('/') } != false) {
            "Draft recovery backend changed"
        }
        check(!scope.outboxServerOrigin.isNullOrBlank() && expected[key] == expectedValue) { "Draft recovery snapshot changed" }
        check(
            if (scope.ownerId ==
                null
            ) {
                expected[BOUND] == null || expected[BOUND] == "false"
            } else {
                expected[BOUND] == "true" &&
                    sameOwnerId(scope.ownerId, expected[OWNER])
            },
        ) {
            "Draft recovery binding changed"
        }
        return storage.compareAndSetStringsOrThrow(expected, mapOf(key to value))
    }

    private companion object {
        const val OWNER = "identity.canonical_owner_id"
        const val BOUND = "identity.canonical_owner_bound"
        const val ORIGIN = "backend_url"
    }
}
