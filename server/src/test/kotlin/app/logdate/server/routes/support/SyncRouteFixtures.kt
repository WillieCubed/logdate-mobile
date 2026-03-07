package app.logdate.server.routes.support

import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

fun mediaUploadMultipartContent(
    contentId: String,
    fileName: String,
    mimeType: String,
    data: ByteArray,
    deviceId: String = "dev-1",
    declaredSizeBytes: Long = data.size.toLong(),
): MultiPartFormDataContent =
    MultiPartFormDataContent(
        formData {
            append("contentId", contentId)
            append("fileName", fileName)
            append("mimeType", mimeType)
            append("sizeBytes", declaredSizeBytes.toString())
            append("deviceId", deviceId)
            append(
                key = "data",
                value = data,
                headers =
                    Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        append(HttpHeaders.ContentType, mimeType)
                    },
            )
        },
    )

fun backupUploadMultipartContent(
    deviceId: String,
    manifest: String,
    data: ByteArray,
    fileName: String = "backup.bin",
): MultiPartFormDataContent =
    MultiPartFormDataContent(
        formData {
            append("deviceId", deviceId)
            append("manifest", manifest)
            append(
                key = "data",
                value = data,
                headers =
                    Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        append(HttpHeaders.ContentType, "application/octet-stream")
                    },
            )
        },
    )
