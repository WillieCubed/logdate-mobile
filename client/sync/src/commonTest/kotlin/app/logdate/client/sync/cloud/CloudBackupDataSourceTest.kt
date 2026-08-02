package app.logdate.client.sync.cloud

import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.shared.model.sync.BackupInfoResponse
import app.logdate.shared.model.sync.BackupListResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CloudBackupDataSourceTest {
    @Test
    fun `source maps upload list download and delete through authenticated api`() =
        runTest {
            val api =
                FakeCloudApiClient().apply {
                    uploadBackupResponse =
                        Result.success(
                            app.logdate.shared.model.sync.BackupUploadResponse("backup-1", 100, 2),
                        )
                    listBackupsResponse =
                        Result.success(
                            BackupListResponse(
                                listOf(BackupInfoResponse("backup-1", "device-1", "{}", 100, 2, "/backups/backup-1")),
                            ),
                        )
                    downloadBackupResponse =
                        Result.success(
                            BackupDownloadResponse(
                                BackupInfoResponse("backup-1", "device-1", "{}", 100, 2, "/backups/backup-1"),
                                byteArrayOf(1, 2),
                            ),
                        )
                }
            val source = DefaultCloudBackupDataSource(api)
            val file = BackupFile("device-1", "{}", byteArrayOf(1, 2))

            assertEquals(
                "backup-1",
                source.uploadBackup("token", file).getOrThrow().id,
            )
            assertEquals(
                "backup-1",
                source.listBackups("token").getOrThrow().single().id,
            )
            val downloaded = source.downloadBackup("token", "backup-1").getOrThrow()
            assertEquals(file.deviceId, downloaded.deviceId)
            assertContentEquals(file.data, downloaded.data)
            source.deleteBackup("token", "backup-1").getOrThrow()
            assertEquals(
                listOf("uploadBackup", "listBackups", "downloadBackup", "deleteBackup"),
                api.methodCalls.takeLast(4),
            )
        }
}
