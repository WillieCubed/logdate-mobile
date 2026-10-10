@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.database

import app.logdate.server.database.support.withH2Database
import app.logdate.shared.model.PasskeyChallenge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class PostgreSQLPasskeyChallengeRepositoryTest {
    @Test
    fun `wrong account ceremony and scope leave the matching challenge usable`() =
        withH2Database(WebAuthnChallengesTable) {
            runBlocking {
                val repository = PostgreSQLPasskeyChallengeRepository()
                val now = Clock.System.now()
                val challenge = challenge()
                repository.save("passkey", challenge)
                assertNull(repository.consume("passkey", challenge.challenge, "registration", Uuid.random(), now))
                assertNull(repository.consume("passkey", challenge.challenge, "authentication", null, now))
                assertNull(repository.consume("restore", challenge.challenge, "restore-registration", challenge.userId, now))
                assertNotNull(repository.consume("passkey", challenge.challenge, "registration", challenge.userId, now))
                assertNull(repository.consume("passkey", challenge.challenge, "registration", challenge.userId, now))
            }
        }

    @Test
    fun `expired challenges cannot be consumed including at the expiry boundary`() =
        withH2Database(WebAuthnChallengesTable) {
            runBlocking {
                val repository = PostgreSQLPasskeyChallengeRepository()
                val now = Clock.System.now()
                val challenge = challenge().copy(expiresAt = now.toString())
                repository.save("passkey", challenge)
                assertNull(repository.consume("passkey", challenge.challenge, "registration", challenge.userId, now))
            }
        }

    @Test
    fun `concurrent server instances can consume a challenge only once`() =
        withH2Database(WebAuthnChallengesTable) {
            runBlocking {
                val challenge = challenge()
                PostgreSQLPasskeyChallengeRepository().save("passkey", challenge)
                val results =
                    (1..16)
                        .map {
                            async(Dispatchers.IO) {
                                PostgreSQLPasskeyChallengeRepository().consume(
                                    "passkey",
                                    challenge.challenge,
                                    "registration",
                                    challenge.userId,
                                    Clock.System.now(),
                                )
                            }
                        }.awaitAll()
                assertEquals(1, results.count { it != null })
            }
        }

    @Test
    fun `restore challenges fit the existing database contract without sharing passkey challenges`() =
        withH2Database(WebAuthnChallengesTable) {
            runBlocking {
                val challenge = challenge().copy(type = "restore-authentication")
                PostgreSQLPasskeyChallengeRepository().save("restore", challenge)
                val reopened = PostgreSQLPasskeyChallengeRepository()
                assertNull(reopened.consume("passkey", challenge.challenge, "authentication", null, Clock.System.now()))
                assertNotNull(reopened.consume("restore", challenge.challenge, challenge.type, null, Clock.System.now()))
            }
        }

    private fun challenge() =
        PasskeyChallenge(
            challenge = Uuid.random().toString(),
            userId = Uuid.random(),
            type = "registration",
            expiresAt = (Clock.System.now() + 5.minutes).toString(),
        )
}
