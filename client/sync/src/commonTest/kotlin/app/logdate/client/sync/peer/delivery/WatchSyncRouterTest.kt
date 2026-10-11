package app.logdate.client.sync.peer.delivery

import app.logdate.client.networking.saver.NetworkConnectionType
import app.logdate.client.networking.saver.NetworkSaverState
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.metadata.PendingOperation
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests [WatchSyncRouter], the table that decides where each watch change goes next. */
class WatchSyncRouterTest {
    private val router = WatchSyncRouter()
    private val ready =
        RouteConditions(
            phone = PhoneReach.NEARBY,
            phoneCloud = CloudPresence.SIGNED_IN,
            linked = true,
            serverReady = true,
            network = WatchNetwork.UNMETERED,
            charging = false,
        )

    @Test
    fun `a record the phone backed up settles`() {
        assertEquals(Route.Settle, router.route(input(state = DeliveryState.BACKED_UP)))
    }

    @Test
    fun `a record the phone deleted settles`() {
        assertEquals(Route.Settle, router.route(input(state = DeliveryState.DELETED_ON_PHONE)))
    }

    @Test
    fun `a new record goes to a phone nearby`() {
        assertEquals(Route.SendToPhone, router.route(input()))
    }

    @Test
    fun `a record on a phone with no account settles`() {
        val signedOut = ready.copy(phoneCloud = CloudPresence.NO_ACCOUNT)

        assertEquals(Route.Settle, router.route(input(state = DeliveryState.ON_PHONE, conditions = signedOut)))
    }

    @Test
    fun `a record on a signed-in phone waits for its backup`() {
        assertEquals(Route.Wait(HoldReason.PHONE_BACKING_UP), router.route(input(state = DeliveryState.ON_PHONE, ageMs = 10 * 60_000L)))
    }

    @Test
    fun `a record the phone has not backed up after fifteen minutes goes straight to the cloud`() {
        assertEquals(Route.UploadDirect, router.route(input(state = DeliveryState.ON_PHONE, ageMs = 15 * 60_000L)))
    }

    @Test
    fun `after fifteen minutes a watch that cannot upload still waits for the phone`() {
        val unlinked = ready.copy(linked = false)

        assertEquals(
            Route.Wait(HoldReason.PHONE_BACKING_UP),
            router.route(input(state = DeliveryState.ON_PHONE, ageMs = 15 * 60_000L, conditions = unlinked)),
        )
    }

    @Test
    fun `a phone whose account is not known yet means waiting for a receipt`() {
        val unknown = ready.copy(phoneCloud = CloudPresence.UNKNOWN)

        assertEquals(Route.Wait(HoldReason.AWAITING_RECEIPT), router.route(input(state = DeliveryState.ON_PHONE, conditions = unknown)))
    }

    @Test
    fun `a record sent moments ago waits for its receipt`() {
        assertEquals(Route.Wait(HoldReason.AWAITING_RECEIPT), router.route(input(state = DeliveryState.SENT, ageMs = 10_000)))
    }

    @Test
    fun `a record sent long ago is sent again`() {
        assertEquals(Route.SendToPhone, router.route(input(state = DeliveryState.SENT, ageMs = 31_000)))
    }

    @Test
    fun `with the phone out of reach a linked watch uploads directly`() {
        assertEquals(Route.UploadDirect, router.route(input(conditions = ready.copy(phone = PhoneReach.UNREACHABLE))))
    }

    @Test
    fun `with the phone out of reach an unlinked watch waits for it`() {
        val away = ready.copy(phone = PhoneReach.UNREACHABLE, linked = false)

        assertEquals(Route.Wait(HoldReason.PHONE_UNREACHABLE), router.route(input(conditions = away)))
    }

    @Test
    fun `a server without create if absent is never uploaded to`() {
        val away = ready.copy(phone = PhoneReach.UNREACHABLE, serverReady = false)

        assertEquals(Route.Wait(HoldReason.SERVER_TOO_OLD), router.route(input(conditions = away)))
    }

