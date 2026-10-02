package app.logdate.client.sync.cloud

import app.logdate.client.device.crypto.IdentityKeyNotFoundException
import app.logdate.client.sync.crypto.UnreadablePayloadException
import app.logdate.client.sync.crypto.WrongKeyPayloadException
import app.logdate.shared.model.diagnostics.DiagnosticReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class RemoteRecordFailureTest {
    private data class Wire(
        val id: String,
        val version: Long,
        val error: Exception? = null,
    )

    @Test
    fun `only an explicit fingerprint mismatch permits repair while other failures retain their versions`() =
        runTest {
            val wrongKey = Uuid.random().toString()
            val damaged = Uuid.random().toString()
            val future = Uuid.random().toString()
            val healthy = Uuid.random().toString()
            val rows =
                listOf(
                    Wire(wrongKey, 4, UnreadablePayloadException("field", WrongKeyPayloadException("field"))),
                    Wire(damaged, 7, UnreadablePayloadException("field", IllegalArgumentException("private-marker"))),
                    Wire(future, 9, UnsupportedRemoteFormatException()),
                    Wire("invalid-identity", 11),
                    Wire(healthy, 12),
                )
            val result =
                rows.readEach(idOf = { it.id }, versionOf = { it.version }) {
                    it.error?.let { error -> throw error }
                    Uuid.parse(it.id)
                }
            assertEquals(listOf(Uuid.parse(healthy)), result.readable)
            assertEquals(listOf(Uuid.parse(wrongKey)), result.unreadable)
            assertEquals(
                listOf(
                    RemoteRecordFailure(damaged, 7, DiagnosticReason.CORRUPT_PAYLOAD),
                    RemoteRecordFailure(future, 9, DiagnosticReason.UNSUPPORTED_FORMAT),
                    RemoteRecordFailure("invalid-identity", 11, DiagnosticReason.CORRUPT_PAYLOAD),
                ),
                result.failures,
            )
        }

    @Test
    fun `page conversion propagates cancellation from both fetch and conversion`() =
        runTest {
            assertFailsWith<CancellationException> {
                Result.failure<String>(CancellationException()).mapRecordPage { it }
            }
            assertFailsWith<CancellationException> {
                Result.success("wire").mapRecordPage { throw CancellationException() }
            }
        }

    @Test
    fun `cancellation and absent identity key abort instead of marking a record corrupt`() =
        runTest {
            val rows = listOf(Wire(Uuid.random().toString(), 1))
            assertFailsWith<CancellationException> {
                rows.readEach(idOf = { it.id }, versionOf = { it.version }) { throw CancellationException() }
            }
            assertFailsWith<IdentityKeyNotFoundException> {
                rows.readEach(idOf = { it.id }, versionOf = { it.version }) { throw IdentityKeyNotFoundException("private-marker") }
            }
        }
}
