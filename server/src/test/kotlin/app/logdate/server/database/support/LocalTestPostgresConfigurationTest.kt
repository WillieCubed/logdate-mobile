package app.logdate.server.database.support

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LocalTestPostgresConfigurationTest {
    @Test
    fun `disposable fixture works with application database unset`() {
        val url = "postgresql://fixture@127.0.0.1:6543/disposable"
        assertEquals(url, configuredLocalTestPostgresUrl(null, url, true))
        assertNull(configuredLocalTestPostgresUrl(null, null, false))
    }

    @Test
    fun `dedicated fixture requires explicit disposable permission and loopback`() {
        assertFailsWith<IllegalArgumentException> {
            configuredLocalTestPostgresUrl(null, "postgresql://fixture@127.0.0.1:6543/disposable", false)
        }
        assertFailsWith<IllegalArgumentException> {
            configuredLocalTestPostgresUrl(null, "postgresql://fixture@remote.invalid/disposable", true)
        }
    }

    @Test
    fun `normal CI configuration is preserved without a dedicated fixture`() {
        assertEquals("postgresql://localhost/test", configuredLocalTestPostgresUrl("postgresql://localhost/test", null, false))
    }
}
