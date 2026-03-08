package app.logdate.integration.e2e.errors

import app.logdate.client.sync.cloud.ContentUpdateRequest
import app.logdate.client.sync.cloud.ContentUploadRequest
import app.logdate.client.sync.cloud.DeviceId
import app.logdate.client.sync.cloud.MediaUploadRequest
import app.logdate.integration.e2e.fixtures.assertCloudError
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.shared.model.sync.VersionConstraint
import kotlin.random.Random
import kotlin.test.Test

class SyncClientServerErrorMatrixE2ETest {
    @Test
    fun `sync endpoints map auth validation conflict and not-found errors`() =
        kotlinx.coroutines.test.runTest {
            withServerClientHarness {
                val username = "sync_err_${Random.nextInt(1000, 9999)}"
                val complete = apiClient.createAccountWithSyntheticPasskey(username)
                val accessToken = complete.data.tokens.accessToken
                val now = 1_700_000_100_000L

                val uploadWithInvalidToken =
                    apiClient.uploadContent(
                        accessToken = "bad",
                        content =
                            ContentUploadRequest(
                                id = "content-err",
                                type = "TEXT",
                                content = "x",
                                mediaUri = null,
                                createdAt = now,
                                lastUpdated = now,
                                deviceId = DeviceId("device-a"),
                            ),
                    )
                assertCloudError(uploadWithInvalidToken, expectedCode = "UNAUTHORIZED", expectedStatus = 401)

                val uploadContent =
                    apiClient.uploadContent(
                        accessToken = accessToken,
                        content =
                            ContentUploadRequest(
                                id = "content-err",
                                type = "TEXT",
                                content = "x",
                                mediaUri = null,
                                createdAt = now,
                                lastUpdated = now,
                                deviceId = DeviceId("device-a"),
                            ),
                    )
                require(uploadContent.isSuccess) { "Expected content upload success before conflict scenario" }

                val updateConflict =
                    apiClient.updateContent(
                        accessToken = accessToken,
                        contentId = "content-err",
                        content =
                            ContentUpdateRequest(
                                content = "conflict",
                                lastUpdated = now + 100,
                                deviceId = DeviceId("device-a"),
                                versionConstraint = VersionConstraint.Known(serverVersion = 0),
                            ),
                    )
                assertCloudError(updateConflict, expectedCode = "CONFLICT", expectedStatus = 409)

                val mediaSizeMismatch =
                    apiClient.uploadMedia(
                        accessToken = accessToken,
                        media =
                            MediaUploadRequest(
                                contentId = "content-err",
                                fileName = "bad.bin",
                                mimeType = "application/octet-stream",
                                sizeBytes = 99,
                                data = byteArrayOf(1, 2, 3),
                                deviceId = DeviceId("device-a"),
                            ),
                    )
                assertCloudError(mediaSizeMismatch, expectedCode = "VALIDATION_ERROR", expectedStatus = 400)

                val missingMedia = apiClient.downloadMedia(accessToken = accessToken, mediaId = "missing-media")
                assertCloudError(missingMedia, expectedCode = "NOT_FOUND", expectedStatus = 404)
            }
        }
}
