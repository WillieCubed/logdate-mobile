package app.logdate.server.atproto.firehose

import studio.hypertext.atproto.syntax.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The list of public collections is fixed in code. These tests pin it down and check that a
 * collection becomes public only by being named.
 */
class PublicRepoPolicyTest {
    private val policy = PublicRepoPolicy()

    @Test
    fun `the profile the sharing keys and published stories are public`() {
        listOf(
            "app.logdate.actor.profile",
            "app.logdate.crypto.keyDeclaration",
            "site.standard.publication",
            "site.standard.document",
        ).forEach { assertTrue(policy.isPublic(Nsid.require(it)), it) }
    }

    @Test
    fun `every collection LogDate keeps for a person is private`() {
        listOf(
            "studio.hypertext.logdate.content",
            "studio.hypertext.logdate.entry",
            "studio.hypertext.logdate.journal",
            "studio.hypertext.logdate.association",
            "studio.hypertext.logdate.draft",
            "studio.hypertext.logdate.moment",
            "app.logdate.space.personal",
            "app.logdate.space.circle",
            "app.logdate.space.group",
            "app.logdate.space.member",
            "app.logdate.crypto.sealed",
            "app.logdate.crypto.wrappedKey",
            "app.logdate.actor.settings",
            "app.logdate.journal.entry",
            "app.logdate.journal.draft",
            "app.logdate.graph.circle",
            "app.logdate.people.person",
            "app.logdate.place.visit",
            "app.logdate.health.snapshot",
            "app.logdate.share.share",
            "app.logdate.somethingNew.record",
        ).forEach { assertFalse(policy.isPublic(Nsid.require(it)), it) }
    }

    @Test
    fun `records of other apps are private until the collection is open to apps`() {
        val other = Nsid.require("com.example.note")

        assertFalse(policy.isPublic(other))
        assertTrue(PublicRepoPolicy(collectionsOpenToApps = setOf("com.example.note")).isPublic(other))
    }

    @Test
    fun `a LogDate journal collection cannot be made public through the open collections`() {
        listOf("studio.hypertext.logdate.content", "app.logdate.journal.entry", "app.logdate.space.personal").forEach { collection ->
            assertFailsWith<IllegalArgumentException>(collection) { PublicRepoPolicy(collectionsOpenToApps = setOf(collection)) }
        }
    }

    @Test
    fun `a tree is public only when every collection in it is`() {
        val profile = Nsid.require("app.logdate.actor.profile")
        val diary = Nsid.require("studio.hypertext.logdate.content")

        assertTrue(policy.arePublic(listOf(profile)))
        assertTrue(policy.arePublic(emptyList()))
        assertFalse(policy.arePublic(listOf(profile, diary)))
        assertEquals(false, policy.arePublic(setOf(diary)))
    }
}
