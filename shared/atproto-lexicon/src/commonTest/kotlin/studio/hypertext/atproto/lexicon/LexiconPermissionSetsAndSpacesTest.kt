package studio.hypertext.atproto.lexicon

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import studio.hypertext.atproto.syntax.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Permission sets (https://atproto.com/specs/permission) and space type declarations (the AT
 * Protocol Spaces proposal) describe access instead of data: they parse into their own models,
 * follow their own rules, and generate constants instead of data classes.
 */
class LexiconPermissionSetsAndSpacesTest {
    @Test
    fun `a space declaration parses its name and key rule and collections`() {
        val main = LexiconParser.parse(SPACE).definitions.getValue("main")
        val space = assertNotNull(main.space)

        assertEquals(LexiconType.SPACE, main.type)
        assertTrue(main.type.isPrimary)
        assertEquals("Your journal", space.name)
        assertEquals(mapOf("es" to "Tu diario"), space.nameLang)
        assertEquals(LexiconRecordKey.Literal("self"), space.key)
        assertEquals(listOf("com.example.journal.sealed"), space.collections)
    }

    @Test
    fun `a permission set parses its wording and permissions`() {
        val main = LexiconParser.parse(PERMISSION_SET).definitions.getValue("main")
        val set = assertNotNull(main.permissionSet)
        val permissions = assertNotNull(set.permissions)

        assertEquals(LexiconType.PERMISSION_SET, main.type)
        assertEquals("Use Example", set.title)
        assertEquals(mapOf("fr" to "Utiliser Example"), set.titleLang)
        assertEquals("Read and write your journal.", set.detail)
        assertEquals(listOf("repo", "rpc", "space"), permissions.map { it.resource })
        assertEquals(listOf("com.example.journal.profile"), permissions[0].values("collection"))
        assertEquals(listOf("create", "update"), permissions[0].values("action"))
        assertEquals(true, permissions[1].boolean("inheritAud"))
        assertEquals("com.example.journal", permissions[2].string("spaceType"))
        assertEquals(listOf("*"), permissions[2].values("authority"))
        assertEquals(emptyList(), permissions[2].values("skey"))
    }

    @Test
    fun `well-formed permission sets and space declarations pass the checker`() {
        assertEquals(emptyList(), issues(SPACE))
        assertEquals(emptyList(), issues(PERMISSION_SET))
    }

    @Test
    fun `a space declaration needs a name and a key rule and its collections`() {
        assertEquals(
            listOf(
                "com.example.journal#main: A space declaration needs a name",
                "com.example.journal#main: A space declaration needs a key rule",
                "com.example.journal#main: A space declaration needs a collections list",
            ),
            issues(document("com.example.journal", """{ "type": "space" }""")),
        )
    }

    @Test
    fun `a space declaration's fields must be well formed`() {
        val space =
            """
            {
              "type": "space",
              "name": "${"x".repeat(65)}",
              "name:lang": { "not a tag": "x" },
              "key": "random",
              "collections": ["*", "not-an-nsid", "com.example.journal.sealed", "com.example.journal.sealed"]
            }
            """.trimIndent()

        assertEquals(
            listOf(
                "com.example.journal#main: The name is longer than 64 characters",
                "com.example.journal#main: name:lang key 'not a tag' isn't a language tag",
                "com.example.journal#main: 'random' isn't a key rule",
                "com.example.journal#main: Collections can't include the * wildcard",
                "com.example.journal#main: Collection 'not-an-nsid' isn't a valid NSID",
                "com.example.journal#main: Collection com.example.journal.sealed is listed twice",
            ),
            issues(document("com.example.journal", space)),
        )
    }

    @Test
    fun `spaces and permission sets must be the main definition`() {
        val misplaced =
            """
            {
              "lexicon": 1,
              "id": "com.example.journal.defs",
              "defs": {
                "personal": { "type": "space", "key": "any", "name": "Journal", "collections": [] },
                "access": { "type": "permission-set", "permissions": [] }
              }
            }
            """.trimIndent()

        assertEquals(
            listOf(
                "com.example.journal.defs#personal: space definitions must be named main",
                "com.example.journal.defs#access: permission-set definitions must be named main",
            ),
            issues(misplaced),
        )
    }

    @Test
    fun `a permission set needs a permissions list of permission entries`() {
        assertEquals(
            listOf("com.example.authFull#main: A permission set needs a permissions list"),
            issues(document("com.example.authFull", """{ "type": "permission-set" }""")),
        )
        assertEquals(
            listOf("com.example.authFull#main.permissions[0]: Every entry needs \"type\": \"permission\""),
            issues(permissionSet("""{ "resource": "repo", "collection": ["com.example.journal"] }""")),
        )
    }

