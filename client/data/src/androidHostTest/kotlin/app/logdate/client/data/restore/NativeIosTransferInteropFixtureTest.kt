package app.logdate.client.data.restore

import app.logdate.client.device.crypto.AndroidCryptoManager
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.identity.ExistingCanonicalOwner
import app.logdate.client.device.restore.PrivateOsCustodyRecord
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.domain.export.archive.StagingDirectoryArchiveContainer
import app.logdate.client.domain.restore.archive3.NativeArchive3Bundle
import app.logdate.client.domain.restore.archive3.NativeArchive3SnapshotInput
import app.logdate.client.domain.restore.archive3.NativeArchive3SnapshotWriter
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.profile.LogDateProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.source
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Emits a source-produced bundle for the native Apple decoder's interoperability test. */
class NativeIosTransferInteropFixtureTest {
    @Test
    fun `android archive and private custody form the exact Apple input tree`() {
        runBlocking {
            val scratch = Files.createTempDirectory("native-ios-interoperability-").toFile()
            try {
                val root = File(scratch, "app/files/logdate-os-transfer")
                val archive = File(root, "archive")
                val journalId = Uuid.parse("11111111-1111-1111-1111-111111111111")
                val noteId = Uuid.parse("22222222-2222-2222-2222-222222222222")
                val draftId = Uuid.parse("33333333-3333-3333-3333-333333333333")
                val blockId = Uuid.parse("44444444-4444-4444-4444-444444444444")
                val ownerId = "55555555-5555-5555-5555-555555555555"
                val timestamp = Instant.parse("2026-10-23T00:00:00Z")
                NativeArchive3SnapshotWriter.write(
                    NativeArchive3SnapshotInput(
                        exportedAt = timestamp.toString(),
                        journals = listOf(Journal(id = journalId, title = "October", created = timestamp, lastUpdated = timestamp)),
                        notes =
                            listOf(
                                JournalNote.Text(
                                    uid = noteId,
                                    creationTimestamp = timestamp,
                                    lastUpdated = timestamp,
                                    content = "Hello",
                                ),
                            ),
                        journalIdsByNote = mapOf(noteId to listOf(journalId)),
                        drafts =
                            listOf(
                                EditorDraft(
                                    id = draftId,
                                    blocks = listOf(SerializableTextBlock(blockId, timestamp, content = "Unfinished")),
                                    selectedJournalIds = listOf(journalId),
                                    createdAt = timestamp,
                                    lastModifiedAt = timestamp,
                                ),
                            ) +
                                NativeArchive3EntryDrafts.convert(
                                    listOf(
                                        EntryDraft(
                                            id = Uuid.parse("77777777-7777-7777-7777-777777777777"),
                                            notes =
                                                listOf(
                                                    JournalNote.Text(
                                                        uid = Uuid.parse("88888888-8888-8888-8888-888888888888"),
                                                        creationTimestamp = timestamp,
                                                        lastUpdated = timestamp,
                                                        content = "Still writing",
                                                    ),
                                                ),
                                            createdAt = timestamp,
                                            updatedAt = timestamp,
                                            selectedJournalIds = listOf(journalId),
                                        ),
                                    ),
                                    ownerBound = false,
                                ),
                        profile = LogDateProfile(),
                        places = emptyList(),
                        locationHistory = emptyList(),
                        originals = emptyList(),
                        unsupportedPopulatedCategories = emptySet(),
                        validateContext = {},
                    ),
                    StagingDirectoryArchiveContainer(FileSystem.SYSTEM, archive.path.toPath()),
                )
                val names =
                    archive
                        .walkTopDown()
                        .filter(File::isFile)
                        .map { it.relativeTo(archive).invariantSeparatorsPath }
                        .toList()
                val bundle = NativeArchive3Bundle.read(names, { File(archive, it).source() })
                assertEquals(1, bundle.notes.size)
                assertEquals(2, bundle.drafts.size)
                assertEquals("f0856d4dabf2686c5887ed721e8f432d88b33d6576bf0a427a5c22fbf793eeaa", bundle.checksumsSha256)
                assertEquals("9c80a5b98b08373ce0642fe68535c8c820c85e2e3c31853f8b25f6e6377a5f9e", bundle.manifestSha256)
                val custody = File(root, ".logdate-os-private/custody-v1.json")
                assertTrue(custody.parentFile!!.mkdirs())
                val emptyStorage =
                    object : SecureStorage {
                        override suspend fun getString(key: String): String? = null

                        override suspend fun putString(
                            key: String,
                            value: String,
                        ): Unit = error("Read-only fixture")

                        override suspend fun remove(key: String): Unit = error("Read-only fixture")

                        override suspend fun clear(): Unit = error("Read-only fixture")

                        override fun observeString(key: String): Flow<String?> = error("Read-only fixture")

                        override fun observeAll(): Flow<Map<String, String>> = error("Read-only fixture")

                        override suspend fun encrypt(data: ByteArray): ByteArray = error("Read-only fixture")

                        override suspend fun decrypt(data: ByteArray): ByteArray? = error("Read-only fixture")
                    }
                val identity = IdentityKeyManager(emptyStorage, AndroidCryptoManager())
                val record =
                    assertNotNull(
                        PrivateOsCustodyRecord.prepareRootlessPlaintext(
                            transportFlags = 9,
                            transferId = Uuid.parse("66666666-6666-6666-6666-666666666666"),
                            sourceGeneration = ByteArray(32) { 7 },
                            owner = ExistingCanonicalOwner(ownerId, false),
                            checksumsFileSha256 = bundle.checksumsSha256,
                            manifestSha256 = bundle.manifestSha256,
                        ) { identity.observeRootlessKeys() },
                    )
                record.withEncodedBytes { custody.writeBytes(it) }
                assertEquals(
                    "0e9a678434c28fa62651e2c7f64318ad30949b1a20e2139f39e53e3423288efd",
                    MessageDigest.getInstance("SHA-256").digest(custody.readBytes()).joinToString("") { "%02x".format(it) },
                )
                val emitted = mutableListOf<String>()
                emitNativeIosStagedFiles(root) { emitted += it.relativeTo(scratch).invariantSeparatorsPath }
                assertEquals(
                    names.sorted().map { "app/files/logdate-os-transfer/archive/$it" } +
                        "app/files/logdate-os-transfer/.logdate-os-private/custody-v1.json",
                    emitted,
                )
                System.getenv("LOGDATE_IOS_INTEROP_FIXTURE_OUTPUT")?.let { destination ->
                    val output = File(destination)
                    require(!output.exists()) { "Fixture destination must be new" }
                    check(output.mkdirs())
                    check(File(scratch, "app").copyRecursively(File(output, "app")))
                }
            } finally {
                check(scratch.deleteRecursively())
            }
        }
    }
}
