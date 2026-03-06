package app.logdate.server.audit

import kotlin.test.Test
import kotlin.test.assertEquals

class AuditLogSchemaTest {
    @Test
    fun `formatAuditLog renders stable sorted key-value payload`() {
        val line =
            formatAuditLog(
                category = AuditCategory.AUTH_SIGNIN_GOOGLE_SUCCESS,
                fields =
                    mapOf(
                        AuditKey.USER_AGENT_HASH to "ua-hash",
                        AuditKey.ACCOUNT_ID to "account-1",
                    ),
            )

        assertEquals(
            "audit.auth.signin.google.success accountId=account-1 userAgentHash=ua-hash",
            line,
        )
    }

    @Test
    fun `formatAuditLog omits null fields`() {
        val line =
            formatAuditLog(
                category = AuditCategory.AUTH_LINK_GOOGLE_IMPLICIT,
                fields =
                    mapOf(
                        AuditKey.ACCOUNT_ID to "account-1",
                        AuditKey.IP_HASH to null,
                    ),
            )

        assertEquals("audit.auth.link.google.implicit accountId=account-1", line)
    }
}