    @Test
    fun `the Bluetooth link is never used for a direct upload`() {
        val away = ready.copy(phone = PhoneReach.UNREACHABLE, network = WatchNetwork.BLUETOOTH_PROXY)

        assertEquals(Route.Wait(HoldReason.BLUETOOTH_ONLY), router.route(input(conditions = away)))
    }

    @Test
    fun `no internet means waiting`() {
        val away = ready.copy(phone = PhoneReach.UNREACHABLE, network = WatchNetwork.NONE)

        assertEquals(Route.Wait(HoldReason.NO_INTERNET), router.route(input(conditions = away)))
    }

    @Test
    fun `audio on mobile data waits for wifi or the charger`() {
        val lte = ready.copy(phone = PhoneReach.UNREACHABLE, network = WatchNetwork.METERED)

        assertEquals(Route.Wait(HoldReason.AUDIO_WAITS_FOR_WIFI), router.route(input(hasMedia = true, conditions = lte)))
        assertEquals(Route.UploadDirect, router.route(input(hasMedia = true, conditions = lte.copy(charging = true))))
        assertEquals(Route.UploadDirect, router.route(input(hasMedia = false, conditions = lte)))
    }

    @Test
    fun `audio with the phone reachable only through the relay goes to the cloud`() {
        assertEquals(Route.UploadDirect, router.route(input(hasMedia = true, conditions = ready.copy(phone = PhoneReach.RELAY_ONLY))))
    }

    @Test
    fun `text with the phone reachable only through the relay goes over the relay`() {
        assertEquals(Route.SendToPhone, router.route(input(hasMedia = false, conditions = ready.copy(phone = PhoneReach.RELAY_ONLY))))
    }

    @Test
    fun `an edit never goes straight to the cloud`() {
        val away = ready.copy(phone = PhoneReach.UNREACHABLE)

        assertEquals(Route.Wait(HoldReason.EDIT_NEEDS_PHONE), router.route(input(operation = PendingOperation.UPDATE, conditions = away)))
        assertEquals(Route.SendToPhone, router.route(input(operation = PendingOperation.UPDATE)))
    }

    @Test
    fun `a record the phone cannot read goes to the cloud when it can`() {
        assertEquals(Route.UploadDirect, router.route(input(state = DeliveryState.REJECTED)))
        assertEquals(
            Route.Wait(HoldReason.PHONE_CANNOT_READ),
            router.route(input(state = DeliveryState.REJECTED, conditions = ready.copy(linked = false))),
        )
    }

    @Test
    fun `a record waiting for the cloud waits until it can go`() {
        assertEquals(Route.UploadDirect, router.route(input(state = DeliveryState.CLOUD_PENDING)))
        assertEquals(
            Route.Wait(HoldReason.NO_INTERNET),
            router.route(input(state = DeliveryState.CLOUD_PENDING, conditions = ready.copy(network = WatchNetwork.NONE))),
        )
    }

    @Test
    fun `the watch's network is read from its connection`() {
        assertEquals(WatchNetwork.BLUETOOTH_PROXY, watchNetworkOf(NetworkSaverState(false, NetworkConnectionType.BLUETOOTH_PROXY)))
        assertEquals(WatchNetwork.METERED, watchNetworkOf(NetworkSaverState(false, NetworkConnectionType.CELLULAR)))
        assertEquals(WatchNetwork.UNMETERED, watchNetworkOf(NetworkSaverState(false, NetworkConnectionType.WIFI)))
        assertEquals(WatchNetwork.NONE, watchNetworkOf(NetworkSaverState(false, NetworkConnectionType.WIFI, isValidated = false)))
    }

    private fun input(
        state: DeliveryState = DeliveryState.NEW,
        operation: PendingOperation = PendingOperation.CREATE,
        hasMedia: Boolean = false,
        ageMs: Long = 0,
        conditions: RouteConditions = ready,
    ) = RouteInput(operation, hasMedia, state, ageMs, conditions)
}
