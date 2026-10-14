package app.logdate.client.watch

import android.companion.AssociationRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertFailsWith

class WatchAssociationRequestFactoryTest {
    @Test
    fun `request scans only Bluetooth devices matching connected Wear name`() {
        val builder = mockk<AssociationRequest.Builder>(relaxed = true)
        every { builder.setDeviceProfile(any()) } returns builder
        every { builder.setDisplayName(any()) } returns builder
        every { builder.setSelfManaged(any()) } returns builder
        every { builder.setForceConfirmation(any()) } returns builder
        every { builder.addDeviceFilter(any()) } returns builder
        every { builder.setSingleDevice(any()) } returns builder
        every { builder.build() } returns mockk()
        DefaultWatchAssociationRequestFactory(builder = { builder }, filter = { mockk() })
            .createRequest(WatchNodeSnapshot("real-node", "Paired Pixel Watch", true))
        verify(exactly = 0) { builder.setSelfManaged(any()) }
        verify(exactly = 0) { builder.setForceConfirmation(any()) }
        verify { builder.setSingleDevice(true) }
        verify(exactly = 1) { builder.addDeviceFilter(any()) }
    }

    @Test
    fun `no transport identity cannot start association`() {
        assertFailsWith<IllegalArgumentException> {
            DefaultWatchAssociationRequestFactory(builder = { mockk(relaxed = true) }).createRequest()
        }
    }
}
