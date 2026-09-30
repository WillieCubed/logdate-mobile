package app.logdate.integration.e2e.journeys

import app.logdate.client.networking.LocationHistoryApiClient
import app.logdate.client.networking.LocationHistoryTransportException
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.sync.LocationHistoryUpload
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocationHistoryClientServerE2ETest {
    @Test
    fun `encrypted records survive retries and pages while account isolation and deletions persist`() =
        runTest {
            withServerClientHarness {
                val client =
                    LocationHistoryApiClient(
                        httpClient,
                        DefaultLogDateConfigRepository(
                            initialBackendUrl = baseUrl.removeSuffix("/api/v1"),
                        ),
                    )
                val token =
                    apiClient
                        .createAccountWithSyntheticPasskey("loc_${Random.nextInt(100000, 999999)}")
                        .data.tokens.accessToken
                val otherToken =
                    apiClient
                        .createAccountWithSyntheticPasskey("loc_${Random.nextInt(100000, 999999)}")
                        .data.tokens.accessToken
                val uploads =
                    (1..3).map {
                        LocationHistoryUpload(
                            "record-$it",
                            "observation",
                            "LDSE2:opaque-$it",
                            deviceId = "device-a",
                            deviceVersion = 1,
                        )
                    }
                val stored = client.upload(token, uploads)
                assertEquals(stored, client.upload(token, uploads))
                assertEquals(stored, client.upload(token, uploads.map { it.copy(payload = "LDSE2:fresh-nonce") }))
                assertTrue(client.changes(otherToken, 0).records.isEmpty())
                val first = client.changes(token, 0, 2)
                assertTrue(first.hasMore)
                assertEquals(stored.take(2), first.records)
                val last = client.changes(token, first.nextCursor, 2)
                assertFalse(last.hasMore)
                assertEquals(stored.takeLast(1), last.records)
                val deleted =
                    client
                        .upload(
                            token,
                            listOf(
                                uploads.first().copy(
                                    payload = null,
                                    deleted = true,
                                    deviceVersion = 2,
                                    expectedServerVersion = stored.first().serverVersion,
                                ),
                            ),
                        ).single()
                assertNull(deleted.payload)
                val conflict =
                    assertFailsWith<LocationHistoryTransportException> {
                        client.upload(token, uploads)
                    }
                assertEquals(409, conflict.statusCode)
                assertEquals(listOf(deleted), client.changes(token, last.nextCursor).records)
                val plaintext =
                    assertFailsWith<LocationHistoryTransportException> {
                        client.upload(token, listOf(uploads.first().copy(id = "plaintext", payload = "coordinates")))
                    }
                assertEquals(400, plaintext.statusCode)
            }
        }
}
