package studio.hypertext.atproto.syntax

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatetimeAndLanguageTagTest {
    @Test
    fun `datetimes need RFC 3339 form with a time zone`() {
        listOf("2026-10-17T10:00:00Z", "2026-10-17T10:00:00.123456Z", "2026-10-17T10:00:00+05:30", "2026-10-17T23:59:60Z")
            .forEach { assertTrue(Datetime.isValid(it), it) }
        listOf("2026-10-17", "2026-10-17 10:00:00Z", "2026-10-17t10:00:00z", "2026-10-17T10:00:00", "2026-13-01T00:00:00Z")
            .forEach { assertFalse(Datetime.isValid(it), it) }
    }

    @Test
    fun `an unknown local offset is refused`() {
        val error = assertFailsWith<InvalidDatetimeException> { Datetime.require("2026-10-17T10:00:00-00:00") }

        assertEquals("Invalid datetime: 2026-10-17T10:00:00-00:00", error.message)
        assertTrue(Datetime.isValid("2026-10-17T10:00:00+00:00"))
    }

    @Test
    fun `language tags follow the BCP 47 shape`() {
        listOf("en", "pt-BR", "zh-Hant-TW", "x-private").forEach { assertTrue(LanguageTag.isValid(it), it) }
        listOf("", "english language", "en_US", "toolonglanguage", "en--US").forEach { assertFalse(LanguageTag.isValid(it), it) }
        assertEquals("pt-BR", LanguageTag.require("pt-BR").toString())
    }
}
