package app.logdate.server.audit

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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

    @Test
    fun `formatAuditLog returns category only when all fields are null`() {
        val line =
            formatAuditLog(
                category = AuditCategory.AUTH_SIGNIN_PASSKEY_SUCCESS,
                fields = mapOf(AuditKey.ACCOUNT_ID to null, AuditKey.IP_HASH to null),
            )

        assertEquals("audit.auth.signin.passkey.success", line)
    }

    @Test
    fun `audit schema doc enumerates all categories and keys`() {
        val docPath = resolveAuditSchemaDocPath()
        val content = Files.readString(docPath)

        val documentedCategories =
            Regex("""### `([^`]+)`""")
                .findAll(content)
                .map { it.groupValues[1] }
                .toSet()

        val expectedCategories =
            setOf(
                AuditCategory.AUTH_SIGNUP_PASSKEY_SUCCESS,
                AuditCategory.AUTH_SIGNUP_GOOGLE_SUCCESS,
                AuditCategory.AUTH_SIGNIN_PASSKEY_SUCCESS,
                AuditCategory.AUTH_SIGNIN_GOOGLE_SUCCESS,
                AuditCategory.AUTH_LINK_GOOGLE_IMPLICIT,
            )

        assertEquals(expectedCategories, documentedCategories)

        val keyRegistryBlock =
            Regex("""## Key Registry\s*(.*?)\s*## Conventions""", setOf(RegexOption.DOT_MATCHES_ALL))
                .find(content)
                ?.groupValues
                ?.get(1)
                ?: error("Could not locate Key Registry section in ${docPath.toAbsolutePath()}")

        val documentedKeys =
            Regex("""- `([^`]+)`:""")
                .findAll(keyRegistryBlock)
                .map { it.groupValues[1] }
                .toSet()

        val expectedKeys =
            setOf(
                AuditKey.ACCOUNT_ID,
                AuditKey.IP_HASH,
                AuditKey.USER_AGENT_HASH,
                AuditKey.CREDENTIAL_ID_HASH,
                AuditKey.PROVIDER_SUBJECT_HASH,
            )

        assertEquals(expectedKeys, documentedKeys)
    }

    private fun resolveAuditSchemaDocPath(): Path {
        val candidates =
            listOf(
                Paths.get("server/docs/audit-schema.md"),
                Paths.get("docs/audit-schema.md"),
            )

        return candidates.firstOrNull { Files.exists(it) }
            ?: error("Could not locate audit schema doc. Checked: ${candidates.joinToString()}")
    }
}