    @Test
    fun `a permission set can't reach outside its namespace or use wildcards`() {
        val set =
            permissionSet(
                """{ "type": "permission", "resource": "repo", "collection": ["app.bsky.feed.post", "*"] }""",
                """{ "type": "permission", "resource": "rpc", "lxm": ["com.exampl.x.y"], "inheritAud": true }""",
                """{ "type": "permission", "resource": "space", "spaceType": "*" }""",
                """{ "type": "permission", "resource": "space", "spaceType": "org.example.forum" }""",
            )

        assertEquals(
            listOf(
                "com.example.authFull#main.permissions[0]: collection app.bsky.feed.post is outside this set's namespace, com.example.*",
                "com.example.authFull#main.permissions[0]: collection can't be * in a permission set",
                "com.example.authFull#main.permissions[1]: lxm com.exampl.x.y is outside this set's namespace, com.example.*",
                "com.example.authFull#main.permissions[2]: A permission set must name a space type, not *",
                "com.example.authFull#main.permissions[3]: spaceType org.example.forum is outside this set's namespace, com.example.*",
            ),
            issues(set),
        )
    }

    @Test
    fun `blob and account and identity permissions and unknown resources are refused`() {
        val set =
            permissionSet(
                """{ "type": "permission", "resource": "blob", "accept": ["*/*"] }""",
                """{ "type": "permission", "resource": "account", "attr": "email" }""",
                """{ "type": "permission", "resource": "identity", "attr": "handle" }""",
                """{ "type": "permission", "resource": "repos", "collection": ["com.example.journal"] }""",
            )

        assertEquals(
            listOf(
                "com.example.authFull#main.permissions[0]: blob permissions can't be in a permission set; apps ask for them directly",
                "com.example.authFull#main.permissions[1]: account permissions can't be in a permission set; apps ask for them directly",
                "com.example.authFull#main.permissions[2]: identity permissions can't be in a permission set; apps ask for them directly",
                "com.example.authFull#main.permissions[3]: 'repos' isn't a resource a permission set can grant",
            ),
            issues(set),
        )
    }

