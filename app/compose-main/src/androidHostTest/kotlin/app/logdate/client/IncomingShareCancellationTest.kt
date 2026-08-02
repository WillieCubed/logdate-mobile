package app.logdate.client

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class IncomingShareCancellationTest {
    @Test
    fun cancellationIsRethrownInsteadOfBecomingAnAttachmentFailure() {
        runBlocking {
            assertFailsWith<CancellationException> {
                importSharedAttachment("content://provider/first") {
                    throw CancellationException("Incoming share owner was cancelled")
                }
            }
        }
    }

    @Test
    fun cancellationOnLaterAttachmentRollsBackEveryEarlierOwnedImport() {
        runBlocking {
            val attemptedSources = mutableListOf<String>()
            val rolledBackImports = mutableListOf<String>()

            assertFailsWith<CancellationException> {
                importOwnedSharedAttachments(
                    sourceUris =
                        listOf(
                            "content://provider/first",
                            "content://provider/second",
                            "content://provider/never-reached",
                        ),
                    importAttachment = { sourceUri ->
                        attemptedSources += sourceUri
                        if (sourceUri.endsWith("/second")) {
                            throw CancellationException("Share owner disappeared")
                        }
                        "owned:$sourceUri"
                    },
                    rollbackAttachment = rolledBackImports::add,
                )
            }

            assertEquals(
                listOf("content://provider/first", "content://provider/second"),
                attemptedSources,
            )
            assertEquals(listOf("owned:content://provider/first"), rolledBackImports)
        }
    }

    @Test
    fun failedEditorLaunchRollsBackEveryAttachmentStillOwnedByIncomingShare() {
        runBlocking {
            val ownedAttachments = listOf("owned:first", "owned:second")
            val acceptedAttachments = mutableListOf<String>()
            val rolledBackAttachments = mutableListOf<String>()
            var launchCount = 0

            val launched =
                handOffIncomingShareToEditor(
                    ownedAttachments = ownedAttachments,
                    launchEditor = {
                        launchCount++
                        false
                    },
                    confirmAccepted = acceptedAttachments::add,
                    rollbackAttachment = rolledBackAttachments::add,
                )

            assertFalse(launched)
            assertEquals(1, launchCount)
            assertEquals(emptyList(), acceptedAttachments)
            assertEquals(ownedAttachments, rolledBackAttachments)
        }
    }
}
