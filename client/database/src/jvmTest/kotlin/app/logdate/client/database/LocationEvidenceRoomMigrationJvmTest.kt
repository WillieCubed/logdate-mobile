package app.logdate.client.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_47_48
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocationEvidenceRoomMigrationJvmTest {
    @Test
    fun roomValidatesTheUpgradeWithoutRewritingExistingSamples() {
        val file = Files.createTempFile("location-migration", ".db")
        val helper =
            MigrationTestHelper(
                Path.of("schemas"),
                file,
                BundledSQLiteDriver(),
                LogDateDatabase::class,
                { LogDateDatabaseConstructor.initialize() },
                emptyList(),
            )
        try {
            helper.createDatabase(47).use { connection ->
                connection.execSQL(
                    "INSERT INTO location_logs (sample_id,user_id,device_id,timestamp,logged_at,latitude,longitude,altitude," +
                        "confidence,is_genuine,capture_pipeline,capture_source,is_mock) " +
                        "VALUES ('old','user','device',1000,2000,36,-115,0,1,1,'LEGACY','MANUAL',0)",
                )
            }
            helper.runMigrationsAndValidate(48, listOf(MIGRATION_47_48)).use { connection ->
                connection.prepare("SELECT timestamp,logged_at,activity_type,time_zone_id FROM location_logs").use { row ->
                    assertTrue(row.step())
                    assertEquals(1000, row.getLong(0))
                    assertEquals(2000, row.getLong(1))
                    assertTrue(row.isNull(2))
                    assertTrue(row.isNull(3))
                }
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
