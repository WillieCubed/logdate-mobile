package app.logdate.client.watch

import app.logdate.feature.core.settings.ui.watch.WatchConnectionState
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchConnectionStateResolverTest {
    @Test
    fun `connected associated watch resolves to connected`() {
        val state =
            resolveWatchConnectionState(
                association =
                    WatchAssociationSnapshot.Associated(
                        displayName = "Pixel Watch",
                    ),
                transport =
                    WatchTransportSnapshot(
                        connectedNodes =
                            listOf(
                                WatchNodeSnapshot(
                                    id = "watch-1",
                                    displayName = "Pixel Watch",
                                    isNearby = true,
                                ),
                            ),
                        appNodes =
                            listOf(
                                WatchNodeSnapshot(
                                    id = "watch-1",
                                    displayName = "Pixel Watch",
                                    isNearby = true,
                                ),
                            ),
                    ),
            )

        assertEquals(
            WatchConnectionState.Connected(
                watchName = "Pixel Watch",
                lastSynced = null,
                pendingCount = 0,
            ),
            state,
        )
    }

    @Test
    fun `connected unassociated watch resolves to needs association`() {
        val state =
            resolveWatchConnectionState(
                association = WatchAssociationSnapshot.Unassociated,
                transport =
                    WatchTransportSnapshot(
                        connectedNodes =
                            listOf(
                                WatchNodeSnapshot(
                                    id = "watch-1",
                                    displayName = "Galaxy Watch",
                                    isNearby = true,
                                ),
                            ),
                    ),
            )

        assertEquals(
            WatchConnectionState.NeedsAssociation("Galaxy Watch"),
            state,
        )
    }

    @Test
    fun `associated watch without reachable node resolves to out of range`() {
        val state =
            resolveWatchConnectionState(
                association = WatchAssociationSnapshot.Associated(displayName = "Pixel Watch"),
                transport = WatchTransportSnapshot(),
            )

        assertEquals(
            WatchConnectionState.OutOfRange(
                watchName = "Pixel Watch",
                lastSynced = null,
            ),
            state,
        )
    }

    @Test
    fun `legacy unsupported association falls back to app install state`() {
        val state =
            resolveWatchConnectionState(
                association = WatchAssociationSnapshot.Unsupported,
                transport =
                    WatchTransportSnapshot(
                        connectedNodes =
                            listOf(
                                WatchNodeSnapshot(
                                    id = "watch-1",
                                    displayName = "Pixel Watch",
                                    isNearby = true,
                                ),
                            ),
                        appNodes = emptyList(),
                    ),
            )

        assertEquals(
            WatchConnectionState.AppNotInstalled("Pixel Watch"),
            state,
        )
    }

    @Test
    fun `pending association always resolves to pending state`() {
        val state =
            resolveWatchConnectionState(
                association = WatchAssociationSnapshot.Pending,
                transport =
                    WatchTransportSnapshot(
                        connectedNodes =
                            listOf(
                                WatchNodeSnapshot(
                                    id = "watch-1",
                                    displayName = "Pixel Watch",
                                    isNearby = true,
                                ),
                            ),
                    ),
            )

        assertEquals(WatchConnectionState.AssociationPending, state)
    }
}
