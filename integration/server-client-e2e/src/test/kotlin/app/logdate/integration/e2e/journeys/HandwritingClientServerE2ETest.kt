@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.shared.model.HandwritingDocument
import app.logdate.shared.model.HandwritingPoint
import app.logdate.shared.model.HandwritingStroke
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

class HandwritingClientServerE2ETest {
    @Test
    fun originalInkSurvivesTheServerContentContract() =
        runTest {
            withServerClientHarness {
                val token =
                    apiClient
                        .createAccountWithSyntheticPasskey("ink_${Random.nextInt(100000, 999999)}")
                        .data.tokens.accessToken
                val source = DefaultCloudContentDataSource(apiClient)
                val time = Instant.fromEpochMilliseconds(1000)
                val note =
                    JournalNote.Handwriting(
                        uid = Uuid.random(),
                        creationTimestamp = time,
                        lastUpdated = time,
                        document =
                            HandwritingDocument(
                                height = 1700f,
                                strokes =
                                    listOf(
                                        HandwritingStroke(
                                            Uuid.random(),
                                            "pen",
                                            "#263044",
                                            3.5f,
                                            listOf(HandwritingPoint(12.25f, 1400.5f, 0.2f), HandwritingPoint(400.75f, 1500.25f, 0.9f)),
                                        ),
                                    ),
                            ),
                    )
                source.uploadNote(token, note).getOrThrow()
                val restored =
                    source
                        .getContentChanges(
                            token,
                            Instant.fromEpochMilliseconds(0),
                        ).getOrThrow()
                        .changes
                        .single() as JournalNote.Handwriting
                assertEquals(note.uid, restored.uid)
                assertEquals(note.document, restored.document)
            }
        }
}
