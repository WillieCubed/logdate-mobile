package app.logdate.server.auth

import app.logdate.shared.model.UpdateAccountProfileRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class OriginalBioContractTest {
    @Test
    fun `account retains original bio separately from displayed bio`() {
        val account =
            Account(
                id = Uuid.random(),
                username = "writer",
                displayName = "Writer",
                bio = "A polished summary",
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            )

        val encoded = Json.encodeToString(Account.serializer(), account)
        val withOriginal = encoded.dropLast(1) + ",\"originalBio\":\"I keep a journal every day\"}"
        val restored = Json.decodeFromString(Account.serializer(), withOriginal)
        val roundTrip = Json.encodeToString(Account.serializer(), restored)

        assertTrue(roundTrip.contains("\"originalBio\":\"I keep a journal every day\""), roundTrip)
    }

    @Test
    fun `profile update accepts original bio independently`() {
        val request =
            Json.decodeFromString<UpdateAccountProfileRequest>(
                """{"originalBio":"I keep a journal every day"}""",
            )

        val originalBio =
            Json
                .parseToJsonElement(Json.encodeToString(UpdateAccountProfileRequest.serializer(), request))
                .jsonObject["originalBio"]
                ?.jsonPrimitive
                ?.content
        assertEquals("I keep a journal every day", originalBio)
    }

    @Test
    fun `empty bio fields remain explicit when clearing the cloud profile`() {
        val request = UpdateAccountProfileRequest(bio = "", originalBio = "")
        val encoded = Json.encodeToString(UpdateAccountProfileRequest.serializer(), request)
        val restored = Json.decodeFromString<UpdateAccountProfileRequest>(encoded)

        assertEquals("", restored.bio)
        assertEquals("", restored.originalBio)
    }
}
