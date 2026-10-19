package app.logdate.client.sync.space

import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.MediaTooLargeException
import app.logdate.client.sync.metadata.EntityType
import app.logdate.shared.model.Journal
import app.logdate.space.DataHomeConflictException
import kotlinx.serialization.json.JsonObject
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
        val journals =
            SpaceKind(
                TestJournalMapper,
                TestJournalRecords(
                    app.logdate.client.sync.test
                        .FakeJournalRepository(),
                ),
            )
        val drafts =
            object : SpaceRecordMapper<Journal> by TestJournalMapper {
                override val entityType = EntityType.DRAFT
                override val collection = "app.logdate.journal.draft"

                override fun toRecord(item: Journal): JsonObject = TestJournalMapper.toRecord(item)
            }

        assertEquals(journals, SpaceRecordRegistry(listOf(journals)).forCollection(JOURNAL_COLLECTION))
        assertFailsWith<IllegalArgumentException> { SpaceRecordRegistry(listOf(journals, journals)) }
        assertFailsWith<IllegalArgumentException> { SpaceRecordRegistry(listOf(SpaceKind(drafts, journals.local))) }
    }
}
