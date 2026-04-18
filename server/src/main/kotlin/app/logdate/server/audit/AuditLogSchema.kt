package app.logdate.server.audit

/**
 * Canonical audit categories emitted by the server.
 */
object AuditCategory {
    const val AUTH_SIGNUP_PASSKEY_SUCCESS = "auth.signup.passkey.success"
    const val AUTH_SIGNUP_GOOGLE_SUCCESS = "auth.signup.google.success"
    const val AUTH_SIGNIN_PASSKEY_SUCCESS = "auth.signin.passkey.success"
    const val AUTH_SIGNIN_GOOGLE_SUCCESS = "auth.signin.google.success"
    const val AUTH_LINK_GOOGLE_IMPLICIT = "auth.link.google.implicit"

    const val ACCOUNT_DELETED = "account.deleted"
    const val SYNC_QUOTA_EXCEEDED = "sync.quota.exceeded"
    const val SYNC_RATE_LIMITED = "sync.rate.limited"
}

/**
 * Canonical audit key names used in event payloads.
 */
object AuditKey {
    const val ACCOUNT_ID = "accountId"
    const val IP_HASH = "ipHash"
    const val USER_AGENT_HASH = "userAgentHash"
    const val CREDENTIAL_ID_HASH = "credentialIdHash"
    const val PROVIDER_SUBJECT_HASH = "providerSubjectHash"
}

/**
 * Builds a consistent audit log line:
 * `audit.<category> key=value key=value ...`
 */
fun formatAuditLog(
    category: String,
    fields: Map<String, String?>,
): String {
    val renderedFields =
        fields
            .toSortedMap()
            .mapNotNull { (key, value) -> value?.let { "$key=${it.replace(' ', '_')}" } }
            .joinToString(" ")

    return if (renderedFields.isBlank()) {
        "audit.$category"
    } else {
        "audit.$category $renderedFields"
    }
}
