package app.logdate.client.watch.presence

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchCatchUpTest {
    @Test fun revokedOrDisabledLinkCannotTriggerWindowPublication() =
        runTest {
            var links = setOf("verified-link")
            var windows = 0
            val runner =
                WatchCatchUp({ true }, { links }, { links = emptySet() }, {
                    windows++
                    true
                })
            assertTrue(runner.run())
            assertEquals(0, windows)
            val disabled = WatchCatchUp({ false }, { setOf("link") }, { error("disabled") }, { error("disabled") })
            assertTrue(disabled.run())
        }

    @Test fun verifiedPresenceRefreshesActualStateThenWindow() =
        runTest {
            val calls = mutableListOf<String>()
            val runner =
                WatchCatchUp({ true }, { setOf("link") }, { calls += "state" }, {
                    calls += "window"
                    true
                })
            assertTrue(runner.run())
            assertEquals(listOf("state", "window"), calls)
        }
}
