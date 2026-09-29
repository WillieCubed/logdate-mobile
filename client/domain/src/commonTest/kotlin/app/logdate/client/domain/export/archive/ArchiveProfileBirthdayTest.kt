package app.logdate.client.domain.export.archive

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ArchiveProfileBirthdayTest {
    @Test
    fun `profile birthday exports as a calendar date`() {
        val json =
            ArchiveJson.document.encodeToString(
                ArchiveProfileFile.serializer(),
                ArchiveProfileFile(ArchiveProfile(birthday = Instant.parse("1992-06-03T18:25:00Z"))),
            )

        assertEquals(
            "1992-06-03",
            Json
                .parseToJsonElement(json)
                .jsonObject["profile"]
                ?.jsonObject
                ?.get("birthday")
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun `legacy timestamp birthday restores to its UTC calendar date`() {
        val profile =
            ArchiveJson.document
                .decodeFromString(
                    ArchiveProfileFile.serializer(),
                    """{"profile":{"birthday":"1992-06-03T18:25:00Z"}}""",
                ).profile

        assertEquals(Instant.parse("1992-06-03T00:00:00Z"), profile.birthday)
    }
}
