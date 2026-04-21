package app.logdate.server.sync

import kotlin.Function1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [MediaAccessPolicy], focusing on the parsing of security and access
 * configuration from environment variables.
 *
 * It ensures that TTL bounds for signed URLs are correctly enforced and that
 * various boolean representations (e.g., "yes", "1", "true") are parsed consistently.
 */
class MediaAccessPolicyTest {
    @Test
    fun `media access policy reads environment defaults and boolean parser`() {
        val policy = MediaAccessPolicy.fromEnvironment { null }
        assertNotNull(policy)
        assertEquals(policy.signedUrlTtlHours.coerceIn(1L, 24L), policy.signedUrlTtlHours)

        val signedPolicy =
            MediaAccessPolicy.fromEnvironment {
                when (it) {
                    "SYNC_MEDIA_SIGNED_URLS" -> "yes"
                    "SYNC_MEDIA_SIGNED_URL_TTL_HOURS" -> "72"
                    else -> null
                }
            }
        assertTrue(signedPolicy.useSignedUrls)
        assertEquals(24L, signedPolicy.signedUrlTtlHours)

        val minBoundPolicy =
            MediaAccessPolicy.fromEnvironment {
                when (it) {
                    "SYNC_MEDIA_SIGNED_URLS" -> "1"
                    "SYNC_MEDIA_SIGNED_URL_TTL_HOURS" -> "-5"
                    else -> null
                }
            }
        assertTrue(minBoundPolicy.useSignedUrls)
        assertEquals(1L, minBoundPolicy.signedUrlTtlHours)

        val companion = MediaAccessPolicy.Companion::class.java
        val readBoolean =
            companion.getDeclaredMethod(
                "readBooleanEnv",
                String::class.java,
                Boolean::class.javaPrimitiveType,
                Function1::class.java,
            )
        readBoolean.isAccessible = true
        val fallback =
            readBoolean.invoke(
                MediaAccessPolicy.Companion,
                "UNSET_TEST_ENV_KEY",
                true,
                { _: String -> null } as Function1<String, String?>,
            ) as Boolean
        assertEquals(true, fallback)

        val fromTrue =
            readBoolean.invoke(
                MediaAccessPolicy.Companion,
                "SYNC_MEDIA_SIGNED_URLS",
                false,
                { _: String -> "true" } as Function1<String, String?>,
            ) as Boolean
        val fromYes =
            readBoolean.invoke(
                MediaAccessPolicy.Companion,
                "SYNC_MEDIA_SIGNED_URLS",
                false,
                { _: String -> "YES" } as Function1<String, String?>,
            ) as Boolean
        val fromOne =
            readBoolean.invoke(
                MediaAccessPolicy.Companion,
                "SYNC_MEDIA_SIGNED_URLS",
                false,
                { _: String -> "1" } as Function1<String, String?>,
            ) as Boolean
        assertTrue(fromTrue)
        assertTrue(fromYes)
        assertTrue(fromOne)
    }
}
