package app.logdate.client.sync.space.mappers

import kotlin.test.Test
import kotlin.test.assertEquals

class PlainTextTest {
    @Test
    fun `ordered markers preserve literal text across supported digit lengths and delimiters`() {
        mapOf(
            "1. first" to "1\\. first",
            "12) second" to "12\\) second",
            "0003. third" to "0003\\. third",
            "999999999. last" to "999999999\\. last",
            "7)" to "7\\)",
        ).forEach { (plain, markdown) -> assertEquals(markdown, plainTextAsMarkdown(plain)) }
    }

    @Test
    fun `numbers that are not ordered markers remain unchanged`() {
        listOf("1000000000. too many digits", "3.14", "4)text", "5.5 text").forEach { text ->
            assertEquals(text, plainTextAsMarkdown(text))
        }
    }
}
