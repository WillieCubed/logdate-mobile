package app.logdate.client.sync.datalayer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteCameraCaptureResultDataMapperTest {
    @Test
    fun `saved result round trips through string data map`() {
        val result =
            RemoteCameraCaptureResult(
                isSaved = true,
                message = "Remote photo saved",
                mediaType = "photo",
            )

        val restored =
            RemoteCameraCaptureResultDataMapper.fromDataMap(
                RemoteCameraCaptureResultDataMapper.toDataMap(result),
            )

        assertTrue(restored.isSaved)
        assertEquals("Remote photo saved", restored.message)
        assertEquals("photo", restored.mediaType)
    }

    @Test
    fun `failed result round trips through string data map`() {
        val result =
            RemoteCameraCaptureResult(
                isSaved = false,
                message = "Could not save remote camera capture",
            )

        val restored =
            RemoteCameraCaptureResultDataMapper.fromDataMap(
                RemoteCameraCaptureResultDataMapper.toDataMap(result),
            )

        assertFalse(restored.isSaved)
        assertEquals("Could not save remote camera capture", restored.message)
        assertEquals(null, restored.mediaType)
    }
}
