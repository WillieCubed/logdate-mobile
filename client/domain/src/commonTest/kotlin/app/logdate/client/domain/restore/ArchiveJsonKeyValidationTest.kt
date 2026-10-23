package app.logdate.client.domain.restore

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ArchiveJsonKeyValidationTest {
    @Test
    fun `nested and escaped equivalent object keys are refused`() {
        for (text in listOf(
            """{"a":1,"a":2}""",
            """{"a":1,"\u0061":2}""",
            """{"outer":[{"a":1,"\u0061":2}]}""",
            """{"a\"b":1,"a\u0022b":2}""",
        )) {
            assertFailsWith<IllegalArgumentException> { ArchiveJsonKeyValidation.requireUnique(text) }
        }
    }

    @Test
    fun `the same key in distinct objects and JSON-looking string values remain valid`() {
        ArchiveJsonKeyValidation.requireUnique("""{"one":{"x":1},"two":{"x":2},"text":"{\"x\":1,\"x\":2}"}""")
        ArchiveJsonKeyValidation.requireUnique("""{"one":[],"two":null,"three":true,"four":-1.2e3}""")
    }

    @Test
    fun `excessive nesting is refused before recursive deserialization`() {
        assertFailsWith<IllegalArgumentException> { ArchiveJsonKeyValidation.requireUnique("[".repeat(129) + "0" + "]".repeat(129)) }
    }
}