    @Test
    fun `permission parameters must hold allowed values`() {
        val set =
            permissionSet(
                """{ "type": "permission", "resource": "repo", "collection": [], "action": ["read", "create", "create"] }""",
                """{ "type": "permission", "resource": "rpc", "lxm": ["com.example.a.b"] }""",
                """{ "type": "permission", "resource": "rpc", "lxm": ["com.example.a.b"], "inheritAud": true, "aud": "*" }""",
                """{ "type": "permission", "resource": "rpc", "lxm": ["com.example.a.b"], "aud": "did:web:example.com#svc" }""",
                """
                {
                  "type": "permission", "resource": "space", "spaceType": "com.example.group",
                  "authority": "alice.example", "skey": "", "collection": ["nope"],
                  "action": ["write"], "manage": ["read"]
                }
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                "com.example.authFull#main.permissions[0]: A repo permission needs at least one collection",
                "com.example.authFull#main.permissions[0]: action 'read' isn't one of create, update, delete",
                "com.example.authFull#main.permissions[0]: action create is listed twice",
                "com.example.authFull#main.permissions[1]: An rpc permission needs aud: *, or inheritAud: true",
                "com.example.authFull#main.permissions[2]: An rpc permission can't set aud and inheritAud together",
                "com.example.authFull#main.permissions[3]: aud 'did:web:example.com#svc' can't name a service in a " +
                    "permission set; use inheritAud: true",
                "com.example.authFull#main.permissions[4]: authority 'alice.example' isn't self, * or a DID",
                "com.example.authFull#main.permissions[4]: skey '' isn't * or a valid space key",
                "com.example.authFull#main.permissions[4]: collection 'nope' isn't * or a valid NSID",
                "com.example.authFull#main.permissions[4]: action 'write' isn't one of read_self, read, create, update, delete",
                "com.example.authFull#main.permissions[4]: manage 'read' isn't one of create, update, delete",
            ),
            issues(set),
        )
    }

    @Test
    fun `a space declaration generates its type and name and key rule and collections`() {
        val generated = LexiconCodegen.generate(LexiconParser.parse(SPACE), "com.example.generated")

        assertTrue(generated.contains("public object Journal {"), generated)
        assertTrue(generated.contains("public const val TYPE: String = \"com.example.journal\""), generated)
        assertTrue(generated.contains("public const val NAME: String = \"Your journal\""), generated)
        assertTrue(generated.contains("public val KEY: LexiconRecordKey = LexiconRecordKey.Literal(\"self\")"), generated)
        assertTrue(generated.contains("public val COLLECTIONS: List<String> = listOf(\"com.example.journal.sealed\")"), generated)
        assertTrue(generated.contains("import studio.hypertext.atproto.lexicon.LexiconRecordKey"), generated)
    }

    @Test
    fun `a space name with quotes and dollar signs generates a valid string`() {
        val space = document("com.example.journal", """{ "type": "space", "key": "any", "name": "Say \"${'$'}hi\"", "collections": [] }""")

        val generated = LexiconCodegen.generate(LexiconParser.parse(space), "com.example.generated")

        assertTrue(generated.contains("public const val NAME: String = \"Say \\\"\\${'$'}hi\\\"\""), generated)
        assertTrue(generated.contains("public val COLLECTIONS: List<String> = emptyList()"), generated)
    }

    @Test
    fun `a permission set generates the scope an app asks for it with`() {
        val generated = LexiconCodegen.generate(LexiconParser.parse(PERMISSION_SET), "com.example.generated")

        assertTrue(generated.contains("public object AuthFull {"), generated)
        assertTrue(generated.contains("public const val SCOPE: String = \"include:com.example.authFull\""), generated)
    }

    @Test
    fun `the record validator says when a collection is a space or a permission set`() {
        val validator = LexiconRecordValidator(LexiconRegistry(listOf(LexiconParser.parse(SPACE), LexiconParser.parse(PERMISSION_SET))))
        val record = Json.parseToJsonElement("{}").jsonObject

        assertEquals(
            listOf(": com.example.journal is a space type, not a record type"),
            validator.validateRecord(Nsid.require("com.example.journal"), record).issues.map { "${it.path}: ${it.message}" },
        )
        assertEquals(
            listOf(": com.example.authFull is a permission set, not a record type"),
            validator.validateRecord(Nsid.require("com.example.authFull"), record).issues.map { "${it.path}: ${it.message}" },
        )
    }

    @Test
    fun `space-ref strings name a space and nothing inside it`() {
        val schema =
            document(
                "com.example.pin",
                """
                {
                  "type": "record", "key": "tid",
                  "record": { "type": "object", "properties": { "space": { "type": "string", "format": "space-ref" } } }
                }
                """.trimIndent(),
            )
        val validator = LexiconRecordValidator(LexiconRegistry(listOf(LexiconParser.parse(schema))))
        val check = { value: String ->
            validator
                .validateRecord(Nsid.require("com.example.pin"), Json.parseToJsonElement("""{ "space": "$value" }""").jsonObject)
                .issues
                .map { "${it.path}: ${it.message}" }
        }

        assertEquals(emptyList(), check("at://did:plc:abc123/space/app.logdate.space.personal/self"))
        assertEquals(emptyList(), check("at://did:web:example.com/space/com.example.forum/3jzfcijpj2z2a"))
        listOf(
            "at://alice.example/space/app.logdate.space.personal/self",
            "at://did:plc:abc123/app.logdate.space.personal/self",
            "at://did:plc:abc123/space/app.logdate.space.personal",
            "at://did:plc:abc123/space/not-an-nsid/self",
            "at://did:plc:abc123/space/app.logdate.space.personal/self/did:plc:abc123/app.logdate.crypto.sealed/x",
            "at://did:plc:abc123/space/app.logdate.space.personal/self/",
        ).forEach { value -> assertEquals(listOf("space: not a valid space-ref"), check(value), value) }
    }

    private fun issues(source: String): List<String> =
        LexiconValidator.validate(LexiconParser.parse(source)).issues.map { "${it.path}: ${it.message}" }

    private fun document(
        id: String,
        main: String,
    ): String = """{ "lexicon": 1, "id": "$id", "defs": { "main": $main } }"""

    private fun permissionSet(vararg permissions: String): String =
        document("com.example.authFull", """{ "type": "permission-set", "permissions": [${permissions.joinToString()}] }""")

    private companion object {
        val SPACE =
            """
            {
              "lexicon": 1,
              "id": "com.example.journal",
              "defs": {
                "main": {
                  "type": "space",
                  "description": "Someone's own journal.",
                  "key": "literal:self",
                  "name": "Your journal",
                  "name:lang": { "es": "Tu diario" },
                  "collections": ["com.example.journal.sealed"]
                }
              }
            }
            """.trimIndent()

        val PERMISSION_SET =
            """
            {
              "lexicon": 1,
              "id": "com.example.authFull",
              "defs": {
                "main": {
                  "type": "permission-set",
                  "title": "Use Example",
                  "title:lang": { "fr": "Utiliser Example" },
                  "detail": "Read and write your journal.",
                  "permissions": [
                    { "type": "permission", "resource": "repo", "collection": ["com.example.journal.profile"], "action": ["create", "update"] },
                    { "type": "permission", "resource": "rpc", "lxm": ["com.example.journal.getEntry"], "inheritAud": true },
                    {
                      "type": "permission", "resource": "space", "spaceType": "com.example.journal",
                      "authority": "*", "collection": ["*", "community.lexicon.calendar.event"],
                      "action": ["read_self", "create"], "manage": ["create", "update", "delete"]
                    }
                  ]
                }
              }
            }
            """.trimIndent()
    }
}
