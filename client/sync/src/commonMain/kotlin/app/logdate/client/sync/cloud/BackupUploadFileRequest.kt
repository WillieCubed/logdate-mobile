package app.logdate.client.sync.cloud

import kotlinx.io.files.Path

data class BackupUploadFileRequest(
    val deviceId: String,
    val manifest: String,
    val sourcePath: Path,
    val sizeBytes: Long,
)
