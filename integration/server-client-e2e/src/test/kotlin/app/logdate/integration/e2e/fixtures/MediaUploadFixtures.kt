package app.logdate.integration.e2e.fixtures

import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.MediaUpload
import app.logdate.client.sync.cloud.MediaUploadRequest
import app.logdate.client.sync.cloud.MediaUploadResponse
import app.logdate.integration.e2e.harness.ServerClientE2EHarness
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.io.Buffer

/** Uploads an in-memory payload; the app itself streams uploads from disk. */
suspend fun CloudApiClient.uploadMedia(
    accessToken: String,
    media: MediaUploadRequest,
): Result<MediaUploadResponse> =
    uploadMedia(
        accessToken,
        MediaUpload(
            contentId = media.contentId,
            fileName = media.fileName,
            mimeType = media.mimeType,
            sizeBytes = media.sizeBytes,
            deviceId = media.deviceId,
        ) { Buffer().apply { write(media.data) } },
    )

/**
 * Posts a media upload whose declared sizeBytes disagrees with its body. The app cannot send one
 * (its body length is the declared size), so this goes around the API client.
 */
suspend fun ServerClientE2EHarness.uploadMediaDeclaringSize(
    accessToken: String,
    declaredSizeBytes: Long,
    data: ByteArray,
): HttpResponse =
    httpClient.post("$baseUrl/media") {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
        setBody(
            MultiPartFormDataContent(
                formData {
                    append("contentId", "content-err")
                    append("fileName", "bad.bin")
                    append("mimeType", "application/octet-stream")
                    append("sizeBytes", declaredSizeBytes.toString())
                    append("deviceId", "device-a")
                    append(
                        key = "data",
                        value = data,
                        headers =
                            Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"bad.bin\"")
                                append(HttpHeaders.ContentType, "application/octet-stream")
                            },
                    )
                },
            ),
        )
    }
