package app.logdate.client.location.places

import kotlin.test.Test
import kotlin.test.assertEquals

class GoogleApiKeyResolutionTest {
    @Test
    fun `explicit key wins over all other sources`() {
        assertEquals(
            expected = "explicit-key",
            actual =
                selectResolvedGoogleMapsApiKey(
                    explicitApiKey = "explicit-key",
                    manifestApiKey = "manifest-key",
                    googleServicesApiKey = "google-services-key",
                ),
        )
    }

    @Test
    fun `manifest key is used when explicit key is blank`() {
        assertEquals(
            expected = "manifest-key",
            actual =
                selectResolvedGoogleMapsApiKey(
                    explicitApiKey = "",
                    manifestApiKey = "manifest-key",
                    googleServicesApiKey = "google-services-key",
                ),
        )
    }

    @Test
    fun `google services key is used when no higher priority key is available`() {
        assertEquals(
            expected = "google-services-key",
            actual =
                selectResolvedGoogleMapsApiKey(
                    explicitApiKey = "",
                    manifestApiKey = null,
                    googleServicesApiKey = "google-services-key",
                ),
        )
    }
}
