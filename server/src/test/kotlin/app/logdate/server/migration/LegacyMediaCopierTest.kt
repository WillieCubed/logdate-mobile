package app.logdate.server.migration

import app.logdate.shared.model.sync.SpaceMigrationErrorCodes
import app.logdate.space.SpaceRef
import kotlinx.coroutines.test.runTest
import studio.hypertext.atproto.repo.Cid
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/** Media the apps already encrypted becomes a space blob without another upload, named by the CID of its exact bytes. */
class LegacyMediaCopierTest {
    @Test
    fun `an app-encrypted file is copied into the personal space byte for byte`() =
        runTest {
            val fixture = MigrationFixture()
            val account = fixture.legacyAccount()
            val bytes = "LDCE2".encodeToByteArray() + ByteArray(200_000) { (it % 251).toByte() }
            val media = fixture.legacyMedia(account, bytes)
            val space = SpaceRef.personal(fixture.did(account)).uri

            val registered = fixture.copier.copy(account, space, media.mediaId).done()

            assertEquals(Cid.fromBytes(byteArrayOf(0x01, 0x55, 0x12, 0x20) + sha256(bytes)).value, registered.cid)
            assertEquals(bytes.size.toLong(), registered.size)
            val blob = assertNotNull(fixture.spaces.getBlob(space, registered.cid))
            assertNotEquals(media.storagePath, blob.storagePath, "The blob is a copy, so deleting the legacy file can't take it")
            assertContentEquals(bytes, fixture.storage.getBlob(blob.storagePath))

            val again = fixture.copier.copy(account, space, media.mediaId).done()
            assertEquals(registered, again, "Copying again names the same blob")
        }

    @Test
    fun `a file stored inline and an older LDCE1 file are copied too`() =
        runTest {
            val fixture = MigrationFixture()
            val account = fixture.legacyAccount()
            val space = SpaceRef.personal(fixture.did(account)).uri
            val inline = fixture.legacyMedia(account, "LDCE2".encodeToByteArray() + byteArrayOf(9, 9), inline = true)
            val single = fixture.legacyMedia(account, "LDCE1".encodeToByteArray() + byteArrayOf(1, 2, 3))

            assertEquals(
                7,
                fixture.copier
                    .copy(account, space, inline.mediaId)
                    .done()
                    .size,
            )
            assertEquals(
                8,
                fixture.copier
                    .copy(account, space, single.mediaId)
                    .done()
                    .size,
            )
        }

    @Test
    fun `a file the server encrypted or another account's file is refused`() =
        runTest {
            val fixture = MigrationFixture()
            val account = fixture.legacyAccount()
            val other = fixture.legacyAccount()
            val space = SpaceRef.personal(fixture.did(account)).uri
            val serverEncrypted = fixture.legacyMedia(account, "LDSM1".encodeToByteArray() + ByteArray(64))
            val plain = fixture.legacyMedia(account, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0, 0, 0))
            val theirs = fixture.legacyMedia(other)

            assertEquals(
                SpaceMigrationErrorCodes.MEDIA_NOT_CLIENT_ENCRYPTED,
                fixture.copier.copy(account, space, serverEncrypted.mediaId).code(),
            )
            assertEquals(SpaceMigrationErrorCodes.MEDIA_NOT_CLIENT_ENCRYPTED, fixture.copier.copy(account, space, plain.mediaId).code())
            assertEquals(LegacyMediaCopier.MEDIA_NOT_FOUND, fixture.copier.copy(account, space, theirs.mediaId).code())
            assertEquals(LegacyMediaCopier.MEDIA_NOT_FOUND, fixture.copier.copy(account, space, "missing").code())
        }

    private fun SpaceMigrationResult<*>.code(): String = assertIs<SpaceMigrationResult.Refused>(this).code

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
