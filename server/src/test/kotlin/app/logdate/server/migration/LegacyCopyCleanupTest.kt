package app.logdate.server.migration

import app.logdate.server.logdate.LogDateBlobNamespace
import app.logdate.server.logdate.LogDateBlobWriteRequest
import app.logdate.server.logdate.LogDateMedia
import app.logdate.shared.model.sync.DeviceId
import app.logdate.shared.model.sync.LegacyRecordKinds
import app.logdate.shared.model.sync.SpaceMigrationErrorCodes
import app.logdate.shared.model.sync.SpaceMigrationPhase
import app.logdate.shared.model.sync.SpaceMigrationSwitchBackRequest
import app.logdate.space.SpaceRef
import kotlinx.coroutines.test.runTest
import studio.hypertext.atproto.identity.AtprotoDid
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** The legacy copy of a moved journal stays for 30 days, then goes, and the personal space is untouched. */
class LegacyCopyCleanupTest {
    @Test
    fun `the legacy copy stays for 30 days after the switch and is deleted after`() =
        runTest {
            val fixture = MigrationFixture()
            val account = fixture.switchedAccount()
            val media = fixture.legacyMedia(account)
            fixture.addLocationRecord(account, "place-one")

            fixture.clock.advance(30.days - 1.hours)
            assertEquals(LegacyCleanupResult(0, 0), fixture.cleanup.runOnce())
            assertEquals(1, fixture.legacyCount(account, LegacyRecordKinds.ENTRIES))

            fixture.clock.advance(2.hours)
            assertEquals(LegacyCleanupResult(1, 0), fixture.cleanup.runOnce())

            LegacyRecordKinds.ALL.forEach { kind -> assertEquals(0, fixture.legacyCount(account, kind), kind) }
            assertNull(fixture.mediaRecords.getMedia(account, media.mediaId))
            assertNull(fixture.storage.getBlob(requireNotNull(media.storagePath)))
            assertNull(
                fixture.repositories.readHead(AtprotoDid.require(fixture.did(account))).getOrThrow(),
                "The public repository is empty",
            )
            val status = fixture.service.status(account)
            assertEquals(SpaceMigrationPhase.CLEANED, status.phase)
            assertNotNull(status.cleanedAt)
            val did = fixture.did(account)
            assertEquals(
                1,
                fixture.spaces
                    .index(SpaceRef.personal(did).uri, did)
                    .live.size,
                "The personal space keeps every record",
            )
            assertEquals(LegacyCleanupResult(0, 0), fixture.cleanup.runOnce(), "Nothing is left to delete")
        }

    @Test
    fun `a failed deletion is tried again and ends the window for switching back`() =
        runTest {
            val fixture = MigrationFixture()
            var failNext = true
            val flaky =
                LegacyCopyCleanup(fixture.store, fixture.accounts, {
                    if (failNext) {
                        failNext = false
                        error("Storage is unavailable")
                    }
                    fixture.eraser.erase(it)
                }, fixture.config, fixture.clock)
            val account = fixture.switchedAccount()
            fixture.clock.advance(31.days)

            assertEquals(LegacyCleanupResult(0, 1), flaky.runOnce())
            assertEquals(SpaceMigrationPhase.CLEANED, fixture.service.status(account).phase)
            assertNull(fixture.service.status(account).cleanedAt)
            fixture.service
                .switchBack(account, SpaceMigrationSwitchBackRequest("move-1", 1))
                .refusedWith(SpaceMigrationErrorCodes.NOT_ACTIVE)

            assertEquals(LegacyCleanupResult(1, 0), flaky.runOnce())
            assertEquals(0, fixture.legacyCount(account, LegacyRecordKinds.ENTRIES))
        }

    @Test
    fun `a switched-back journal keeps its legacy copy`() =
        runTest {
            val fixture = MigrationFixture()
            val account = fixture.switchedAccount()
            fixture.service.hold(account, "move-1").done()
            fixture.clock.advance(fixture.config.holdGrace)
            fixture.service
                .switchBack(
                    account,
                    SpaceMigrationSwitchBackRequest("move-1", fixture.service.status(account).switchedAtSeq!!),
                ).done()

            fixture.clock.advance(60.days)

            assertEquals(LegacyCleanupResult(0, 0), fixture.cleanup.runOnce())
            assertEquals(1, fixture.legacyCount(account, LegacyRecordKinds.ENTRIES))
        }
}

internal suspend fun MigrationFixture.legacyCount(
    account: UUID,
    kind: String,
): Int =
    inventory
        .read(account)
        .kinds
        .single { it.kind == kind }
        .count

internal fun MigrationFixture.legacyMedia(
    account: UUID,
    bytes: ByteArray = "LDCE2".encodeToByteArray() + ByteArray(4096) { it.toByte() },
    inline: Boolean = false,
): LogDateMedia {
    val mediaId = UUID.randomUUID().toString()
    val path =
        if (inline) {
            null
        } else {
            storage.putBlob(LogDateBlobWriteRequest(account, LogDateBlobNamespace.MEDIA, mediaId, "photo.jpg", "image/jpeg", bytes))
        }
    return mediaRecords.upsertMedia(
        account,
        LogDateMedia(
            mediaId = mediaId,
            contentId = UUID.randomUUID().toString(),
            userId = account,
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            sizeBytes = bytes.size.toLong(),
            data = if (inline) bytes else ByteArray(0),
            storagePath = path,
            createdAt = clock.now().toEpochMilliseconds(),
            version = 0,
            deviceId = DeviceId("device"),
        ),
    )
}
