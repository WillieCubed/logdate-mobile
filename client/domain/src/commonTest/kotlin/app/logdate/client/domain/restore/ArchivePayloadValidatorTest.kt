package app.logdate.client.domain.restore

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertFailsWith

internal class ArchivePayloadValidatorTest : RestoreArchiveTestFixture() {
    @Test
    fun `valid legacy documents are accepted before any destination access`() {
        ArchivePayloadValidator.validate(RestoreArchiveBundle.V1("", buildBundle()))
    }

    @Test
    fun `malformed note documents are rejected during archive validation`() {
        assertFailsWith<SerializationException> {
            ArchivePayloadValidator.validate(RestoreArchiveBundle.V1("", buildBundle().copy(notesJson = "{truncated")))
        }
    }
}
