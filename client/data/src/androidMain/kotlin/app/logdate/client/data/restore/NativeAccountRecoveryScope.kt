package app.logdate.client.data.restore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.datastore.NativeRestorePolicy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** Cosmetic settings and copied installation ids are deliberately outside recovery authorization. */
internal suspend fun nativeAccountRecoveryRequest(
    delivery: String,
    generation: String,
    preferences: Preferences,
): NativeAccountRecoveryRequest? {
    val config = nativeRestoreConfiguration(preferences) ?: return null
    val origin = config.getCurrentBackendUrl()
    val decision =
        NativeRestorePolicy({
            preferences[stringPreferencesKey(it)]
        }, { _, _ -> error("Read-only restore snapshot") }).decision(origin)
    if (!NativeRestorePolicy.allows(decision)) return null
    val security =
        listOf(origin, config.getCurrentApiBaseUrl(), config.apiVersion.value, config.getCurrentServerDescriptor()?.passkey?.rpId, decision)
    val digest = MessageDigest.getInstance("SHA-256").digest(Json.encodeToString(security).encodeToByteArray())
    return NativeAccountRecoveryRequest(
        delivery,
        generation,
        digest.joinToString("") { "%02x".format(it) },
        preferences[stringPreferencesKey("identity.canonical_owner_id")],
        preferences[stringPreferencesKey("identity.canonical_owner_bound")],
    )
}

internal fun nativeRestoreSelectedConfigurationMatches(
    expected: app.logdate.shared.config.LogDateConfigRepository,
    selected: app.logdate.shared.config.LogDateConfigRepository,
): Boolean {
    fun security(config: app.logdate.shared.config.LogDateConfigRepository): List<String?> {
        val origin = config.getCurrentBackendUrl().trimEnd('/')
        val descriptor = config.getCurrentServerDescriptor()
        return listOf(
            origin,
            descriptor?.apiBaseUrl?.trimEnd('/') ?: "$origin/api/${config.apiVersion.value}",
            config.apiVersion.value,
            descriptor?.passkey?.rpId,
            descriptor?.hasCapability(app.logdate.shared.model.ServerCapability.AUTH_PASSKEY)?.toString(),
            descriptor?.hasProtocolFeature(app.logdate.shared.model.ServerProtocolFeature.CANONICAL_OWNER_BINDING_V1)?.toString(),
        )
    }
    return security(expected) == security(selected)
}

internal suspend fun recoverNativeAccountRecoveryCustody(
    request: NativeAccountRecoveryRequest,
    preferences: Preferences,
    inbox: NativeAccountRecoveryInbox,
    hasLocalContent: Boolean,
): Boolean {
    val actual = nativeAccountRecoveryRequest(request.delivery, request.generation, preferences) ?: return false
    val policy =
        app.logdate.client.data.account
            .NativeRestoreAccountPolicy(actual.owner, actual.binding, hasLocalContent)
    val target =
        if (actual == request) {
            if (!policy.canAttempt) return false
            actual.owner.takeIf { actual.binding == "true" }
        } else {
            val owner = actual.owner ?: return false
            if (actual.binding != "true" ||
                preferences[stringPreferencesKey("identity.native_restore_commit")] != nativeAccountRecoveryCommitStamp(request, owner) ||
                actual.copy(owner = request.owner, binding = request.binding) != request
            ) {
                return false
            }
            owner
        }
    return inbox.restoreScheduledRequest(request, target)
}

internal fun nativeAccountRecoveryCommitStamp(
    request: NativeAccountRecoveryRequest,
    target: String,
): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(Json.encodeToString(request).encodeToByteArray())
    return bytes.joinToString("") { "%02x".format(it) } + ":" + target
}
