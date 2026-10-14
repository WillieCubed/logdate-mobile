package app.logdate.client.watch

import app.logdate.feature.core.settings.ui.watch.WatchConnectionState
import kotlin.test.Test
import kotlin.test.assertEquals

class WearAppConnectionTest {
    private val watch = WatchNodeSnapshot("watch-a", "Pixel Watch", true)
    private val transport = WatchTransportSnapshot(listOf(watch), listOf(watch))

    @Test
    fun `reachable installed app still requires verified link`() {
        assertEquals(WatchConnectionState.NeedsAssociation("Pixel Watch", "watch-a"), resolveWearAppConnection(transport, emptySet()))
    }

    @Test
    fun `verified exact peer is connected without manufacturer CDM privileges`() {
        assertEquals(WatchConnectionState.Connected("Pixel Watch", "watch-a"), resolveWearAppConnection(transport, setOf("watch-a")))
    }

    @Test
    fun `another watch cannot satisfy selected peers link`() {
        assertEquals(WatchConnectionState.NeedsAssociation("Pixel Watch", "watch-a"), resolveWearAppConnection(transport, setOf("watch-b")))
    }
}
