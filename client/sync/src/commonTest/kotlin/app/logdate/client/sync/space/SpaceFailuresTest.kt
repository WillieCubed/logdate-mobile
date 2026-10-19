package app.logdate.client.sync.space

import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.MediaTooLargeException
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.space.DataHomeBusyException
import app.logdate.space.DataHomeConflictException
import app.logdate.space.DataHomeQuotaExceededException
import app.logdate.space.DataHomeRateLimitedException
import studio.hypertext.atproto.xrpc.XrpcProtocolException
import studio.hypertext.atproto.xrpc.XrpcSerializationException
import studio.hypertext.atproto.xrpc.XrpcTransportException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SpaceFailuresTest {
    @Test
    fun `an expired token becomes the 401 the token refresher handles`() {
        val failure = assertIs<CloudApiException>(translateSpaceFailure(XrpcProtocolException(401, "ExpiredToken", "expired")))

        assertEquals(401, failure.statusCode)
        assertEquals(SyncErrorType.AUTHENTICATION_ERROR, failure.spaceErrorType())
    }

    @Test
    fun `rate limiting keeps the server's Retry-After`() {
        val failure =
            assertIs<CloudApiException>(translateSpaceFailure(XrpcProtocolException(429, "RateLimitExceeded", retryAfterSeconds = 30)))

        assertEquals(30_000L, failure.retryAfterMillis)
        assertEquals(SyncErrorType.SERVER_ERROR, failure.spaceErrorType())
    }

    @Test
    fun `the data home's typed refusals are reported as the failures they wrap`() {
        val limited = XrpcProtocolException(429, "RateLimitExceeded", retryAfterSeconds = 30)
        val busy = XrpcProtocolException(503, "ServerBusy", retryAfterSeconds = 5)
        val full = XrpcProtocolException(402, "QuotaExceeded")

        assertEquals(
            30_000L,
            assertIs<CloudApiException>(translateSpaceFailure(DataHomeRateLimitedException("slow", 30, limited))).retryAfterMillis,
        )
        assertEquals(5_000L, assertIs<CloudApiException>(translateSpaceFailure(DataHomeBusyException("busy", 5, busy))).retryAfterMillis)
        assertEquals(
            STORAGE_QUOTA_EXCEEDED,
            assertIs<CloudApiException>(translateSpaceFailure(DataHomeQuotaExceededException(full))).errorCode,
        )
    }

    @Test
    fun `a full quota and an oversized file are named for what they are`() {
        assertEquals(STORAGE_QUOTA_EXCEEDED, (translateSpaceFailure(XrpcProtocolException(507)) as CloudApiException).errorCode)
        assertEquals(
            SyncErrorType.STORAGE_ERROR,
            translateSpaceFailure(XrpcProtocolException(400, "QuotaExceeded")).spaceErrorType(),
        )
        val tooLarge = translateSpaceFailure(XrpcProtocolException(413, "BlobTooLarge", "too big"))
        assertIs<MediaTooLargeException>(tooLarge)
        assertTrue(tooLarge.isPermanentSpaceFailure())
    }

    @Test
    fun `a failure before any response is a network error that is retried`() {
        val failure = translateSpaceFailure(XrpcTransportException("reset"))

        assertEquals(SyncErrorType.NETWORK_ERROR, failure.spaceErrorType())
        assertEquals(false, failure.isPermanentSpaceFailure())
        assertEquals("INVALID_RESPONSE", (translateSpaceFailure(XrpcSerializationException("bad")) as CloudApiException).errorCode)
    }

    @Test
    fun `swap conflicts pass through for the uploader`() {
        val conflict = DataHomeConflictException("changed")

        assertSame(conflict, translateSpaceFailure(conflict))
    }

    @Test
    fun `a kind is registered once and only after its download is wired`() {
        val journals = journalKind(FakeJournalRepository())
        val notes = noteKind(FakeJournalNotesRepository())
        val drafts =
            SpaceKind.single(
                EntityType.DRAFT,
                TestJournalMapper,
                journals.local,
            ) { journal, version -> journal.copy(syncVersion = version) }
        val registry = SpaceRecordRegistry(listOf(journals, notes))

        assertSame(journals, registry.forCollection(JOURNAL_COLLECTION)?.kind)
        assertSame(notes, registry.forCollection(TRANSCRIPT_COLLECTION)?.kind)
        assertEquals(false, registry.forCollection(TRANSCRIPT_COLLECTION)?.isMain)
        assertEquals(
            "6f1c2c9e-4b1a-4c55-9a39-2f0d6c3b8e71",
            registry
                .forCollection(
                    TRANSCRIPT_COLLECTION,
                )?.ownerOf("6F1C2C9E-4B1A-4C55-9A39-2F0D6C3B8E71:6F1C2C9E-4B1A-4C55-9A39-2F0D6C3B8E71"),
        )
        assertFailsWith<IllegalArgumentException> { SpaceRecordRegistry(listOf(journals, journals)) }
        assertFailsWith<IllegalArgumentException> { SpaceRecordRegistry(listOf(drafts)) }
        val twoOwners =
            SpaceKind.single(
                EntityType.NOTE,
                TestJournalMapper,
                journals.local,
            ) { journal, version -> journal.copy(syncVersion = version) }
        assertFailsWith<IllegalArgumentException> { SpaceRecordRegistry(listOf(journals, twoOwners)) }
    }
}
