package app.logdate.server.routes.support

import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

fun contentUploadBody(
    id: String,
    type: String = "TEXT",
    content: String? = "content-$id",
    mediaUri: String? = null,
    createdAt: Long = 1L,
    lastUpdated: Long = createdAt,
    deviceId: String = "dev-1",
): String =
    """
    {
      "id": "$id",
      "type": "$type",
      "content": ${content?.let { "\"$it\"" } ?: "null"},
      "mediaUri": ${mediaUri?.let { "\"$it\"" } ?: "null"},
      "createdAt": $createdAt,
      "lastUpdated": $lastUpdated,
      "deviceId": "$deviceId"
    }
    """.trimIndent()

fun contentUpdateBody(
    content: String? = null,
    mediaUri: String? = null,
    durationMs: Long = 0L,
    lastUpdated: Long = 2L,
    deviceId: String = "dev-1",
    knownVersion: Long? = null,
): String {
    val versionConstraint =
        knownVersion?.let { """{"type":"known","serverVersion":$it}""" } ?: """{"type":"none"}"""
    return (
        """
        {
          "content": ${content?.let { "\"$it\"" } ?: "null"},
          "mediaUri": ${mediaUri?.let { "\"$it\"" } ?: "null"},
          "durationMs": $durationMs,
          "lastUpdated": $lastUpdated,
          "deviceId": "$deviceId",
          "versionConstraint": $versionConstraint
        }
        """.trimIndent()
    )
}

fun journalUploadBody(
    id: String,
    title: String = "title-$id",
    description: String = "description-$id",
    createdAt: Long = 1L,
    lastUpdated: Long = createdAt,
    deviceId: String = "dev-1",
): String =
    """
    {
      "id": "$id",
      "title": "$title",
      "description": "$description",
      "createdAt": $createdAt,
      "lastUpdated": $lastUpdated,
      "deviceId": "$deviceId"
    }
    """.trimIndent()

fun journalUpdateBody(
    title: String? = null,
    description: String? = null,
    lastUpdated: Long = 2L,
    deviceId: String = "dev-1",
    knownVersion: Long? = null,
): String {
    val versionConstraint =
        knownVersion?.let { """{"type":"known","serverVersion":$it}""" } ?: """{"type":"none"}"""
    return (
        """
        {
          "title": ${title?.let { "\"$it\"" } ?: "null"},
          "description": ${description?.let { "\"$it\"" } ?: "null"},
          "lastUpdated": $lastUpdated,
          "deviceId": "$deviceId",
          "versionConstraint": $versionConstraint
        }
        """.trimIndent()
    )
}

fun associationUploadBody(
    journalId: String,
    contentId: String,
    createdAt: Long = 1L,
    deviceId: String = "dev-1",
): String =
    """
    {
      "associations": [
        {
          "journalId": "$journalId",
          "contentId": "$contentId",
          "createdAt": $createdAt,
          "deviceId": "$deviceId"
        }
      ]
    }
    """.trimIndent()

fun associationDeleteBody(
    journalId: String,
    contentId: String,
): String =
    """
    {
      "associations": [
        {
          "journalId": "$journalId",
          "contentId": "$contentId"
        }
      ]
    }
    """.trimIndent()

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

fun mediaMultipartWithFields(
    includeContentId: Boolean,
    includeFileName: Boolean,
    includeMimeType: Boolean,
    includeSizeBytes: Boolean,
    includeDeviceId: Boolean,
    includeData: Boolean,
    sizeBytes: Long,
    payload: ByteArray,
): MultiPartFormDataContent =
    MultiPartFormDataContent(
        formData {
            if (includeContentId) append("contentId", "content-1")
            if (includeFileName) append("fileName", "photo.jpg")
            if (includeMimeType) append("mimeType", "image/jpeg")
            if (includeSizeBytes) append("sizeBytes", sizeBytes.toString())
            if (includeDeviceId) append("deviceId", "device-1")
            if (includeData) {
                append(
                    key = "data",
                    value = payload,
                    headers =
                        Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                            append(HttpHeaders.ContentType, "image/jpeg")
                        },
                )
            }
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

fun backupMultipartWithFields(
    includeDeviceId: Boolean,
    includeManifest: Boolean,
    includeData: Boolean,
    payload: ByteArray,
    fileName: String = "backup.bin",
): MultiPartFormDataContent =
    MultiPartFormDataContent(
        formData {
            if (includeDeviceId) append("deviceId", "device-1")
            if (includeManifest) append("manifest", "{}")
            if (includeData) {
                append(
                    key = "data",
                    value = payload,
                    headers =
                        Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            append(HttpHeaders.ContentType, "application/octet-stream")
                        },
                )
            }
        },
    )
