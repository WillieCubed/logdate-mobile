package app.logdate.client.sync.cloud

/**
 * Authenticated remote backup operations.
 *
 * Backup bytes are accepted only while the caller has an access token. The server encrypts the
 * payload before storage and decrypts it only for this authenticated download, so this source
 * deliberately does not expose storage URLs as a separate unauthenticated path.
 */
interface CloudBackupDataSource {
    suspend fun uploadBackup(
        accessToken: String,
        backup: BackupFile,
    ): Result<BackupUploadResult>

    suspend fun listBackups(accessToken: String): Result<List<BackupMetadata>>

    suspend fun downloadBackup(
        accessToken: String,
        backupId: String,
    ): Result<BackupFile>

    suspend fun deleteBackup(
        accessToken: String,
        backupId: String,
    ): Result<Unit>
}

data class BackupFile(
    val deviceId: String,
    val manifest: String,
    val data: ByteArray,
) {
    init {
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
        require(manifest.isNotBlank()) { "manifest must not be blank" }
        require(data.isNotEmpty()) { "backup data must not be empty" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupFile) return false
        return deviceId == other.deviceId && manifest == other.manifest && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = (31 * deviceId.hashCode() + manifest.hashCode()) * 31 + data.contentHashCode()
}

data class BackupUploadResult(
    val id: String,
    val createdAt: Long,
    val sizeBytes: Long,
)

data class BackupMetadata(
    val id: String,
    val deviceId: String,
    val manifest: String,
    val createdAt: Long,
    val sizeBytes: Long,
    val downloadUrl: String,
)

class DefaultCloudBackupDataSource(
    private val cloudApiClient: CloudApiClient,
) : CloudBackupDataSource {
    override suspend fun uploadBackup(
        accessToken: String,
        backup: BackupFile,
    ): Result<BackupUploadResult> =
        cloudApiClient
            .uploadBackup(
                accessToken,
                BackupUploadRequest(
                    deviceId = backup.deviceId,
                    manifest = backup.manifest,
                    data = backup.data,
                ),
            ).map { response ->
                BackupUploadResult(response.id, response.createdAt, response.sizeBytes)
            }

    override suspend fun listBackups(accessToken: String): Result<List<BackupMetadata>> =
        cloudApiClient.listBackups(accessToken).map { response ->
            response.backups.map { it.toMetadata() }
        }

    override suspend fun downloadBackup(
        accessToken: String,
        backupId: String,
    ): Result<BackupFile> =
        cloudApiClient.downloadBackup(accessToken, backupId).map { response ->
            BackupFile(
                deviceId = response.metadata.deviceId,
                manifest = response.metadata.manifest,
                data = response.data,
            )
        }

    override suspend fun deleteBackup(
        accessToken: String,
        backupId: String,
    ): Result<Unit> = cloudApiClient.deleteBackup(accessToken, backupId)
}

private fun BackupInfoResponse.toMetadata(): BackupMetadata =
    BackupMetadata(
        id = id,
        deviceId = deviceId,
        manifest = manifest,
        createdAt = createdAt,
        sizeBytes = sizeBytes,
        downloadUrl = downloadUrl,
    )
